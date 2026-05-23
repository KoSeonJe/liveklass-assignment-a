package com.liveklass.assignment.facade;

import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.service.CourseService;
import com.liveklass.assignment.service.CourseStatusChangeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final InMemoryCourseSeatCounter seatCounter;

    public CourseStatusChangeResult changeStatus(Long courseId, Long requesterId, CourseStatus updateCourseStatus) {
        CourseStatusChangeResult result = courseService.changeStatus(courseId, requesterId, updateCourseStatus);
        switch (result.status()) {
            case OPEN -> seatCounter.initialize(result.courseId(), result.remaining());
            case CLOSED -> seatCounter.remove(result.courseId());
            case DRAFT -> {
            }
        }
        return result;
    }
}
