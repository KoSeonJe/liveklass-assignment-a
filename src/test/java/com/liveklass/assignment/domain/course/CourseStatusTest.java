package com.liveklass.assignment.domain.course;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CourseStatusTest {

    @ParameterizedTest(name = "{0} → {1} 전이는 허용된다")
    @CsvSource({
            "DRAFT, OPEN",
            "DRAFT, CLOSED",
            "OPEN, CLOSED",
            "CLOSED, OPEN"
    })
    void allowed_transitions(CourseStatus from, CourseStatus to) {
        assertThatCode(() -> from.verifyTransitionTo(to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} → {1} 전이는 IllegalCourseStateTransitionException을 던진다")
    @CsvSource({
            "DRAFT, DRAFT",
            "OPEN, OPEN",
            "OPEN, DRAFT",
            "CLOSED, DRAFT",
            "CLOSED, CLOSED"
    })
    void disallowed_transitions(CourseStatus from, CourseStatus to) {
        assertThatThrownBy(() -> from.verifyTransitionTo(to))
                .isInstanceOf(IllegalCourseStateTransitionException.class);
    }
}
