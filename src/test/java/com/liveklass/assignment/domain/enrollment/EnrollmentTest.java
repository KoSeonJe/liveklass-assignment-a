package com.liveklass.assignment.domain.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 0, 0, 0);

    private static Enrollment pending(Long classmateId) {
        return Enrollment.create(100L, classmateId);
    }

    private static Enrollment confirmed(Long classmateId, LocalDateTime confirmedAt) {
        Enrollment e = pending(classmateId);
        e.confirm(confirmedAt);
        return e;
    }

    private static void setId(Enrollment e, Long id) throws Exception {
        Field f = Enrollment.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(e, id);
    }

    @Test
    @DisplayName("create는 status=PENDING, confirmedAt/cancelledAt=null로 생성한다")
    void create_starts_pending() {
        Enrollment e = pending(1L);
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(e.getConfirmedAt()).isNull();
        assertThat(e.getCancelledAt()).isNull();
        assertThat(e.getCourseId()).isEqualTo(100L);
        assertThat(e.getClassmateId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("create는 courseId/classmateId null을 거부한다")
    void create_rejects_null_ids() {
        assertThatThrownBy(() -> Enrollment.create(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Enrollment.create(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirm은 PENDING→CONFIRMED 전이와 confirmedAt을 세팅한다")
    void confirm_transitions_and_sets_confirmedAt() {
        Enrollment e = pending(1L);
        e.confirm(CONFIRMED_AT);
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(e.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
    }

    @Test
    @DisplayName("rollbackToPending은 CONFIRMED→PENDING으로 되돌리고 confirmedAt을 null로 만든다")
    void rollback_clears_confirmedAt() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        e.rollbackToPending();
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(e.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("rollbackToPending은 PENDING 상태에서 호출 시 거부한다")
    void rollback_from_pending_throws() {
        Enrollment e = pending(1L);
        assertThatThrownBy(e::rollbackToPending)
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);
    }

    @Test
    @DisplayName("cancelByClassmate는 CONFIRMED 시점부터 7일 이내(6일 23시 59분)에는 허용된다")
    void cancel_within_7days_allowed() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        LocalDateTime now = CONFIRMED_AT.plusDays(6).plusHours(23).plusMinutes(59);
        assertThatCode(() -> e.cancelByClassmate(1L, now)).doesNotThrowAnyException();
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(e.getCancelledAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("cancelByClassmate는 정확히 7일 정각에는 허용된다 (경계 포함)")
    void cancel_at_exactly_7days_allowed() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        LocalDateTime now = CONFIRMED_AT.plusDays(7);
        assertThatCode(() -> e.cancelByClassmate(1L, now)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cancelByClassmate는 7일 1초 초과 시 CancellationPeriodExpiredException을 던진다")
    void cancel_after_7days_throws() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        LocalDateTime now = CONFIRMED_AT.plusDays(7).plusSeconds(1);
        assertThatThrownBy(() -> e.cancelByClassmate(1L, now))
                .isInstanceOf(CancellationPeriodExpiredException.class);
    }

    @Test
    @DisplayName("cancelByClassmate는 본인이 아닌 요청자를 UnauthorizedException으로 거부한다")
    void cancel_rejects_non_owner() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        assertThatThrownBy(() -> e.cancelByClassmate(2L, CONFIRMED_AT.plusDays(1)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("cancelByClassmate는 PENDING 상태에서 호출 시 IllegalEnrollmentStateTransitionException을 던진다")
    void cancel_from_pending_throws() throws Exception {
        Enrollment e = pending(1L);
        setId(e, 9L);
        assertThatThrownBy(() -> e.cancelByClassmate(1L, CONFIRMED_AT))
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);
    }

    @Test
    @DisplayName("revertCancel은 CANCELLED→CONFIRMED로 복귀시키고 cancelledAt을 null로 만든다")
    void revertCancel_restores_confirmed() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        e.cancelByClassmate(1L, CONFIRMED_AT.plusDays(1));
        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);

        e.revertCancel();

        assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(e.getCancelledAt()).isNull();
        assertThat(e.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
    }

    @Test
    @DisplayName("revertCancel은 CANCELLED가 아닌 상태에서 호출 시 IllegalEnrollmentStateTransitionException을 던진다")
    void revertCancel_from_non_cancelled_throws() {
        Enrollment e = confirmed(1L, CONFIRMED_AT);
        assertThatThrownBy(e::revertCancel)
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);
    }
}
