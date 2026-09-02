package io.github.senjar.courseservice.service;

import io.github.senjar.courseservice.dto.enrollment.CreateEnrollmentDto;
import io.github.senjar.courseservice.dto.enrollment.EnrollmentDto;

public interface EnrollmentService {

    EnrollmentDto createEnrollment(CreateEnrollmentDto createEnrollmentDto, Long userId);

    void deleteEnrollment(Long userId, Long enrollmentId);
}
