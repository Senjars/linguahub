package io.github.senjar.bookingservice.dto.booking;

import io.github.senjar.bookingservice.model.booking.Status;
import java.util.UUID;

public record BookingResponseDto(
        Long id,
        Long slotId,
        UUID studentId,
        Status status,
        String sessionUrl
) {
}

