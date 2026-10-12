package io.github.senjar.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.senjar.notificationservice.dto.EmailRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class EmailServiceTest {

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private static final String API_KEY = "test-key";
    private static final String FROM = "from@example.com";
    private static final String TO = "student@example.com";
    private static final String IDEMPOTENCY_KEY = "booking-confirmed/42";

    private MockRestServiceServer server;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        emailService = new EmailService(builder.build());
        ReflectionTestUtils.setField(emailService, "apiKey", API_KEY);
        ReflectionTestUtils.setField(emailService, "fromAddress", FROM);
    }

    @Nested
    @DisplayName("sendEmail")
    class SendEmail {

        @Test
        @DisplayName("Should send the idempotency key to Resend as the Idempotency-Key header")
        void sendEmail_validRequest_sendsIdempotencyKeyHeader() {
            // GIVEN
            server.expect(requestTo(RESEND_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer " + API_KEY))
                    .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                    .andExpect(jsonPath("$.from").value(FROM))
                    .andExpect(jsonPath("$.to[0]").value(TO))
                    .andRespond(withSuccess());

            // WHEN
            emailService.sendEmail(
                    new EmailRequestDto(TO, "Subject", "<p>Body</p>", IDEMPOTENCY_KEY));

            // THEN
            server.verify();
        }

        @Test
        @DisplayName("Should not swallow a Resend error so Kafka can retry the event")
        void sendEmail_resendReturnsConflict_propagatesException() {
            // GIVEN
            server.expect(requestTo(RESEND_URL))
                    .andRespond(withStatus(HttpStatus.CONFLICT));

            // WHEN / THEN
            assertThatThrownBy(() -> emailService.sendEmail(
                    new EmailRequestDto(TO, "Subject", "<p>Body</p>", IDEMPOTENCY_KEY)))
                    .isInstanceOf(RestClientResponseException.class);
        }
    }
}
