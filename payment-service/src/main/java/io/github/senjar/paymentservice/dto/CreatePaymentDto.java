package io.github.senjar.paymentservice.dto;

public record CreatePaymentDto(
        Long bookingId,
        Long userId,
        String paymentMethod
) {
}
