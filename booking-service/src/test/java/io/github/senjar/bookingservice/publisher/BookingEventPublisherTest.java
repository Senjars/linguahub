package io.github.senjar.bookingservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.senjar.bookingservice.config.KafkaTestcontainersConfiguration;
import io.github.senjar.bookingservice.config.TestcontainersConfiguration;
import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
class BookingEventPublisherTest {

    private static final String BOOKING_CONFIRMED_TOPIC = "booking-confirmed";
    private static final Long BOOKING_ID = 900_001L;
    private static final Long SLOT_ID = 5L;
    private static final String EMAIL = "student@example.com";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private BookingEventPublisher bookingEventPublisher;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        consumer = createConsumer();
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Nested
    @DisplayName("publishBookingConfirmed")
    class PublishBookingConfirmed {

        @Test
        @DisplayName("Should publish the event under the booking id "
                + "with the lesson data as ISO JSON")
        void publishBookingConfirmed_validEvent_publishesJsonKeyedByBookingId() {
            // GIVEN
            BookingConfirmedEvent event = provideEvent();

            // WHEN
            bookingEventPublisher.publishBookingConfirmed(event);

            // THEN
            ConsumerRecord<String, String> published = awaitRecord(String.valueOf(BOOKING_ID));
            DocumentContext json = JsonPath.parse(published.value());
            assertThat(json.read("$.bookingId", Long.class)).isEqualTo(BOOKING_ID);
            assertThat(json.read("$.slotId", Long.class)).isEqualTo(SLOT_ID);
            assertThat(json.read("$.studentEmail", String.class)).isEqualTo(EMAIL);
            assertThat(json.read("$.startTime", String.class)).startsWith("2026-10-25T10:00");
            assertThat(json.read("$.endTime", String.class)).startsWith("2026-10-25T11:00");
        }

        @Test
        @DisplayName("Should publish exactly the agreed fields, so renaming one breaks this test")
        void publishBookingConfirmed_validEvent_containsExactlyTheAgreedFields() {
            // GIVEN
            BookingConfirmedEvent event = provideEvent();

            // WHEN
            bookingEventPublisher.publishBookingConfirmed(event);

            // THEN
            ConsumerRecord<String, String> published = awaitRecord(String.valueOf(BOOKING_ID));
            Map<String, Object> fields = JsonPath.parse(published.value()).read("$");
            assertThat(fields.keySet()).containsExactlyInAnyOrder(
                    "bookingId", "slotId", "studentEmail", "startTime", "endTime");
        }
    }

    private ConsumerRecord<String, String> awaitRecord(String key) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        await().pollInSameThread().atMost(TIMEOUT).untilAsserted(() -> {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                if (key.equals(record.key())) {
                    found.add(record);
                }
            });
            assertThat(found).isNotEmpty();
        });
        return found.get(found.size() - 1);
    }

    private KafkaConsumer<String, String> createConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> created = new KafkaConsumer<>(props);
        created.subscribe(List.of(BOOKING_CONFIRMED_TOPIC));
        return created;
    }

    private BookingConfirmedEvent provideEvent() {
        return new BookingConfirmedEvent(BOOKING_ID, SLOT_ID, EMAIL, START, END);
    }
}
