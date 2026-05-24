package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentNotFoundException;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import com.liveklass.assignment.dto.PaymentPreparedInfo;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.repository.PaymentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentCompensationServiceTest extends AbstractIntegrationTest {

    @Autowired
    PaymentCompensationService paymentCompensationService;

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
    @DisplayName("compensate 호출 시 Payment FAILED 전이와 Enrollment CONFIRMED→PENDING 롤백이 함께 반영된다")
    void compensate_marks_payment_failed_and_rolls_back_enrollment() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);
        PaymentPreparedInfo prepared = enrollPaymentService.confirmAndPreparePayment(enrollmentId, 42L, "key-1");

        paymentCompensationService.compensate(prepared.paymentId(), enrollmentId);

        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("enrollmentId 미존재 시 예외가 발생하고 Payment FAILED 전이도 함께 롤백된다 (atomic 보장)")
    void compensate_rolls_back_payment_when_enrollment_missing() {
        Course course = saveOpenPriced(7L, 10, 15000);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);
        PaymentPreparedInfo prepared = enrollPaymentService.confirmAndPreparePayment(enrollmentId, 42L, "key-1");

        assertThatThrownBy(() -> paymentCompensationService.compensate(prepared.paymentId(), 9999L))
                .isInstanceOf(EnrollmentNotFoundException.class);

        Payment payment = paymentRepository.findById(prepared.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    private Course saveOpenPriced(Long creatorId, int max, int price) {
        Course course = Course.createDraft(creatorId, "t", "d", price, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        return courseRepository.save(course);
    }
}
