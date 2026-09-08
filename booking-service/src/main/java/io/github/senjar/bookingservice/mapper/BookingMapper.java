package io.github.senjar.bookingservice.mapper;

import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.booking.UpdateBookingDto;
import io.github.senjar.bookingservice.model.booking.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingResponseDto toDto(Booking booking);

    Booking toEntity(BookingRequestDto bookingRequestDto);

    void updateBooking(@MappingTarget Booking booking, UpdateBookingDto updateBookingDto);
}
