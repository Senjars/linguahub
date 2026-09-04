package io.github.senjar.courseservice.mapper;

import io.github.senjar.courseservice.dto.course.CourseDto;
import io.github.senjar.courseservice.dto.course.CreateCourseDto;
import io.github.senjar.courseservice.dto.course.UpdateCourseDto;
import io.github.senjar.courseservice.model.course.Course;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CourseMapper {

    Course toEntity(CreateCourseDto createCourseDto);

    CourseDto toDto(Course course);

    void updateCourse(UpdateCourseDto updateCourseDto, @MappingTarget Course course);
}
