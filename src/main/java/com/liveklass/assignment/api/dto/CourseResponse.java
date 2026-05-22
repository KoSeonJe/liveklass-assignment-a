package com.liveklass.assignment.api.dto;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import java.time.LocalDate;

public record CourseResponse(
        Long id,
        Long creatorId,
        String title,
        String description,
        int price,
        int maxCapacity,
        int currentCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getMaxCapacity(),
                course.getCurrentCount(),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}
