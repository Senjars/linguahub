package io.github.senjar.paymentservice.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import io.github.senjar.paymentservice.client.BookingClient;
import io.github.senjar.paymentservice.dto.PaymentResponseDto;
import io.github.senjar.paymentservice.exception.PaymentException;
import io.github.senjar.paymentservice.mapper.PaymentMapper;
import io.github.senjar.paymentservice.model.Payment;
import io.github.senjar.paymentservice.model.PaymentStatus;
import io.github.senjar.paymentservice.model.PaymentType;
import io.github.senjar.paymentservice.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final BigDecimal SINGLE_LESSON_PRICE = new BigDecimal("100.00");

    private final PaymentRepository paymentRepository;
    private final StripeProvider stripeProvider;
    private final PaymentMapper paymentMapper;
    private final BookingClient bookingClient;

    @Override
    @Transactional
    public PaymentResponseDto createSingleLessonPayment(Long userId, Long bookingId) {
        Optional<Payment> existing = paymentRepository.findByBookingId(bookingId);

        if (existing.isPresent()) {
            Payment payment = existing.get();

            if (payment.getStatus() == PaymentStatus.PAID) {
                throw new PaymentException("Payment for booking with id: " + bookingId + " is already paid.");
            }

            if (payment.getStatus() == PaymentStatus.PENDING) {
                return paymentMapper.toDto(payment);
            }
        }

        String description = "Payment for booking ID: " + bookingId;
        Session session = createStripeSession(userId, bookingId, SINGLE_LESSON_PRICE, description);

        Payment payment = existing.orElseGet(Payment::new);
        payment.setUserId(userId);
        payment.setBookingId(bookingId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.PAY_PER_BOOKING);
        payment.setAmountToPay(SINGLE_LESSON_PRICE);
        payment.setSessionId(session.getId());
        payment.setSessionUrl(session.getUrl());

        return paymentMapper.toDto(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponseDto> getPaymentsByUserId(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(paymentMapper::toDto);
    }

    @Override
    @Transactional
    public PaymentResponseDto fulfillPayment(String sessionId) {
        Payment payment = paymentRepository.findPaymentBySessionId(sessionId).orElseThrow(
                () -> new EntityNotFoundException("Payment with session ID: "
                        + sessionId + " not found"));

        if (payment.getStatus() == PaymentStatus.PAID) {
            return paymentMapper.toDto(payment);
        }

        Session session = stripeProvider.getSession(sessionId);

        if (session == null) {
            throw new EntityNotFoundException("Session with ID: " + sessionId + " not found in Stripe");
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            throw new PaymentException("Payment with session ID: " + sessionId + " is not paid yet in Stripe");
        }

        payment.setStatus(PaymentStatus.PAID);
        Payment savedPayment = paymentRepository.save(payment);

        notifyBookingService(savedPayment.getBookingId());

        return paymentMapper.toDto(savedPayment);
    }

    private void notifyBookingService(Long bookingId) {
        try {
            bookingClient.confirmBooking(bookingId);
        } catch (Exception e) {
            log.error("Failed to notify booking-service about confirmation of booking {}: {}. "
                    + "Booking will remain PENDING despite successful payment — "
                    + "a retry/reconciliation mechanism is needed.", bookingId, e.getMessage());
        }
    }

    @Override
    public String handleCancel() {
        return "Payment cancelled successfully. No charges were made to your account.";
    }

    @Override
    @Transactional
    public void processWebhook(String payload, String sigHeader) {
        try {
            Event event = stripeProvider.getWebhookEvent(payload, sigHeader);

            if ("checkout.session.completed".equals(event.getType())) {
                Session session = extractSession(event);
                try {
                    this.fulfillPayment(session.getId());
                } catch (EntityNotFoundException e) {
                    log.warn("Received webhook for a locally unknown session: {}", session.getId());
                }
            }
        } catch (StripeException e) {
            throw new PaymentException("Webhook processing failed: " + e.getMessage());
        }
    }

    private Session extractSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        if (deserializer.getObject().isPresent()) {
            return (Session) deserializer.getObject().get();
        }

        log.warn("Could not safely deserialize the event's data object (event id: {}), "
                        + "likely an API version mismatch. Falling back to deserializeUnsafe().",
                event.getId());

        try {
            return (Session) deserializer.deserializeUnsafe();
        } catch (Exception e) {
            throw new PaymentException("Empty session in webhook event");
        }
    }

    private Session createStripeSession(Long userId, Long bookingId, BigDecimal amount, String description) {
        try {
            return stripeProvider.createSession(userId, bookingId, amount, description);
        } catch (StripeException e) {
            throw new PaymentException("Error creating Stripe payment session: " + e.getMessage());
        }
    }
}
