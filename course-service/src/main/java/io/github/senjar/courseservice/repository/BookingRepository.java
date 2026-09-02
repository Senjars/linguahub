package io.github.senjar.courseservice.repository;

import io.github.senjar.courseservice.model.booking.Booking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdAndStudentId(Long id, Long studentId);

    List<Booking> findAllByStudentId(Long userId);
}
