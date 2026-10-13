package io.github.senjar.notificationservice.service;

import io.github.senjar.notificationservice.dto.EmailRequestDto;
import io.github.senjar.notificationservice.exception.EmailRejectedException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final RestClient resendRestClient;

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-address}")
    private String fromAddress;

    public void sendEmail(EmailRequestDto request) {
        Map<String, Object> payload = Map.of(
                "from", fromAddress,
                "to", List.of(request.to()),
                "subject", request.subject(),
                "html", request.htmlBody()
        );

        try {
            resendRestClient.post()
                    .uri("/emails")
                    .headers(headers -> {
                        headers.setBearerAuth(apiKey);
                        headers.set(IDEMPOTENCY_HEADER, request.idempotencyKey());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (isTransient(e.getStatusCode())) {
                throw e;
            }
            throw new EmailRejectedException(
                    "Resend rejected the email with status " + e.getStatusCode().value(), e);
        }

        log.info("Email sent to {} (idempotency key: {})", request.to(), request.idempotencyKey());
    }

    private boolean isTransient(HttpStatusCode status) {
        int code = status.value();
        return code == 408 || code == 409 || code == 429;
    }
}
