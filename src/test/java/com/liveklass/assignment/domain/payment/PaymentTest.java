package com.liveklass.assignment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

    @Test
    @DisplayName("create는 status=PENDING, externalPaymentKey=null, amount 스냅샷으로 생성한다")
    void create_initializes_pending_with_null_external_key() {
        Payment payment = Payment.create(10L, "idem-1", 12345);

        assertThat(payment.getEnrollmentId()).isEqualTo(10L);
        assertThat(payment.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(payment.getAmount()).isEqualTo(12345);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getExternalPaymentKey()).isNull();
    }

    @Test
    @DisplayName("create는 음수 amount를 거부한다")
    void create_rejects_negative_amount() {
        assertThatThrownBy(() -> Payment.create(10L, "idem-1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create는 enrollmentId null을 거부한다")
    void create_rejects_null_enrollment_id() {
        assertThatThrownBy(() -> Payment.create(null, "idem-1", 10000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "create는 idempotencyKey \"{0}\"을(를) 거부한다")
    @ValueSource(strings = {"", "   "})
    @DisplayName("create는 idempotencyKey null·공백을 거부한다")
    void create_rejects_blank_idempotency_key(String key) {
        assertThatThrownBy(() -> Payment.create(10L, key, 10000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create는 idempotencyKey null을 거부한다")
    void create_rejects_null_idempotency_key() {
        assertThatThrownBy(() -> Payment.create(10L, null, 10000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markSuccess는 PENDING→SUCCESS 전이와 externalPaymentKey를 세팅한다")
    void markSuccess_transitions_and_sets_external_key() {
        Payment payment = Payment.create(10L, "idem-1", 10000);

        payment.markSuccess("ext-key");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getExternalPaymentKey()).isEqualTo("ext-key");
    }

    @ParameterizedTest(name = "markSuccess는 externalPaymentKey \"{0}\"을(를) 거부한다")
    @ValueSource(strings = {"", "   "})
    @DisplayName("markSuccess는 externalPaymentKey null·공백을 거부한다")
    void markSuccess_rejects_blank_external_key(String key) {
        Payment payment = Payment.create(10L, "idem-1", 10000);

        assertThatThrownBy(() -> payment.markSuccess(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markSuccess는 externalPaymentKey null을 거부한다")
    void markSuccess_rejects_null_external_key() {
        Payment payment = Payment.create(10L, "idem-1", 10000);

        assertThatThrownBy(() -> payment.markSuccess(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markFailed는 PENDING→FAILED 전이한다")
    void markFailed_transitions_to_failed() {
        Payment payment = Payment.create(10L, "idem-1", 10000);

        payment.markFailed();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("markSuccess는 이미 SUCCESS인 경우 IllegalPaymentStateTransitionException을 던진다")
    void markSuccess_rejects_when_already_success() {
        Payment payment = Payment.create(10L, "idem-1", 10000);
        payment.markSuccess("ext-key");

        assertThatThrownBy(() -> payment.markSuccess("ext-key-2"))
                .isInstanceOf(IllegalPaymentStateTransitionException.class);
    }

    @Test
    @DisplayName("markFailed는 SUCCESS 상태에서 호출 시 거부한다")
    void markFailed_rejects_when_success() {
        Payment payment = Payment.create(10L, "idem-1", 10000);
        payment.markSuccess("ext-key");

        assertThatThrownBy(payment::markFailed)
                .isInstanceOf(IllegalPaymentStateTransitionException.class);
    }

    @Test
    @DisplayName("cancel은 SUCCESS→CANCELLED 전이한다")
    void cancel_transitions_success_to_cancelled() {
        Payment payment = Payment.create(10L, "idem-1", 10000);
        payment.markSuccess("ext-key");

        payment.cancel();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel은 PENDING 상태에서 호출 시 거부한다")
    void cancel_rejects_when_pending() {
        Payment payment = Payment.create(10L, "idem-1", 10000);

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalPaymentStateTransitionException.class);
    }

    @Test
    @DisplayName("cancel은 FAILED 상태에서 호출 시 거부한다")
    void cancel_rejects_when_failed() {
        Payment payment = Payment.create(10L, "idem-1", 10000);
        payment.markFailed();

        assertThatThrownBy(payment::cancel)
                .isInstanceOf(IllegalPaymentStateTransitionException.class);
    }
}
