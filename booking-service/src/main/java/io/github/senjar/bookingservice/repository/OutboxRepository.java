package io.github.senjar.bookingservice.repository;

import io.github.senjar.bookingservice.model.outbox.Outbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    List<Outbox> findTop50ByProcessedFalseAndRetryCountLessThan(int retryNumber);
}
