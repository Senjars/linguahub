package io.github.senjar.courseservice.service;

import io.github.senjar.courseservice.dto.course.CourseDto;
import io.github.senjar.courseservice.dto.course.CreateCourseDto;
import io.github.senjar.courseservice.dto.course.UpdateCourseDto;
import java.util.List;

public interface CourseService {

    CourseDto createCourse(CreateCourseDto createCourseDto);

    void deleteCourse(Long courseId);

    List<CourseDto> getCoursesList();

    CourseDto updateCourse(UpdateCourseDto updateCourseDto, Long courseId);

    CourseDto findCourse(Long courseId);
}
