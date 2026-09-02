package io.github.senjar.courseservice.service.impl;

import io.github.senjar.courseservice.dto.booking.BookingRequestDto;
import io.github.senjar.courseservice.dto.booking.BookingResponseDto;
import io.github.senjar.courseservice.dto.booking.UpdateBookingDto;
import io.github.senjar.courseservice.exception.EntityNotFoundException;
import io.github.senjar.courseservice.mapper.BookingMapper;
import io.github.senjar.courseservice.model.booking.Booking;
import io.github.senjar.courseservice.repository.BookingRepository;
import io.github.senjar.courseservice.service.BookingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;

    @Override
    public BookingResponseDto createBooking(BookingRequestDto bookingRequestDto, Long userId) {
        Booking booking = bookingMapper.toEntity(bookingRequestDto);
        booking.setStudentId(userId);

        Booking savedBooking = bookingRepository.save(booking);
        return bookingMapper.toDto(savedBooking);
    }

    @Override
    public BookingResponseDto findBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        return bookingMapper.toDto(booking);
    }

    @Override
    public List<BookingResponseDto> getBookingsForStudent(Long userId) {
        return bookingRepository.findAllByStudentId(userId).stream()
                .map(bookingMapper::toDto)
                .toList();
    }

    @Override
    public BookingResponseDto updateBooking(UpdateBookingDto updateBookingDto, Long bookingId,
                                            Long userId) {
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        bookingMapper.updateBooking(booking, updateBookingDto);
        Booking updatedBooking = bookingRepository.save(booking);

        return bookingMapper.toDto(updatedBooking);
    }

    @Override
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        bookingRepository.delete(booking);
    }

    @Override
    public void confirmBooking(Long bookingId) {

    }
}
