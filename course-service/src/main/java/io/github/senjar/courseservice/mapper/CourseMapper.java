package io.github.senjar.courseservice.mapper;

import io.github.senjar.courseservice.dto.course.CourseDto;
import io.github.senjar.courseservice.dto.course.CreateCourseDto;
import io.github.senjar.courseservice.dto.course.UpdateCourseDto;
import io.github.senjar.courseservice.model.course.Course;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.data.jpa.repository.JpaRepository;

@Mapper(componentModel = "spring")
public interface CourseMapper extends JpaRepository<Course, Long> {

    Course toEntity(CreateCourseDto createCourseDto);

    CourseDto toDto(Course course);

    Course updateCourse(@MappingTarget UpdateCourseDto updateCourseDto, Course course);
}
