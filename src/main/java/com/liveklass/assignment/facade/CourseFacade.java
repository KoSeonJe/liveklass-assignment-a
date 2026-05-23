package com.liveklass.assignment.facade;

import com.liveklass.assignment.api.dto.CourseResponse;
import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.service.CourseQueryService;
import com.liveklass.assignment.service.CourseService;
import com.liveklass.assignment.service.CourseStatusChangeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseService courseService;
    private final CourseQueryService courseQueryService;
    private final InMemoryCourseSeatCounter seatCounter;

    public Course create(Long creatorId, CreateCourseRequest request) {
        return courseService.create(creatorId, request);
    }

    public PageResponse<CourseResponse> list(int page, int size) {
        return courseQueryService.list(page, size);
    }

    public CourseResponse detail(Long courseId) {
        return courseQueryService.detail(courseId);
    }

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
