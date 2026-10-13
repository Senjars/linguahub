package io.github.senjar.paymentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.senjar.paymentservice.config.TestcontainersConfiguration;
import io.github.senjar.paymentservice.model.payment.Payment;
import io.github.senjar.paymentservice.model.payment.PaymentStatus;
import io.github.senjar.paymentservice.model.payment.PaymentType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentRepositoryTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long BOOKING_ID = 10L;

    @Autowired
    private PaymentRepository paymentRepository;

    @Nested
    @DisplayName("unique booking id")
    class UniqueBookingId {

        @Test
        @DisplayName("Should reject a second payment for the same booking")
        void save_secondPaymentForSameBooking_violatesUniqueConstraint() {
            // GIVEN
            paymentRepository.saveAndFlush(providePayment(BOOKING_ID, "cs_first"));

            // WHEN / THEN
            assertThatThrownBy(
                    () -> paymentRepository.saveAndFlush(providePayment(BOOKING_ID, "cs_second")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("Should allow payments for different bookings")
        void save_paymentsForDifferentBookings_areAllowed() {
            // GIVEN
            paymentRepository.saveAndFlush(providePayment(BOOKING_ID, "cs_first"));

            // WHEN / THEN
            assertThatCode(
                    () -> paymentRepository.saveAndFlush(
                            providePayment(BOOKING_ID + 1, "cs_second")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should find the single payment of a booking")
        void findByBookingId_existingPayment_returnsIt() {
            // GIVEN
            Payment saved = paymentRepository.saveAndFlush(providePayment(BOOKING_ID, "cs_first"));

            // WHEN
            var found = paymentRepository.findByBookingId(BOOKING_ID);

            // THEN
            assertThat(found).get().extracting(Payment::getId).isEqualTo(saved.getId());
        }
    }

    private Payment providePayment(Long bookingId, String sessionId) {
        Payment payment = new Payment();
        payment.setUserId(USER_ID);
        payment.setBookingId(bookingId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setType(PaymentType.PAY_PER_BOOKING);
        payment.setAmountToPay(new BigDecimal("100.00"));
        payment.setSessionId(sessionId);
        payment.setSessionUrl("https://checkout.stripe.com/c/pay/" + sessionId);
        return payment;
    }
}
