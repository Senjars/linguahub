package io.github.senjar.courseservice.mapper;

import io.github.senjar.courseservice.dto.enrollment.CreateEnrollmentDto;
import io.github.senjar.courseservice.dto.enrollment.EnrollmentDto;
import io.github.senjar.courseservice.model.Enrollment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {

    Enrollment toEntity(CreateEnrollmentDto createEnrollmentDto);

    EnrollmentDto toDto(Enrollment enrollment);
}
