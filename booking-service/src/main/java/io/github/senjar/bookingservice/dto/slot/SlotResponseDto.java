package io.github.senjar.bookingservice.dto.slot;

import java.time.LocalDateTime;
import java.util.UUID;

public record SlotResponseDto(
        Long id,
        UUID teacherId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int capacity,
        int bookedCount
) {
}
