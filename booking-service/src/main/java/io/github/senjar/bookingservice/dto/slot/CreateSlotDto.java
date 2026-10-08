package io.github.senjar.bookingservice.dto.slot;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateSlotDto(
        @NotNull @Future LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @Positive int capacity
) {

    @AssertTrue(message = "endTime must be after startTime")
    public boolean isEndAfterStart() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
