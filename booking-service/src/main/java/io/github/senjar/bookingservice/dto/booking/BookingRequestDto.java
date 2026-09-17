package io.github.senjar.bookingservice.dto.booking;

import jakarta.validation.constraints.NotNull;

public record BookingRequestDto(

        @NotNull
        Long slotId,

        @NotNull
        Long studentId
) {
}
