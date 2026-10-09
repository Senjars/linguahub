package io.github.senjar.paymentservice.repository;

import io.github.senjar.paymentservice.model.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByUserId(UUID userId, Pageable pageable);

    Optional<Payment> findPaymentBySessionId(String sessionId);

    Optional<Payment> findByBookingId(Long bookingId);
}
