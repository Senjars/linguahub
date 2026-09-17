package io.github.senjar.paymentservice.repository;

import io.github.senjar.paymentservice.model.Payment;
import io.github.senjar.paymentservice.model.PaymentStatus;
import io.github.senjar.paymentservice.model.PaymentType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Optional<Payment> findPaymentBySessionId(String sessionId);

    Optional<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByUserIdAndTypeAndStatus(Long userId, PaymentType paymentType, PaymentStatus paymentStatus);
}
