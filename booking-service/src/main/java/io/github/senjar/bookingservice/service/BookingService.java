package io.github.senjar.bookingservice.service;

import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingResponseDto createBooking(BookingRequestDto bookingRequestDto, UUID userId, String email);

    BookingResponseDto findBooking(Long bookingId, UUID userId);

    List<BookingResponseDto> getBookingsForStudent(UUID userId);

    void confirmBooking(Long bookingId);

}
