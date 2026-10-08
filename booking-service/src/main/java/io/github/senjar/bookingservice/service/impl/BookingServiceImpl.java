package io.github.senjar.bookingservice.service.impl;

import io.github.senjar.bookingservice.client.PaymentClient;
import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.payment.PaymentResponseDto;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotFullException;
import io.github.senjar.bookingservice.mapper.BookingMapper;
import io.github.senjar.bookingservice.model.booking.Booking;
import io.github.senjar.bookingservice.model.booking.Status;
import io.github.senjar.bookingservice.repository.BookingRepository;
import io.github.senjar.bookingservice.repository.SlotRepository;
import io.github.senjar.bookingservice.service.BookingService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PaymentClient paymentClient;
    private final SlotRepository slotRepository;

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto bookingRequestDto, UUID userId) {
        int reserved = slotRepository.tryReserveSlot(bookingRequestDto.slotId());
        if (reserved == 0) {
            throw new SlotFullException("Slot " + bookingRequestDto.slotId() + " is full or does not exist");
        }

        Booking booking = bookingMapper.toEntity(bookingRequestDto);
        booking.setStudentId(userId);
        booking.setStatus(Status.PENDING);

        Booking savedBooking = bookingRepository.save(booking);


        PaymentResponseDto paymentResponseDto = paymentClient.createLessonPayment(booking.getId());
        booking.setPaymentId(paymentResponseDto.id());

        log.info("Booking {} created for student {} (slot {}, payment {})",
                savedBooking.getId(), userId, bookingRequestDto.slotId(), paymentResponseDto.id());

        return bookingMapper.toDto(savedBooking, paymentResponseDto.sessionUrl());
    }

    @Override
    @Transactional
    public void confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() == Status.PENDING) {
            booking.setStatus(Status.CONFIRMED);
            bookingRepository.save(booking);
            log.info("Booking {} confirmed", bookingId);
        } else {
            log.info("Booking {} already {}, ignoring duplicate confirmation", bookingId, booking.getStatus());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto findBooking(Long bookingId, UUID userId) {
        Booking booking = bookingRepository.findByIdAndStudentId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        return bookingMapper.toDto(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> getBookingsForStudent(UUID userId) {
        return bookingRepository.findAllByStudentId(userId).stream()
                .map(bookingMapper::toDto)
                .toList();
    }
}
