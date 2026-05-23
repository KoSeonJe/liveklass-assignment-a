package com.liveklass.assignment.api.dto;

import com.liveklass.assignment.domain.course.CourseStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeCourseStatusRequest(
        @NotNull CourseStatus status
) {
}
