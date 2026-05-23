package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.CancellationPeriodExpiredException;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentNotFoundException;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.enrollment.IllegalEnrollmentStateTransitionException;
import com.liveklass.assignment.dto.CancelledEnrollment;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import com.liveklass.assignment.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentCancelServiceTest extends AbstractIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Autowired
    EnrollmentService enrollmentService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    MutableClock clock;

    @Test
    @DisplayName("CONFIRMED 수강 신청을 7일 이내 본인이 취소하면 CANCELLED 전이 + current_count -1")
    void cancel_within_period_success() {
        Course course = saveOpenWithCount(7L, 10, 1);
        Enrollment confirmed = saveConfirmedEnrollment(course.getId(), 42L, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        CancelledEnrollment result = enrollmentService.cancel(confirmed.getId(), 42L);

        assertThat(result.enrollmentId()).isEqualTo(confirmed.getId());
        assertThat(result.courseId()).isEqualTo(course.getId());
        Enrollment reloaded = enrollmentRepository.findById(confirmed.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(reloaded.getCancelledAt()).isEqualTo(CONFIRMED_AT.plusDays(1));
        Course reloadedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloadedCourse.getCurrentCount()).isZero();
    }

    @Test
    @DisplayName("7일 1초 초과 후 취소 시 CancellationPeriodExpiredException이 발생한다")
    void cancel_after_window_throws() {
        Course course = saveOpenWithCount(7L, 10, 1);
        Enrollment confirmed = saveConfirmedEnrollment(course.getId(), 42L, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(7).plusSeconds(1));

        assertThatThrownBy(() -> enrollmentService.cancel(confirmed.getId(), 42L))
                .isInstanceOf(CancellationPeriodExpiredException.class);

        Enrollment reloaded = enrollmentRepository.findById(confirmed.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("본인이 아닌 요청자가 취소 시도 시 UnauthorizedException이 발생한다")
    void cancel_by_other_user_throws() {
        Course course = saveOpenWithCount(7L, 10, 1);
        Enrollment confirmed = saveConfirmedEnrollment(course.getId(), 42L, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        assertThatThrownBy(() -> enrollmentService.cancel(confirmed.getId(), 99L))
                .isInstanceOf(UnauthorizedException.class);

        Enrollment reloaded = enrollmentRepository.findById(confirmed.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("PENDING 수강 신청 취소 시 IllegalEnrollmentStateTransitionException이 발생한다")
    void cancel_pending_throws() {
        Course course = saveOpenWithCount(7L, 10, 1);
        Enrollment pending = enrollmentRepository.save(Enrollment.create(course.getId(), 42L));
        clock.set(CONFIRMED_AT.plusDays(1));

        assertThatThrownBy(() -> enrollmentService.cancel(pending.getId(), 42L))
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);

        Enrollment reloaded = enrollmentRepository.findById(pending.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("미존재 enrollmentId 취소 시 EnrollmentNotFoundException이 발생한다")
    void cancel_missing_enrollment_throws() {
        assertThatThrownBy(() -> enrollmentService.cancel(9999L, 42L))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    private Course saveOpenWithCount(Long creatorId, int max, int initialCount) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        Course saved = courseRepository.save(course);
        for (int i = 0; i < initialCount; i++) {
            int updated = courseRepository.tryIncreaseCurrentCount(saved.getId());
            assertThat(updated).isEqualTo(1);
        }
        return courseRepository.findById(saved.getId()).orElseThrow();
    }

    private Enrollment saveConfirmedEnrollment(Long courseId, Long classmateId, LocalDateTime confirmedAt) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        enrollment.confirm(confirmedAt);
        return enrollmentRepository.save(enrollment);
    }
}
