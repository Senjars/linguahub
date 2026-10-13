package io.github.senjar.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.senjar.notificationservice.dto.EmailRequestDto;
import io.github.senjar.notificationservice.exception.EmailRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
            emailService.sendEmail(new EmailRequestDto(TO,
                    "Subject", "<p>Body</p>", IDEMPOTENCY_KEY));

            // THEN
            server.verify();
        }

        @ParameterizedTest
        @ValueSource(ints = {400, 401, 403, 422})
        @DisplayName("Should mark a final 4xx answer as rejected so Kafka does not retry it")
        void sendEmail_finalClientError_throwsEmailRejectedException(int status) {
            // GIVEN
            server.expect(requestTo(RESEND_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

            // WHEN / THEN
            assertThatThrownBy(() -> emailService.sendEmail(
                    new EmailRequestDto(TO, "Subject", "<p>Body</p>", IDEMPOTENCY_KEY)))
                    .isInstanceOf(EmailRejectedException.class)
                    .hasCauseInstanceOf(HttpClientErrorException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {408, 409, 429})
        @DisplayName("Should let a temporary 4xx answer through so Kafka retries it")
        void sendEmail_temporaryClientError_isNotMarkedAsRejected(int status) {
            // GIVEN
            server.expect(requestTo(RESEND_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

            // WHEN / THEN
            assertThatThrownBy(() -> emailService.sendEmail(
                    new EmailRequestDto(TO, "Subject", "<p>Body</p>", IDEMPOTENCY_KEY)))
                    .isInstanceOf(HttpClientErrorException.class)
                    .isNotInstanceOf(EmailRejectedException.class);
        }

        @Test
        @DisplayName("Should let a server error through so Kafka retries it")
        void sendEmail_serverError_throwsHttpServerErrorException() {
            // GIVEN
            server.expect(requestTo(RESEND_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

            // WHEN / THEN
            assertThatThrownBy(() -> emailService.sendEmail(
                    new EmailRequestDto(TO, "Subject", "<p>Body</p>", IDEMPOTENCY_KEY)))
                    .isInstanceOf(HttpServerErrorException.class);
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
