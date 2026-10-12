package io.github.senjar.bookingservice.service.impl;

import io.github.senjar.bookingservice.client.PaymentClient;
import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.payment.PaymentResponseDto;
import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
import io.github.senjar.bookingservice.exception.DuplicateBookingException;
import io.github.senjar.bookingservice.exception.EntityNotFoundException;
import io.github.senjar.bookingservice.exception.SlotFullException;
import io.github.senjar.bookingservice.mapper.BookingMapper;
import io.github.senjar.bookingservice.model.booking.Booking;
import io.github.senjar.bookingservice.model.booking.Status;
import io.github.senjar.bookingservice.model.outbox.EventType;
import io.github.senjar.bookingservice.model.outbox.Outbox;
import io.github.senjar.bookingservice.model.slot.Slot;
import io.github.senjar.bookingservice.repository.BookingRepository;
import io.github.senjar.bookingservice.repository.OutboxRepository;
import io.github.senjar.bookingservice.repository.SlotRepository;
import io.github.senjar.bookingservice.service.BookingService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final List<Status> ACTIVE_STATUSES = List.of(Status.PENDING, Status.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PaymentClient paymentClient;
    private final SlotRepository slotRepository;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    @Override
    @Transactional
    public BookingResponseDto createBooking(BookingRequestDto bookingRequestDto,
                                            UUID userId, String email) {
        Long slotId = bookingRequestDto.slotId();

        if (bookingRepository.existsByStudentIdAndSlotIdAndStatusIn(userId, slotId, ACTIVE_STATUSES)) {
            throw new DuplicateBookingException(
                    "Student already has an active booking for slot " + slotId);
        }

        if (slotRepository.tryReserveSlot(slotId) == 0) {
            throw new SlotFullException("Slot " + slotId + " is full or does not exist");
        }

        Booking booking = bookingMapper.toEntity(bookingRequestDto);
        booking.setStudentId(userId);
        booking.setStudentEmail(email);
        booking.setStatus(Status.PENDING);

        Booking savedBooking = bookingRepository.save(booking);

        PaymentResponseDto paymentResponseDto =
                paymentClient.createLessonPayment(savedBooking.getId());
        savedBooking.setPaymentId(paymentResponseDto.id());

        log.info("Booking {} created for student {} (slot {}, payment {})",
                savedBooking.getId(), userId, slotId, paymentResponseDto.id());

        return bookingMapper.toDto(savedBooking, paymentResponseDto.sessionUrl());
    }

    @Override
    @Transactional
    public void confirmBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (booking.getStatus() == Status.CONFIRMED) {
            log.info("Booking {} already confirmed, ignoring duplicate confirmation", bookingId);
            return;
        }

        if (booking.getStatus() != Status.PENDING) {
            log.warn("Payment confirmed for booking {} in status {}, it needs a manual refund",
                    bookingId, booking.getStatus());
            return;
        }

        Slot slot = slotRepository.findById(booking.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Slot not found"));

        booking.setStatus(Status.CONFIRMED);
        bookingRepository.save(booking);
        log.info("Booking {} confirmed", bookingId);

        saveBookingConfirmedOutbox(booking, slot);
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

    private void saveBookingConfirmedOutbox(Booking booking, Slot slot) {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                booking.getId(), slot.getId(), booking.getStudentEmail(),
                slot.getStartTime(), slot.getEndTime());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to create outbox message for booking " + booking.getId(), e);
        }

        Outbox outboxMessage = new Outbox();
        outboxMessage.setEventType(EventType.BOOKING_CONFIRMED);
        outboxMessage.setPayload(payload);
        outboxRepository.save(outboxMessage);

        log.info("Outbox message saved for confirmed booking {}", booking.getId());
    }
}
