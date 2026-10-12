package io.github.senjar.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import io.github.senjar.paymentservice.exception.PaymentException;
import io.github.senjar.paymentservice.mapper.PaymentMapper;
import io.github.senjar.paymentservice.model.outbox.EventType;
import io.github.senjar.paymentservice.model.outbox.Outbox;
import io.github.senjar.paymentservice.model.payment.Payment;
import io.github.senjar.paymentservice.model.payment.PaymentStatus;
import io.github.senjar.paymentservice.model.payment.PaymentType;
import io.github.senjar.paymentservice.repository.OutboxRepository;
import io.github.senjar.paymentservice.repository.PaymentRepository;
import io.github.senjar.paymentservice.service.impl.PaymentServiceImpl;
import io.github.senjar.paymentservice.service.impl.StripeProvider;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long BOOKING_ID = 10L;
    private static final String SESSION_ID = "cs_test_123";
    private static final String SESSION_URL = "https://checkout.stripe.com/c/pay/cs_test_123";
    private static final String PAYLOAD = "{\"id\":\"evt_1\"}";
    private static final String SIGNATURE = "t=1,v1=abc";
    private static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    private static final BigDecimal PRICE = new BigDecimal("100.00");
    private static final String DESCRIPTION = "Payment for booking ID: 10";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StripeProvider stripeProvider;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private JsonMapper objectMapper = JsonMapper.builder().build();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Nested
    @DisplayName("createSingleLessonPayment")
    class CreateSingleLessonPayment {

        @Test
        @DisplayName("Should create a Stripe session and save a PENDING payment when none exists")
        void createSingleLessonPayment_noExistingPayment_savesPendingPayment() throws Exception {
            // GIVEN
            PaymentResponseDto expected = providePaymentResponse();
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
            when(stripeProvider.createSession(USER_ID, BOOKING_ID, PRICE, DESCRIPTION))
                    .thenReturn(provideSession("unpaid"));
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentMapper.toDto(any(Payment.class))).thenReturn(expected);

            // WHEN
            PaymentResponseDto result =
                    paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID);

            // THEN
            assertThat(result).isSameAs(expected);
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            Payment saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getBookingId()).isEqualTo(BOOKING_ID);
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getType()).isEqualTo(PaymentType.PAY_PER_BOOKING);
            assertThat(saved.getAmountToPay()).isEqualByComparingTo(PRICE);
            assertThat(saved.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(saved.getSessionUrl()).isEqualTo(SESSION_URL);
            verifyNoInteractions(outboxRepository);
        }

        @Test
        @DisplayName("Should return the existing PENDING payment without calling Stripe again")
        void createSingleLessonPayment_existingPending_returnsExistingWithoutStripe() {
            // GIVEN
            Payment existing = providePayment(PaymentStatus.PENDING);
            PaymentResponseDto expected = providePaymentResponse();
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existing));
            when(paymentMapper.toDto(existing)).thenReturn(expected);

            // WHEN
            PaymentResponseDto result =
                    paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID);

            // THEN
            assertThat(result).isSameAs(expected);
            verifyNoInteractions(stripeProvider);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw PaymentException when the booking is already paid")
        void createSingleLessonPayment_existingPaid_throwsPaymentException() {
            // GIVEN
            Payment existing = providePayment(PaymentStatus.PAID);
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existing));

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("already paid");
            verifyNoInteractions(stripeProvider);
        }

        @Test
        @DisplayName("Should reuse the FAILED payment record and open a new Stripe session")
        void createSingleLessonPayment_existingFailed_reusesRecordWithNewSession() throws Exception {
            // GIVEN
            Payment existing = providePayment(PaymentStatus.FAILED);
            existing.setSessionId("cs_old");
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existing));
            when(stripeProvider.createSession(USER_ID, BOOKING_ID, PRICE, DESCRIPTION))
                    .thenReturn(provideSession("unpaid"));
            when(paymentRepository.save(any(Payment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(paymentMapper.toDto(any(Payment.class))).thenReturn(providePaymentResponse());

            // WHEN
            paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID);

            // THEN
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(existing);
            assertThat(existing.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(existing.getSessionId()).isEqualTo(SESSION_ID);
        }

        @Test
        @DisplayName("Should throw PaymentException when Stripe fails to create the session")
        void createSingleLessonPayment_stripeFails_throwsPaymentException() throws Exception {
            // GIVEN
            StripeException stripeException = mock(StripeException.class);
            when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
            when(stripeProvider.createSession(USER_ID, BOOKING_ID, PRICE, DESCRIPTION))
                    .thenThrow(stripeException);

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.createSingleLessonPayment(USER_ID, BOOKING_ID))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Error creating Stripe payment session");
            verify(paymentRepository, never()).save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("getPaymentsByUserId")
    class GetPaymentsByUserId {

        @Test
        @DisplayName("Should return the payments of the user mapped to DTOs")
        void getPaymentsByUserId_paymentsExist_returnsMappedPage() {
            // GIVEN
            Pageable pageable = PageRequest.of(0, 10);
            Payment payment = providePayment(PaymentStatus.PAID);
            PaymentResponseDto response = providePaymentResponse();
            Page<Payment> page = new PageImpl<>(List.of(payment), pageable, 1);
            when(paymentRepository.findByUserId(USER_ID, pageable)).thenReturn(page);
            when(paymentMapper.toDto(payment)).thenReturn(response);

            // WHEN
            Page<PaymentResponseDto> result = paymentService.getPaymentsByUserId(USER_ID, pageable);

            // THEN
            assertThat(result.getContent()).containsExactly(response);
        }

        @Test
        @DisplayName("Should return an empty page when the user has no payments")
        void getPaymentsByUserId_noPayments_returnsEmptyPage() {
            // GIVEN
            Pageable pageable = PageRequest.of(0, 10);
            when(paymentRepository.findByUserId(USER_ID, pageable)).thenReturn(Page.empty());

            // WHEN
            Page<PaymentResponseDto> result = paymentService.getPaymentsByUserId(USER_ID, pageable);

            // THEN
            assertThat(result.getContent()).isEmpty();
            verifyNoInteractions(paymentMapper);
        }
    }

    @Nested
    @DisplayName("fulfillPayment")
    class FulfillPayment {

        @Test
        @DisplayName("Should mark the payment PAID and store a PAYMENT_CONFIRMED outbox message")
        void fulfillPayment_pendingPaidInStripe_marksPaidAndStoresOutboxMessage() {
            // GIVEN
            Payment payment = providePayment(PaymentStatus.PENDING);
            PaymentResponseDto expected = providePaymentResponse();
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(stripeProvider.getSession(SESSION_ID)).thenReturn(provideSession("paid"));
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.toDto(payment)).thenReturn(expected);

            // WHEN
            PaymentResponseDto result = paymentService.fulfillPayment(SESSION_ID);

            // THEN
            assertThat(result).isSameAs(expected);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            verifyOutboxMessageSavedFor(BOOKING_ID);
        }

        @Test
        @DisplayName("Should be idempotent: an already PAID payment is returned without Stripe or outbox")
        void fulfillPayment_alreadyPaid_doesNothing() {
            // GIVEN
            Payment payment = providePayment(PaymentStatus.PAID);
            PaymentResponseDto expected = providePaymentResponse();
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(paymentMapper.toDto(payment)).thenReturn(expected);

            // WHEN
            PaymentResponseDto result = paymentService.fulfillPayment(SESSION_ID);

            // THEN
            assertThat(result).isSameAs(expected);
            verifyNoInteractions(stripeProvider, outboxRepository);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when the session is unknown locally")
        void fulfillPayment_unknownSession_throwsEntityNotFoundException() {
            // GIVEN
            when(paymentRepository.findPaymentBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.fulfillPayment(SESSION_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(SESSION_ID);
            verifyNoInteractions(stripeProvider, outboxRepository);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when Stripe does not know the session")
        void fulfillPayment_sessionMissingInStripe_throwsEntityNotFoundException() {
            // GIVEN
            Payment payment = providePayment(PaymentStatus.PENDING);
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(stripeProvider.getSession(SESSION_ID)).thenReturn(null);

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.fulfillPayment(SESSION_ID))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("not found in Stripe");
            verifyNoInteractions(outboxRepository);
        }

        @Test
        @DisplayName("Should throw PaymentException and keep PENDING when Stripe says it is not paid")
        void fulfillPayment_notPaidInStripe_throwsPaymentException() {
            // GIVEN
            Payment payment = providePayment(PaymentStatus.PENDING);
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(stripeProvider.getSession(SESSION_ID)).thenReturn(provideSession("unpaid"));

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.fulfillPayment(SESSION_ID))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("not paid yet");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository, never()).save(any(Payment.class));
            verifyNoInteractions(outboxRepository);
        }
    }

    @Nested
    @DisplayName("handleCancel")
    class HandleCancel {

        @Test
        @DisplayName("Should return the cancellation message")
        void handleCancel_always_returnsCancellationMessage() {
            // WHEN
            String message = paymentService.handleCancel();

            // THEN
            assertThat(message).contains("cancelled").contains("No charges");
        }
    }

    @Nested
    @DisplayName("processWebhook")
    class ProcessWebhook {

        @Test
        @DisplayName("Should fulfil the payment on checkout.session.completed")
        void processWebhook_checkoutCompleted_fulfillsPayment() throws Exception {
            // GIVEN
            Session session = provideSession("paid");
            Payment payment = providePayment(PaymentStatus.PENDING);
            PaymentResponseDto response = providePaymentResponse();
            Event event = provideEvent(CHECKOUT_COMPLETED);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(session));
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(stripeProvider.getSession(SESSION_ID)).thenReturn(session);
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.toDto(payment)).thenReturn(response);

            // WHEN
            paymentService.processWebhook(PAYLOAD, SIGNATURE);

            // THEN
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            verifyOutboxMessageSavedFor(BOOKING_ID);
        }

        @Test
        @DisplayName("Should fall back to unsafe deserialization when the safe one is empty")
        void processWebhook_safeDeserializationEmpty_usesUnsafeDeserialization() throws Exception {
            // GIVEN
            Session session = provideSession("paid");
            Payment payment = providePayment(PaymentStatus.PENDING);
            PaymentResponseDto response = providePaymentResponse();
            Event event = provideEvent(CHECKOUT_COMPLETED);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe()).thenReturn(session);
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
            when(paymentRepository.findPaymentBySessionId(SESSION_ID))
                    .thenReturn(Optional.of(payment));
            when(stripeProvider.getSession(SESSION_ID)).thenReturn(session);
            when(paymentRepository.save(payment)).thenReturn(payment);
            when(paymentMapper.toDto(payment)).thenReturn(response);

            // WHEN
            paymentService.processWebhook(PAYLOAD, SIGNATURE);

            // THEN
            verifyOutboxMessageSavedFor(BOOKING_ID);
        }

        @Test
        @DisplayName("Should throw PaymentException when the event data cannot be deserialized")
        void processWebhook_deserializationFails_throwsPaymentException() throws Exception {
            // GIVEN
            Event event = provideEvent(CHECKOUT_COMPLETED);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.empty());
            when(deserializer.deserializeUnsafe())
                    .thenThrow(new IllegalStateException("cannot deserialize"));
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenReturn(event);

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.processWebhook(PAYLOAD, SIGNATURE))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Empty session in webhook event");
            verifyNoInteractions(outboxRepository);
        }

        @Test
        @DisplayName("Should ignore a webhook for a session unknown locally so Stripe stops retrying")
        void processWebhook_unknownLocalSession_isIgnored() throws Exception {
            // GIVEN
            Session session = provideSession("paid");
            Event event = provideEvent(CHECKOUT_COMPLETED);
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
            when(deserializer.getObject()).thenReturn(Optional.of(session));
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenReturn(event);
            when(paymentRepository.findPaymentBySessionId(SESSION_ID)).thenReturn(Optional.empty());

            // WHEN / THEN
            assertThatCode(() -> paymentService.processWebhook(PAYLOAD, SIGNATURE))
                    .doesNotThrowAnyException();
            verifyNoInteractions(outboxRepository);
        }

        @Test
        @DisplayName("Should ignore events of other types")
        void processWebhook_otherEventType_isIgnored() throws Exception {
            // GIVEN
            Event event = provideEvent("payment_intent.created");
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenReturn(event);

            // WHEN
            paymentService.processWebhook(PAYLOAD, SIGNATURE);

            // THEN
            verifyNoInteractions(paymentRepository, outboxRepository);
        }

        @Test
        @DisplayName("Should throw PaymentException when the signature is invalid")
        void processWebhook_invalidSignature_throwsPaymentException() throws Exception {
            // GIVEN
            SignatureVerificationException invalid =
                    new SignatureVerificationException("Invalid signature", SIGNATURE);
            when(stripeProvider.getWebhookEvent(PAYLOAD, SIGNATURE)).thenThrow(invalid);

            // WHEN / THEN
            assertThatThrownBy(() -> paymentService.processWebhook(PAYLOAD, SIGNATURE))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Webhook processing failed");
            verifyNoInteractions(paymentRepository, outboxRepository);
        }
    }

    private void verifyOutboxMessageSavedFor(Long bookingId) {
        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        Outbox outbox = captor.getValue();
        assertThat(outbox.getEventType()).isEqualTo(EventType.PAYMENT_CONFIRMED);
        assertThat(outbox.getProcessed()).isFalse();
        assertThat(JsonPath.parse(outbox.getPayload()).read("$.bookingId", Long.class))
                .isEqualTo(bookingId);
    }

    private Payment providePayment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setUserId(USER_ID);
        payment.setBookingId(BOOKING_ID);
        payment.setStatus(status);
        payment.setType(PaymentType.PAY_PER_BOOKING);
        payment.setAmountToPay(PRICE);
        payment.setSessionId(SESSION_ID);
        payment.setSessionUrl(SESSION_URL);
        return payment;
    }

    private Session provideSession(String paymentStatus) {
        Session session = new Session();
        session.setId(SESSION_ID);
        session.setUrl(SESSION_URL);
        session.setPaymentStatus(paymentStatus);
        return session;
    }

    private Event provideEvent(String type) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        return event;
    }

    private PaymentResponseDto providePaymentResponse() {
        return mock(PaymentResponseDto.class);
    }
}
