package io.github.senjar.bookingservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.senjar.bookingservice.client.PaymentClient;
import io.github.senjar.bookingservice.dto.booking.BookingRequestDto;
import io.github.senjar.bookingservice.dto.booking.BookingResponseDto;
import io.github.senjar.bookingservice.dto.payment.PaymentResponseDto;
import io.github.senjar.bookingservice.event.BookingConfirmedEvent;
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
import io.github.senjar.bookingservice.service.impl.BookingServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
public class BookingServiceImplTest {

    private static final Long SLOT_ID = 5L;
    private static final Long BOOKING_ID = 10L;
    private static final Long PAYMENT_ID = 7L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "student@example.com";
    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/test";
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 25, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private JsonMapper objectMapper = JsonMapper.builder().build();

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Nested
    @DisplayName("createBooking")
    class CreateBooking {

        @Test
        @DisplayName("Should return the booking together with the payment link")
        void createBooking_validRequest_returnsBookingWithSessionUrl() {
            // GIVEN
            BookingRequestDto request = provideBookingRequest();
            BookingResponseDto expected = provideBookingResponse(SESSION_URL);
            givenSlotReservedAndBookingSaved(request);
            PaymentResponseDto payment = providePayment();
            when(paymentClient.createLessonPayment(BOOKING_ID)).thenReturn(payment);
            when(bookingMapper.toDto(any(Booking.class), eq(SESSION_URL))).thenReturn(expected);

            // WHEN
            BookingResponseDto result = bookingService.createBooking(request, USER_ID, EMAIL);

            // THEN
            assertThat(result).isEqualTo(expected);
            verifyNoInteractions(outboxRepository);
        }

        @Test
        @DisplayName("Should save a PENDING booking of the current student with the payment id")
        void createBooking_validRequest_savesPendingBookingOfCurrentStudent() {
            // GIVEN
            BookingRequestDto request = provideBookingRequest();
            givenSlotReservedAndBookingSaved(request);
            PaymentResponseDto payment = providePayment();
            when(paymentClient.createLessonPayment(BOOKING_ID)).thenReturn(payment);
            when(bookingMapper.toDto(any(Booking.class), eq(SESSION_URL)))
                    .thenReturn(provideBookingResponse(SESSION_URL));

            // WHEN
            bookingService.createBooking(request, USER_ID, EMAIL);

            // THEN
            ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(captor.capture());
            Booking saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(Status.PENDING);
            assertThat(saved.getStudentId()).isEqualTo(USER_ID);
            assertThat(saved.getStudentEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPaymentId()).isEqualTo(PAYMENT_ID);
        }

