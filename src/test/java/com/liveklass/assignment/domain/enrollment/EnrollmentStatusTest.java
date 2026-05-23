package com.liveklass.assignment.domain.enrollment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnrollmentStatusTest {

    @ParameterizedTest(name = "{0} → {1} 전이는 허용된다")
    @CsvSource({
            "PENDING, CONFIRMED",
            "CONFIRMED, PENDING",
            "CONFIRMED, CANCELLED"
    })
    void allowed_transitions(EnrollmentStatus from, EnrollmentStatus to) {
        assertThatCode(() -> from.verifyTransitionTo(to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} → {1} 전이는 IllegalEnrollmentStateTransitionException을 던진다")
    @CsvSource({
            "PENDING, PENDING",
            "PENDING, CANCELLED",
            "CONFIRMED, CONFIRMED",
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED",
            "CANCELLED, CANCELLED"
    })
    void disallowed_transitions(EnrollmentStatus from, EnrollmentStatus to) {
        assertThatThrownBy(() -> from.verifyTransitionTo(to))
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);
    }
}
