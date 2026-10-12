package io.github.senjar.paymentservice.repository;

import io.github.senjar.paymentservice.model.outbox.Outbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findTop50ByProcessedFalseAndRetryCountLessThan(int retryNumber);
}
