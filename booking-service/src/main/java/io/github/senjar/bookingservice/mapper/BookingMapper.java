package io.github.senjar.bookingservice.mapper;

import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.model.booking.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "sessionUrl", ignore = true)
    BookingResponseDto toDto(Booking booking);

    @Mapping(target = "sessionUrl", source = "sessionUrl")
    BookingResponseDto toDto(Booking booking, String sessionUrl);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "studentEmail", ignore = true)
    Booking toEntity(BookingRequestDto bookingRequestDto);
}
