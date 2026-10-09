package io.github.senjar.bookingservice.event;

import java.time.LocalDateTime;

public record BookingConfirmedEvent(
        Long bookingId,
        Long slotId,
        String studentEmail,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
