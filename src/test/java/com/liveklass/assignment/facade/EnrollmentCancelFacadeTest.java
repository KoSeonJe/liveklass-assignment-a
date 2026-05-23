package com.liveklass.assignment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.liveklass.assignment.api.dto.PaymentResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentResult;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import com.liveklass.assignment.domain.waitlist.InMemoryCourseWaitlist;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.repository.PaymentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import com.liveklass.assignment.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class EnrollmentCancelFacadeTest extends AbstractIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Autowired
    EnrollmentFacade enrollmentFacade;

    @Autowired
    PaymentFacade paymentFacade;

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Autowired
    InMemoryCourseWaitlist waitlist;

    @Autowired
    MutableClock clock;

    @MockBean
    PaymentGateway paymentGateway;

    @Test
    @DisplayName("CONFIRMED + SUCCESS 결제 상태에서 취소 시 카운터 해제, 결제 게이트웨이 취소 호출, Payment=CANCELLED로 종결된다")
    void cancel_saga_releases_seat_and_cancels_payment() {
        Course course = openCourse(7L, 10, 10000);
        Enrollment enrollment = pendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-1");
        PaymentResponse pay = paymentFacade.pay(enrollment.getId(), 42L, "key-1");
        markEnrollmentConfirmedAt(enrollment.getId(), CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        enrollmentFacade.cancelEnrollment(enrollment.getId(), 42L);

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        Course reloadedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloadedCourse.getCurrentCount()).isZero();
        assertThat(seatCounter.remaining(course.getId())).isEqualTo(10);
        Payment payment = paymentRepository.findById(pay.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        then(paymentGateway).should().cancel("ext-1");
    }

    @Test
    @DisplayName("정원 1 강의에 PENDING+WAITLISTED 1명씩 있을 때 취소하면 다음 대기자가 PENDING으로 승급된다")
    void cancel_promotes_next_waitlisted_user() {
        Course course = openCourse(7L, 1, 10000);
        EnrollmentResult first = enrollmentFacade.enroll(course.getId(), 42L);
        EnrollmentResult second = enrollmentFacade.enroll(course.getId(), 43L);
        assertThat(first).isInstanceOf(EnrollmentResult.Pending.class);
        assertThat(second).isInstanceOf(EnrollmentResult.Waitlisted.class);
        Long firstEnrollmentId = ((EnrollmentResult.Pending) first).enrollmentId();

        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-1");
        paymentFacade.pay(firstEnrollmentId, 42L, "key-1");
        markEnrollmentConfirmedAt(firstEnrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        enrollmentFacade.cancelEnrollment(firstEnrollmentId, 42L);

        Course reloadedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloadedCourse.getCurrentCount()).isEqualTo(1);
        assertThat(seatCounter.remaining(course.getId())).isZero();
        assertThat(waitlist.positionOf(course.getId(), 43L)).isEmpty();
        Enrollment promoted = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassmateId().equals(43L))
                .findFirst().orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("취소 대상 Enrollment에 SUCCESS 결제가 없으면 결제 게이트웨이 cancel은 호출되지 않는다")
    void cancel_skips_payment_when_no_success_payment() {
        Course course = openCourse(7L, 10, 10000);
        Enrollment confirmed = enrollmentRepository.save(confirmedEnrollment(course.getId(), 42L, CONFIRMED_AT));
        int updated = courseRepository.tryIncreaseCurrentCount(course.getId());
        assertThat(updated).isEqualTo(1);
        clock.set(CONFIRMED_AT.plusDays(1));

        enrollmentFacade.cancelEnrollment(confirmed.getId(), 42L);

        then(paymentGateway).should(never()).cancel(anyString());
    }

    @Test
    @DisplayName("PG 취소 실패 시 enrollment는 CONFIRMED로 보상되고 카운터·결제는 원복된다")
    void cancel_reverts_enrollment_when_gateway_cancel_fails() {
        Course course = openCourse(7L, 10, 10000);
        Enrollment enrollment = pendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-1");
        PaymentResponse pay = paymentFacade.pay(enrollment.getId(), 42L, "key-1");
        markEnrollmentConfirmedAt(enrollment.getId(), CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));
        willThrow(new RuntimeException("PG down")).given(paymentGateway).cancel(anyString());

        assertThatThrownBy(() -> enrollmentFacade.cancelEnrollment(enrollment.getId(), 42L))
                .isInstanceOf(RuntimeException.class);

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        Course reloadedCourse = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloadedCourse.getCurrentCount()).isEqualTo(1);
        assertThat(seatCounter.remaining(course.getId())).isEqualTo(9);
        Payment payment = paymentRepository.findById(pay.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    private Course openCourse(Long creatorId, int max, int price) {
        Course course = Course.createDraft(creatorId, "t", "d", price, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        Course saved = courseRepository.save(course);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        return courseRepository.findById(saved.getId()).orElseThrow();
    }

    private Enrollment pendingEnrollment(Long courseId, Long classmateId) {
        int updated = courseRepository.tryIncreaseCurrentCount(courseId);
        assertThat(updated).isEqualTo(1);
        seatCounter.tryAcquire(courseId);
        return enrollmentRepository.save(Enrollment.create(courseId, classmateId));
    }

    private Enrollment confirmedEnrollment(Long courseId, Long classmateId, LocalDateTime confirmedAt) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        enrollment.confirm(confirmedAt);
        return enrollment;
    }

    private void markEnrollmentConfirmedAt(Long enrollmentId, LocalDateTime confirmedAt) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        try {
            java.lang.reflect.Field f = Enrollment.class.getDeclaredField("confirmedAt");
            f.setAccessible(true);
            f.set(enrollment, confirmedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        enrollmentRepository.saveAndFlush(enrollment);
    }
}
