package com.liveklass.assignment.domain.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentStatusTest {

    @ParameterizedTest(name = "{0} → {1} 전이는 허용된다")
    @CsvSource({
            "PENDING, SUCCESS",
            "PENDING, FAILED",
            "SUCCESS, CANCELLED"
    })
    void allowed_transitions(PaymentStatus from, PaymentStatus to) {
        assertThatCode(() -> from.verifyTransitionTo(to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} → {1} 전이는 IllegalPaymentStateTransitionException을 던진다")
    @CsvSource({
            "PENDING, PENDING",
            "PENDING, CANCELLED",
            "SUCCESS, PENDING",
            "SUCCESS, FAILED",
            "SUCCESS, SUCCESS",
            "FAILED, PENDING",
            "FAILED, SUCCESS",
            "FAILED, FAILED",
            "FAILED, CANCELLED",
            "CANCELLED, PENDING",
            "CANCELLED, SUCCESS",
            "CANCELLED, FAILED",
            "CANCELLED, CANCELLED"
    })
    void disallowed_transitions(PaymentStatus from, PaymentStatus to) {
        assertThatThrownBy(() -> from.verifyTransitionTo(to))
                .isInstanceOf(IllegalPaymentStateTransitionException.class);
    }
}
