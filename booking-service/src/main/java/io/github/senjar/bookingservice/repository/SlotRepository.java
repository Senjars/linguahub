package io.github.senjar.bookingservice.repository;

import io.github.senjar.bookingservice.model.slot.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
}
