package com.liveklass.assignment.api;

import com.liveklass.assignment.api.dto.CourseResponse;
import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.common.web.ApiResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.service.CourseService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

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
}
