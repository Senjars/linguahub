package io.github.senjar.bookingservice.dto.payment;

public record PaymentResponseDto(
        Long id,
        String sessionUrl
) {
}
