package com.liveklass.assignment.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    @DisplayName("각 ErrorCode가 의도한 HTTP 상태로 매핑된다")
    void status_mapping_is_correct() {
        assertThat(ErrorCode.INVALID_REQUEST.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.MISSING_HEADER.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.FORBIDDEN.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.CONFLICT.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.ILLEGAL_STATE_TRANSITION.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.CAPACITY_EXCEEDED.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.CANCELLATION_PERIOD_EXPIRED.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.DUPLICATE_PAYMENT.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.PAYMENT_GATEWAY_FAILURE.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("모든 ErrorCode의 code 값은 서로 중복되지 않는다")
    void codes_are_distinct() {
        long unique = java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .distinct()
                .count();
        assertThat(unique).isEqualTo(ErrorCode.values().length);
    }
}
