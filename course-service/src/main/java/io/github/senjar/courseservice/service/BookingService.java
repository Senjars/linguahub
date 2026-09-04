package io.github.senjar.courseservice.service;

import io.github.senjar.courseservice.dto.booking.BookingRequestDto;
import io.github.senjar.courseservice.dto.booking.BookingResponseDto;
import io.github.senjar.courseservice.dto.booking.UpdateBookingDto;
import java.util.List;

public interface BookingService {

    BookingResponseDto createBooking(BookingRequestDto bookingRequestDto, Long userId);

    BookingResponseDto findBooking(Long bookingId, Long userId);

    List<BookingResponseDto> getBookingsForStudent(Long userId);

    BookingResponseDto updateBooking(UpdateBookingDto updateBookingDto, Long bookingId, Long userId);

    void cancelBooking(Long bookingId, Long userId);

}