        @Test
        @DisplayName("Should throw SlotFullException when the slot is full and touch nothing else")
        void createBooking_fullSlot_throwsSlotFullException() {
            // GIVEN
            BookingRequestDto request = provideBookingRequest();
            when(slotRepository.tryReserveSlot(SLOT_ID)).thenReturn(0);

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID, EMAIL))
                    .isInstanceOf(SlotFullException.class);
            verifyNoInteractions(bookingMapper, bookingRepository, paymentClient);
        }

        @Test
        @DisplayName("Should not swallow a payment failure so the transaction can roll back")
        void createBooking_paymentFails_propagatesException() {
            // GIVEN
            BookingRequestDto request = provideBookingRequest();
            givenSlotReservedAndBookingSaved(request);
            when(paymentClient.createLessonPayment(BOOKING_ID))
                    .thenThrow(new IllegalStateException("payment-service unavailable"));

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.createBooking(request, USER_ID, EMAIL))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("payment-service unavailable");
        }

        private void givenSlotReservedAndBookingSaved(BookingRequestDto request) {
            when(slotRepository.tryReserveSlot(SLOT_ID)).thenReturn(1);
            when(bookingMapper.toEntity(request)).thenReturn(new Booking());
            when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
                Booking booking = invocation.getArgument(0);
                booking.setId(BOOKING_ID);
                return booking;
            });
        }
    }

    @Nested
    @DisplayName("confirmBooking")
    class ConfirmBooking {

        @Test
        @DisplayName("Should set CONFIRMED and store an unprocessed BOOKING_CONFIRMED outbox message")
        void confirmBooking_pendingBooking_setsConfirmedAndStoresOutboxMessage() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(provideSlot()));

            // WHEN
            bookingService.confirmBooking(BOOKING_ID);

            // THEN
            assertThat(booking.getStatus()).isEqualTo(Status.CONFIRMED);
            verify(bookingRepository).save(booking);
            ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
            verify(outboxRepository).save(captor.capture());
            Outbox outbox = captor.getValue();
            assertThat(outbox.getEventType()).isEqualTo(EventType.BOOKING_CONFIRMED);
            assertThat(outbox.getProcessed()).isFalse();
        }

        @Test
        @DisplayName("Should store a payload that the relay can read back as the same event")
        void confirmBooking_pendingBooking_payloadRoundTripsToTheEvent() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(provideSlot()));

            // WHEN
            bookingService.confirmBooking(BOOKING_ID);

            // THEN
            ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
            verify(outboxRepository).save(captor.capture());
            BookingConfirmedEvent restored =
                    objectMapper.readValue(captor.getValue().getPayload(), BookingConfirmedEvent.class);
            assertThat(restored)
                    .isEqualTo(new BookingConfirmedEvent(BOOKING_ID, SLOT_ID, EMAIL, START, END));
        }

        @Test
        @DisplayName("Should fail loudly and store nothing when the payload cannot be serialized")
        void confirmBooking_payloadSerializationFails_throwsSoTransactionRollsBack() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(provideSlot()));
            doThrow(new IllegalStateException("cannot serialize"))
                    .when(objectMapper).writeValueAsString(any());

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create outbox message");
            verify(outboxRepository, never()).save(any(Outbox.class));
        }

        @Test
        @DisplayName("Should ignore a duplicate confirmation and store no second outbox message")
        void confirmBooking_alreadyConfirmed_doesNothing() {
            // GIVEN
            Booking booking = provideBooking(Status.CONFIRMED);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

            // WHEN
            bookingService.confirmBooking(BOOKING_ID);

            // THEN
            verify(bookingRepository, never()).save(any(Booking.class));
            verifyNoInteractions(slotRepository, outboxRepository);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when the booking does not exist")
        void confirmBooking_nonExistingBooking_throwsEntityNotFoundException() {
            // GIVEN
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                    .isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(slotRepository, outboxRepository);
        }

        @Test
        @DisplayName("Should store no outbox message when the slot of the booking is missing")
        void confirmBooking_missingSlot_throwsAndStoresNoOutboxMessage() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
            when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.confirmBooking(BOOKING_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Slot");
            verifyNoInteractions(outboxRepository);
        }
    }

    @Nested
    @DisplayName("findBooking")
    class FindBooking {

        @Test
        @DisplayName("Should return the booking of the student")
        void findBooking_ownBooking_returnsBookingDto() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            BookingResponseDto expected = provideBookingResponse(null);
            when(bookingRepository.findByIdAndStudentId(BOOKING_ID, USER_ID))
                    .thenReturn(Optional.of(booking));
            when(bookingMapper.toDto(booking)).thenReturn(expected);

            // WHEN
            BookingResponseDto result = bookingService.findBooking(BOOKING_ID, USER_ID);

            // THEN
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException for a booking of another student")
        void findBooking_bookingOfAnotherStudent_throwsEntityNotFoundException() {
            // GIVEN
            when(bookingRepository.findByIdAndStudentId(BOOKING_ID, USER_ID))
                    .thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> bookingService.findBooking(BOOKING_ID, USER_ID))
                    .isInstanceOf(EntityNotFoundException.class);
            verifyNoInteractions(bookingMapper);
        }
    }

    @Nested
    @DisplayName("getBookingsForStudent")
    class GetBookingsForStudent {

        @Test
        @DisplayName("Should return the bookings of the student mapped to DTOs")
        void getBookingsForStudent_bookingsExist_returnsMappedList() {
            // GIVEN
            Booking booking = provideBooking(Status.PENDING);
            BookingResponseDto response = provideBookingResponse(null);
            when(bookingRepository.findAllByStudentId(USER_ID)).thenReturn(List.of(booking));
            when(bookingMapper.toDto(booking)).thenReturn(response);

            // WHEN
            List<BookingResponseDto> result = bookingService.getBookingsForStudent(USER_ID);

            // THEN
            assertThat(result).containsExactly(response);
        }

        @Test
        @DisplayName("Should return an empty list when the student has no bookings")
        void getBookingsForStudent_noBookings_returnsEmptyList() {
            // GIVEN
            when(bookingRepository.findAllByStudentId(USER_ID)).thenReturn(List.of());

            // WHEN
            List<BookingResponseDto> result = bookingService.getBookingsForStudent(USER_ID);

            // THEN
            assertThat(result).isEmpty();
            verifyNoInteractions(bookingMapper);
        }
    }

    private Booking provideBooking(Status status) {
        Booking booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setSlotId(SLOT_ID);
        booking.setStudentId(USER_ID);
        booking.setStudentEmail(EMAIL);
        booking.setStatus(status);
        return booking;
    }

    private Slot provideSlot() {
        return Slot.builder()
                .id(SLOT_ID)
                .teacherId(UUID.randomUUID())
                .startTime(START)
                .endTime(END)
                .capacity(1)
                .bookedCount(1)
                .build();
    }

    private BookingRequestDto provideBookingRequest() {
        return new BookingRequestDto(SLOT_ID);
    }

    private BookingResponseDto provideBookingResponse(String sessionUrl) {
        return new BookingResponseDto(BOOKING_ID, SLOT_ID, USER_ID, Status.PENDING, sessionUrl);
    }

    private PaymentResponseDto providePayment() {
        PaymentResponseDto payment = mock(PaymentResponseDto.class);
        when(payment.id()).thenReturn(PAYMENT_ID);
        when(payment.sessionUrl()).thenReturn(SESSION_URL);
        return payment;
    }
}
