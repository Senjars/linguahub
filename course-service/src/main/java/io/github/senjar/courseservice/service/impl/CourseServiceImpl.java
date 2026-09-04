package io.github.senjar.courseservice.service.impl;

import io.github.senjar.courseservice.dto.course.CourseDto;
import io.github.senjar.courseservice.dto.course.CreateCourseDto;
import io.github.senjar.courseservice.dto.course.UpdateCourseDto;
import io.github.senjar.courseservice.exception.EntityNotFoundException;
import io.github.senjar.courseservice.mapper.CourseMapper;
import io.github.senjar.courseservice.model.course.Course;
import io.github.senjar.courseservice.repository.CourseRepository;
import io.github.senjar.courseservice.service.CourseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;

    @Override
    public CourseDto createCourse(CreateCourseDto createCourseDto) {
        Course course = courseMapper.toEntity(createCourseDto);
        Course savedCourse = courseRepository.save(course);

        return courseMapper.toDto(savedCourse);
    }

    @Override
    public void deleteCourse(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(
                () -> new EntityNotFoundException(""));

        courseRepository.delete(course);
    }

    @Override
    public List<CourseDto> getCoursesList() {
        return courseRepository.findAll()
                .stream().map(courseMapper::toDto).toList();
    }

    @Override
    public CourseDto updateCourse(UpdateCourseDto updateCourseDto, Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(
                () -> new EntityNotFoundException(""));

        courseMapper.updateCourse(updateCourseDto, course);
        Course savedCourse = courseRepository.save(course);

        return courseMapper.toDto(savedCourse);
    }

    @Override
    public CourseDto findCourse(Long courseId) {
        Course course = courseRepository.findById(courseId).orElseThrow(
                () -> new EntityNotFoundException(""));

        return courseMapper.toDto(course);
    }
}
