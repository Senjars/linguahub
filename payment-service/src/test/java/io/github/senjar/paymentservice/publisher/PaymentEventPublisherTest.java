package io.github.senjar.paymentservice.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.github.senjar.paymentservice.config.KafkaTestcontainersConfiguration;
import java.time.Duration;
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(classes = PaymentEventPublisher.class)
@ImportAutoConfiguration({KafkaAutoConfiguration.class, ServiceConnectionAutoConfiguration.class})
@Import(KafkaTestcontainersConfiguration.class)
class PaymentEventPublisherTest {

    private static final String PAYMENT_CONFIRMED_TOPIC = "payment-confirmed";
    private static final Long BOOKING_ID = 900_010L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private PaymentEventPublisher paymentEventPublisher;

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
    @DisplayName("publishPaymentConfirmed")
    class PublishPaymentConfirmed {

        @Test
        @DisplayName("Should publish the booking id as JSON on payment-confirmed")
        void publishPaymentConfirmed_bookingId_publishesJsonWithBookingId() {
            // WHEN
            paymentEventPublisher.publishPaymentConfirmed(BOOKING_ID);

            // THEN
            ConsumerRecord<String, String> published = awaitRecordFor(BOOKING_ID);
            DocumentContext json = JsonPath.parse(published.value());
            assertThat(json.read("$.bookingId", Long.class)).isEqualTo(BOOKING_ID);
        }

        @Test
        @DisplayName("Should publish exactly the field booking-service reads, so renaming it breaks this test")
        void publishPaymentConfirmed_bookingId_containsExactlyTheAgreedFields() {
            // WHEN
            paymentEventPublisher.publishPaymentConfirmed(BOOKING_ID);

            // THEN
            ConsumerRecord<String, String> published = awaitRecordFor(BOOKING_ID);
            Map<String, Object> fields = JsonPath.parse(published.value()).read("$");
            assertThat(fields.keySet()).containsExactly("bookingId");
        }

        @Test
        @DisplayName("Should use the booking id as the message key so events of one booking stay ordered")
        void publishPaymentConfirmed_bookingId_usesBookingIdAsKey() {
            // WHEN
            paymentEventPublisher.publishPaymentConfirmed(BOOKING_ID);

            // THEN
            ConsumerRecord<String, String> published = awaitRecordFor(BOOKING_ID);
            assertThat(published.key()).isEqualTo(String.valueOf(BOOKING_ID));
        }
    }

    private ConsumerRecord<String, String> awaitRecordFor(Long bookingId) {
        String marker = "\"bookingId\":" + bookingId;
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        await().pollInSameThread().atMost(TIMEOUT).untilAsserted(() -> {
            consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                if (record.value() != null && record.value().contains(marker)) {
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
        created.subscribe(List.of(PAYMENT_CONFIRMED_TOPIC));
        return created;
    }
}
