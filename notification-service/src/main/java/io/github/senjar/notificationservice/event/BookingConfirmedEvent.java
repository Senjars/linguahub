package io.github.senjar.notificationservice.event;

import java.time.LocalDateTime;

public record BookingConfirmedEvent(
        Long bookingId,
        Long slotId,
        String studentEmail,
        LocalDateTime startTime,
        LocalDateTime endTime){
}
