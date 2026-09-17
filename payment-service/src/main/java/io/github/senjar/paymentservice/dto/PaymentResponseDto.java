package io.github.senjar.paymentservice.dto;

import io.github.senjar.paymentservice.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponseDto(
        Long id,
        Long bookingId,
        BigDecimal amount,
        PaymentStatus status,
        String sessionUrl,
        LocalDateTime createdAt
) {
}
