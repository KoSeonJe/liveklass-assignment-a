package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentNotFoundException;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.enrollment.IllegalEnrollmentStateTransitionException;
import com.liveklass.assignment.domain.payment.DuplicatePaymentException;
import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.repository.PaymentRepository;
import com.liveklass.assignment.dto.PaymentPreparedInfo;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollPaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    EnrollPaymentService enrollPaymentService;

    @Autowired
    EnrollmentService enrollmentService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    @DisplayName("confirmAndPreparePayment는 한 트랜잭션으로 Enrollment를 CONFIRMED로 전이하고 PENDING Payment를 저장한다")
    void confirm_and_prepare_payment_happy_path() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);

        PaymentPreparedInfo prepared = enrollPaymentService.confirmAndPreparePayment(enrollmentId, 42L, "key-1");

        assertThat(prepared.amount()).isEqualTo(15000);
        assertThat(prepared.paymentId()).isNotNull();

        Enrollment reloadedEnrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(reloadedEnrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(reloadedEnrollment.getConfirmedAt()).isNotNull();

        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        assertThat(payment.getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(payment.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(payment.getAmount()).isEqualTo(15000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("타인의 Enrollment confirmAndPreparePayment 호출 시 UnauthorizedException을 던지고 상태 변경 없음")
    void confirm_and_prepare_payment_other_user_throws() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);

        assertThatThrownBy(() -> enrollPaymentService.confirmAndPreparePayment(enrollmentId, 99L, "key-1"))
                .isInstanceOf(UnauthorizedException.class);

        Enrollment reloaded = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 enrollmentId는 EnrollmentNotFoundException을 던진다")
    void confirm_and_prepare_payment_missing_throws() {
        assertThatThrownBy(() -> enrollPaymentService.confirmAndPreparePayment(9999L, 42L, "key-1"))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    @DisplayName("이미 CONFIRMED인 Enrollment의 confirmAndPreparePayment는 IllegalEnrollmentStateTransitionException을 던지고 Payment 저장 없음")
    void confirm_and_prepare_payment_already_confirmed_throws() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);
        enrollPaymentService.confirmAndPreparePayment(enrollmentId, 42L, "key-1");

        assertThatThrownBy(() -> enrollPaymentService.confirmAndPreparePayment(enrollmentId, 42L, "key-2"))
                .isInstanceOf(IllegalEnrollmentStateTransitionException.class);

        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("동일 idempotencyKey 두 번째 호출은 DuplicatePaymentException + Enrollment 상태 롤백된다")
    void duplicate_idempotency_key_rolls_back_enrollment() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long firstEnrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);
        Long secondEnrollmentId = enrollmentService.createEnrollment(course.getId(), 43L);
        enrollPaymentService.confirmAndPreparePayment(firstEnrollmentId, 42L, "key-dup");

        assertThatThrownBy(() ->
                enrollPaymentService.confirmAndPreparePayment(secondEnrollmentId, 43L, "key-dup"))
                .isInstanceOf(DuplicatePaymentException.class);

        Enrollment secondReloaded = enrollmentRepository.findById(secondEnrollmentId).orElseThrow();
        assertThat(secondReloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(secondReloaded.getConfirmedAt()).isNull();
    }

    private Course saveOpenPriced(Long creatorId, int max, int price) {
        Course course = Course.createDraft(creatorId, "t", "d", price, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        return courseRepository.save(course);
    }
}
