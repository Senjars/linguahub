package io.github.senjar.bookingservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.senjar.bookingservice.config.KafkaTestcontainersConfiguration;
import io.github.senjar.bookingservice.config.TestcontainersConfiguration;
import io.github.senjar.bookingservice.model.booking.Booking;
import io.github.senjar.bookingservice.model.booking.Status;
import io.github.senjar.bookingservice.model.slot.Slot;
import io.github.senjar.bookingservice.repository.BookingRepository;
import io.github.senjar.bookingservice.repository.SlotRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.kafka.KafkaContainer;

@ActiveProfiles("test")
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class BookingKafkaFlowTest {

    private static final String PAYMENT_CONFIRMED_TOPIC = "payment-confirmed";
    private static final String BOOKING_CONFIRMED_TOPIC = "booking-confirmed";
    private static final String EMAIL = "student@example.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SlotRepository slotRepository;

    private KafkaProducer<String, String> rawProducer;
    private KafkaConsumer<String, String> bookingConfirmedConsumer;

    @BeforeEach
    void setUp() {
        rawProducer = createRawProducer();
        bookingConfirmedConsumer = createConsumer(BOOKING_CONFIRMED_TOPIC);
    }

    @AfterEach
    void tearDown() {
        rawProducer.close();
        bookingConfirmedConsumer.close();
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
    }

    @Nested
    @DisplayName("consuming payment-confirmed")
    class PaymentConfirmedMessage {

        @Test
        @DisplayName("Should confirm the booking and publish booking-confirmed with the lesson data")
        void paymentConfirmed_pendingBooking_confirmsBookingAndPublishesEvent() throws Exception {
            // GIVEN
            Booking booking = saveBookingWithSlot();
            String key = String.valueOf(booking.getId());

            // WHEN
            sendRaw(PAYMENT_CONFIRMED_TOPIC, key, provideEventJson(booking.getId()));

            // THEN
            awaitBookingStatus(booking.getId(), Status.CONFIRMED);
            ConsumerRecord<String, String> published = awaitRecord(key);
            DocumentContext json = JsonPath.parse(published.value());
            assertThat(json.read("$.bookingId", Long.class)).isEqualTo(booking.getId());
            assertThat(json.read("$.slotId", Long.class)).isEqualTo(booking.getSlotId());
            assertThat(json.read("$.studentEmail", String.class)).isEqualTo(EMAIL);
            assertThat(json.<Object>read("$.startTime")).isNotNull();
            assertThat(json.<Object>read("$.endTime")).isNotNull();
        }

        @Test
        @DisplayName("Should ignore a duplicate delivery and publish booking-confirmed only once")
        void paymentConfirmed_duplicateMessage_publishesEventOnlyOnce() throws Exception {
            // GIVEN
            Booking booking = saveBookingWithSlot();
            String key = String.valueOf(booking.getId());

            // WHEN
            sendRaw(PAYMENT_CONFIRMED_TOPIC, key, provideEventJson(booking.getId()));
            sendRaw(PAYMENT_CONFIRMED_TOPIC, key, provideEventJson(booking.getId()));

            // THEN
            awaitBookingStatus(booking.getId(), Status.CONFIRMED);
            // The outbox relay publishes asynchronously (every 2 s), so the window must be longer
            // than its interval, otherwise the single expected event may not have arrived yet.
            List<ConsumerRecord<String, String>> published =
                    collectRecords(key, Duration.ofSeconds(8));
            assertThat(published).hasSize(1);
        }

        @Test
        @DisplayName("Should not be blocked by a message that cannot be deserialized")
        void paymentConfirmed_poisonMessage_doesNotBlockFollowingMessages() throws Exception {
            // GIVEN
            Booking booking = saveBookingWithSlot();
            String key = String.valueOf(booking.getId());

            // WHEN
            sendRaw(PAYMENT_CONFIRMED_TOPIC, "poison", "this is not json");
            sendRaw(PAYMENT_CONFIRMED_TOPIC, key, provideEventJson(booking.getId()));

            // THEN
            awaitBookingStatus(booking.getId(), Status.CONFIRMED);
        }
    }

    private Booking saveBookingWithSlot() {
        Slot slot = slotRepository.save(provideSlot());
        return bookingRepository.save(providePendingBooking(slot.getId()));
    }

    private void awaitBookingStatus(Long bookingId, Status expected) {
        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                        .isEqualTo(expected));
    }

    private ConsumerRecord<String, String> awaitRecord(String key) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        await().pollInSameThread().atMost(TIMEOUT).untilAsserted(() -> {
            bookingConfirmedConsumer.poll(Duration.ofMillis(200)).forEach(record -> {
                if (key.equals(record.key())) {
                    found.add(record);
                }
            });
            assertThat(found).isNotEmpty();
        });
        return found.get(0);
    }

    private List<ConsumerRecord<String, String>> collectRecords(String key, Duration duration) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) {
            bookingConfirmedConsumer.poll(Duration.ofMillis(200)).forEach(record -> {
                if (key.equals(record.key())) {
                    found.add(record);
                }
            });
        }
        return found;
    }

    private void sendRaw(String topic, String key, String value) throws Exception {
        rawProducer.send(new ProducerRecord<>(topic, key, value)).get();
    }

    private KafkaProducer<String, String> createRawProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private String provideEventJson(Long bookingId) {
        return "{\"bookingId\":" + bookingId + "}";
    }

    private Slot provideSlot() {
        return Slot.builder()
                .teacherId(UUID.randomUUID())
                .startTime(START)
                .endTime(END)
                .capacity(1)
                .bookedCount(1)
                .build();
    }

    private Booking providePendingBooking(Long slotId) {
        Booking booking = new Booking();
        booking.setSlotId(slotId);
        booking.setStudentId(UUID.randomUUID());
        booking.setStudentEmail(EMAIL);
        booking.setStatus(Status.PENDING);
        return booking;
    }
}
