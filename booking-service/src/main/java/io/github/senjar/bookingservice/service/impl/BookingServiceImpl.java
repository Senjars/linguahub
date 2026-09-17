package io.github.senjar.bookingservice.service.impl;

import io.github.senjar.bookingservice.client.PaymentClient;
import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.booking.UpdateBookingDto;
import io.github.senjar.bookingservice.dto.payment.PaymentResponseDto;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.mapper.BookingMapper;
import io.github.senjar.bookingservice.model.booking.Booking;
import io.github.senjar.bookingservice.model.booking.Status;
import io.github.senjar.bookingservice.repository.BookingRepository;
import io.github.senjar.bookingservice.service.BookingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto bookingRequestDto, Long userId) {
        Booking booking = bookingMapper.toEntity(bookingRequestDto);

        booking.setStudentId(userId);
        booking.setStatus(Status.PENDING);

        Booking savedBooking = bookingRepository.save(booking);

        PaymentResponseDto paymentResponseDto = paymentClient.createLessonPayment(userId, booking.getId());
        booking.setPaymentId(paymentResponseDto.id());

        return bookingMapper.toDto(savedBooking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto findBooking(Long bookingId, Long userId) {
        Booking booking = getBookingByIdAndStudentId(bookingId, userId);

        return bookingMapper.toDto(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getBookingsForStudent(Long userId) {
        return bookingRepository.findAllByStudentId(userId).stream()
                .map(bookingMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponseDto updateBooking(UpdateBookingDto updateBookingDto, Long bookingId,
                                            Long userId) {
        Booking booking = getBookingByIdAndStudentId(bookingId, userId);

        bookingMapper.updateBooking(booking, updateBookingDto);
        Booking updatedBooking = bookingRepository.save(booking);

        return bookingMapper.toDto(updatedBooking);
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = getBookingByIdAndStudentId(bookingId, userId);

        bookingRepository.delete(booking);
    }

    @Override
    @Transactional
    public void confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() == Status.PENDING) {
            booking.setStatus(Status.CONFIRMED);
            bookingRepository.save(booking);
        }
    }

    private Booking getBookingByIdAndStudentId(Long bookingId, Long userId) {
        return bookingRepository.findByIdAndStudentId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
    }
}
