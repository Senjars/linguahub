package io.github.senjar.courseservice.service.impl;

import io.github.senjar.courseservice.client.PaymentClient;
import io.github.senjar.courseservice.exception.AccessDeniedException;
import io.github.senjar.courseservice.exception.EntityNotFoundException;
import io.github.senjar.courseservice.dto.enrollment.CreateEnrollmentDto;
import io.github.senjar.courseservice.dto.enrollment.EnrollmentDto;
import io.github.senjar.courseservice.mapper.EnrollmentMapper;
import io.github.senjar.courseservice.model.Enrollment;
import io.github.senjar.courseservice.repository.EnrollmentRepository;
import io.github.senjar.courseservice.service.EnrollmentService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMapper enrollmentMapper;
    private final PaymentClient paymentClient;

    @Override
    public EnrollmentDto createEnrollment(CreateEnrollmentDto createEnrollmentDto, Long userId) {
        if (!paymentClient.getSubscriptionStatus(userId).isActive()) {
            throw new AccessDeniedException("You can't create enrollment "
                    + "without active subscription");
        }

        Enrollment enrollment = enrollmentMapper.toEntity(createEnrollmentDto);
        enrollment.setStudentId(userId);
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        return enrollmentMapper.toDto(enrollment);
    }

    @Override
    public void deleteEnrollment(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow(
                () -> new EntityNotFoundException("Entity with id: " + enrollmentId + " not found"));

        if (!Objects.equals(enrollment.getStudentId(), userId)) {
            throw new AccessDeniedException("You can't modify entity with id: " + enrollmentId);
        }

        enrollmentRepository.delete(enrollment);
    }
}
