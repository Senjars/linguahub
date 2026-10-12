package io.github.senjar.bookingservice.repository;

import io.github.senjar.bookingservice.model.slot.Slot;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Modifying
    @Query("UPDATE Slot s SET s.bookedCount = s.bookedCount + 1 "
            + "WHERE s.id = :slotId AND s.bookedCount < s.capacity")
    int tryReserveSlot(@Param("slotId") Long slotId);

    @Query("SELECT COUNT(s) > 0 FROM Slot s "
            + "WHERE s.teacherId = :teacherId "
            + "AND s.startTime < :newEnd AND s.endTime > :newStart")
    boolean existsOverlapping(@Param("teacherId") UUID teacherId,
                              @Param("newStart") LocalDateTime newStart,
                              @Param("newEnd") LocalDateTime newEnd);
}
