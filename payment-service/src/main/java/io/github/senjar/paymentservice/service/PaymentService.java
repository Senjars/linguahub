package io.github.senjar.paymentservice.service;

import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponseDto createSingleLessonPayment(UUID userId, Long bookingId);

    Page<PaymentResponseDto> getPaymentsByUserId(UUID userId, Pageable pageable);

    PaymentResponseDto fulfillPayment(String sessionId);

    String handleCancel();

    void processWebhook(String payload, String sigHeader);
}
