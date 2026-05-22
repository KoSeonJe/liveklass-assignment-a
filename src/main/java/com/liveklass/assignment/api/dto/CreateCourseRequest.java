package com.liveklass.assignment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCourseRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        String description,

        @PositiveOrZero
        int price,

        @Positive
        int maxCapacity,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {
}
