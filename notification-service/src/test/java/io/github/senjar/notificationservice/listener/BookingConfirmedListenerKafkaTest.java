package io.github.senjar.notificationservice.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.senjar.notificationservice.config.KafkaTestcontainersConfiguration;
import io.github.senjar.notificationservice.dto.EmailRequestDto;
import io.github.senjar.notificationservice.service.EmailService;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnectionAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(classes = BookingConfirmedListener.class)
@ImportAutoConfiguration({KafkaAutoConfiguration.class, ServiceConnectionAutoConfiguration.class})
@Import(KafkaTestcontainersConfiguration.class)
class BookingConfirmedListenerKafkaTest {

    private static final String BOOKING_CONFIRMED_TOPIC = "booking-confirmed";
    private static final String KEY = "42";
    private static final String EMAIL = "student@example.com";
    private static final long TIMEOUT_MS = 30_000;

    // The exact JSON booking-service publishes, see BookingEventPublisherTest
    private static final String BOOKING_CONFIRMED_JSON = """
            {"bookingId":42,"slotId":5,"studentEmail":"student@example.com",\
            "startTime":"2026-10-25T10:00:00","endTime":"2026-10-25T11:00:00"}""";

    @Autowired
    private KafkaContainer kafka;

    @MockitoBean
    private EmailService emailService;

    private KafkaProducer<String, String> rawProducer;

    @BeforeEach
    void setUp() {
        rawProducer = createRawProducer();
    }

    @AfterEach
    void tearDown() {
        rawProducer.close();
    }

    @Nested
    @DisplayName("consuming booking-confirmed")
    class BookingConfirmedMessage {

        @Test
        @DisplayName("Should read the JSON from booking-service "
                + "and send the email with the lesson times")
        void bookingConfirmed_validJson_sendsEmailToStudent() throws Exception {
            // WHEN
            sendRaw(KEY, BOOKING_CONFIRMED_JSON);

            // THEN
            EmailRequestDto sent = awaitSentEmail();
            assertThat(sent.to()).isEqualTo(EMAIL);
            assertThat(sent.htmlBody()).contains("25.10.2026 10:00").contains("25.10.2026 11:00");
        }

        @Test
        @DisplayName("Should not be blocked by a message that cannot be deserialized")
        void bookingConfirmed_poisonMessage_doesNotBlockFollowingMessages() throws Exception {
            // WHEN
            sendRaw("poison", "this is not json");
            sendRaw(KEY, BOOKING_CONFIRMED_JSON);

            // THEN
            EmailRequestDto sent = awaitSentEmail();
            assertThat(sent.to()).isEqualTo(EMAIL);
        }
    }

    private EmailRequestDto awaitSentEmail() {
        ArgumentCaptor<EmailRequestDto> captor = ArgumentCaptor.forClass(EmailRequestDto.class);
        verify(emailService, timeout(TIMEOUT_MS)).sendEmail(captor.capture());
        return captor.getValue();
    }

    private void sendRaw(String key, String value) throws Exception {
        rawProducer.send(new ProducerRecord<>(BOOKING_CONFIRMED_TOPIC, key, value)).get();
    }

    private KafkaProducer<String, String> createRawProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(props);
    }
}
