package com.liveklass.assignment.api;

import com.liveklass.assignment.api.dto.ChangeCourseStatusRequest;
import com.liveklass.assignment.api.dto.CourseResponse;
import com.liveklass.assignment.api.dto.CourseStatusChangeResponse;
import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.api.dto.EnrollmentResponse;
import com.liveklass.assignment.common.web.ApiResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.enrollment.EnrollmentResult;
import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.facade.EnrollmentFacade;
import com.liveklass.assignment.service.CourseQueryService;
import com.liveklass.assignment.service.CourseService;
import com.liveklass.assignment.service.CourseStatusChangeResult;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseQueryService courseQueryService;
    private final CourseFacade courseFacade;
    private final EnrollmentFacade enrollmentFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<CourseResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateCourseRequest request
    ) {
        Course course = courseService.create(userId, request);
        CourseResponse body = CourseResponse.from(course);
        return ResponseEntity
                .created(URI.create("/api/courses/" + course.getId()))
                .body(ApiResponse.of(body));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CourseResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(courseQueryService.list(page, size)));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseResponse>> detail(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.of(courseQueryService.detail(courseId)));
    }

    @PostMapping("/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long courseId
    ) {
        EnrollmentResult result = enrollmentFacade.enroll(courseId, userId);
        return switch (result) {
            case EnrollmentResult.Pending p -> ResponseEntity
                    .created(URI.create("/api/enrollments/" + p.enrollmentId()))
                    .body(ApiResponse.of(EnrollmentResponse.pending(p.enrollmentId())));
            case EnrollmentResult.Waitlisted w -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.of(EnrollmentResponse.waitlisted(courseId, w.position())));
        };
    }

    @PatchMapping("/{courseId}/status")
    public ResponseEntity<ApiResponse<CourseStatusChangeResponse>> changeStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long courseId,
            @Valid @RequestBody ChangeCourseStatusRequest request
    ) {
        CourseStatusChangeResult result = courseFacade.changeStatus(courseId, userId, request.status());
        return ResponseEntity.ok(ApiResponse.of(CourseStatusChangeResponse.from(result)));
    }
}
