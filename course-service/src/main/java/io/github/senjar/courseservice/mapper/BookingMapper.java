package io.github.senjar.courseservice.mapper;

import io.github.senjar.courseservice.dto.booking.BookingRequestDto;
import io.github.senjar.courseservice.dto.booking.BookingResponseDto;
import io.github.senjar.courseservice.dto.booking.UpdateBookingDto;
import io.github.senjar.courseservice.model.booking.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingResponseDto toDto(Booking booking);

    Booking toEntity(BookingRequestDto bookingRequestDto);

    void updateBooking(@MappingTarget Booking booking, UpdateBookingDto updateBookingDto);
}
