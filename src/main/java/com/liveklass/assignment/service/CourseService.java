package com.liveklass.assignment.service;

import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    @Transactional
    public Course create(Long creatorId, CreateCourseRequest request) {
        Course course = Course.createDraft(
                creatorId,
                request.title(),
                request.description(),
                request.price(),
                request.maxCapacity(),
                request.startDate(),
                request.endDate()
        );
        return courseRepository.save(course);
    }

    @Transactional
    public CourseStatusChangeResult changeStatus(Long courseId, Long requesterId, CourseStatus next) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.verifyCreator(requesterId);
        course.transitionTo(next);
        course.validateRemainingCapacity();
        return new CourseStatusChangeResult(
                course.getId(),
                course.getStatus(),
                course.remainingCapacity()
        );
    }
}
