package com.liveklass.assignment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import com.liveklass.assignment.api.dto.PaymentResponse;
import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.DuplicatePaymentException;
import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentFailedException;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.payment.PaymentGatewayException;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.repository.PaymentRepository;
import com.liveklass.assignment.service.PaymentService;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

class PaymentFacadeTest extends AbstractIntegrationTest {

    @Autowired
    PaymentFacade paymentFacade;

    @MockBean
    PaymentGateway paymentGateway;

    @SpyBean
    PaymentService paymentService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    @DisplayName("결제 게이트웨이 Success 응답 시 Enrollment=CONFIRMED, Payment=SUCCESS로 종결된다")
    void pay_success_happy_path() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-success");

        PaymentResponse response = paymentFacade.pay(enrollment.getId(), 42L, "key-success");

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.enrollmentStatus()).isEqualTo("CONFIRMED");
        assertThat(response.paymentId()).isNotNull();

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);

        Payment payment = paymentRepository.findById(response.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getExternalPaymentKey()).isEqualTo("ext-success");
        assertThat(payment.getAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("결제 게이트웨이 Failure 응답 시 PaymentFailedException + Enrollment=PENDING, Payment=FAILED로 롤백된다")
    void pay_failure_rolls_back() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt()))
                .willThrow(new PaymentGatewayException("카드 한도 초과"));

        assertThatThrownBy(() -> paymentFacade.pay(enrollment.getId(), 42L, "key-fail"))
                .isInstanceOf(PaymentFailedException.class);

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(reloaded.getConfirmedAt()).isNull();

        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> p.getEnrollmentId().equals(enrollment.getId()))
                .findFirst().orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제 실패 후 새로운 idempotencyKey로 재결제 시 정상 처리된다")
    void retry_after_failure_succeeds() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        given(paymentGateway.charge(anyLong(), anyInt()))
                .willThrow(new PaymentGatewayException("일시 오류"))
                .willReturn("ext-retry");

        assertThatThrownBy(() -> paymentFacade.pay(enrollment.getId(), 42L, "key-1"))
                .isInstanceOf(PaymentFailedException.class);

        PaymentResponse response = paymentFacade.pay(enrollment.getId(), 42L, "key-2");

        assertThat(response.status()).isEqualTo("SUCCESS");
        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("동일 idempotencyKey 재요청 시 DuplicatePaymentException + Enrollment는 PENDING으로 보상된다")
    void duplicate_idempotency_key_throws_and_compensates() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment first = savePendingEnrollment(course.getId(), 42L);
        Enrollment second = savePendingEnrollment(course.getId(), 43L);

        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-1");
        paymentFacade.pay(first.getId(), 42L, "key-dup");

        assertThatThrownBy(() -> paymentFacade.pay(second.getId(), 43L, "key-dup"))
                .isInstanceOf(DuplicatePaymentException.class);

        Enrollment reloadedSecond = enrollmentRepository.findById(second.getId()).orElseThrow();
        assertThat(reloadedSecond.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(reloadedSecond.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("타인의 enrollmentId로 결제 시도 시 UnauthorizedException을 던진다")
    void other_user_throws() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        assertThatThrownBy(() -> paymentFacade.pay(enrollment.getId(), 99L, "key-1"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("markSuccess가 실패하면 게이트웨이 cancel·Payment FAILED·Enrollment PENDING 보정이 모두 수행되고 예외가 전파된다")
    void mark_success_failure_runs_best_effort_compensations() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-success");
        doThrow(new RuntimeException("forced markSuccess failure"))
                .when(paymentService).markSuccess(anyLong(), anyString());

        assertThatThrownBy(() -> paymentFacade.pay(enrollment.getId(), 42L, "key-broken"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced");

        then(paymentGateway).should().cancel("ext-success");
        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> p.getEnrollmentId().equals(enrollment.getId()))
                .findFirst().orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Course saveOpenCourse(Long creatorId, int max, int price) {
        Course course = Course.createDraft(creatorId, "t", "d", price, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        return courseRepository.save(course);
    }

    private Enrollment savePendingEnrollment(Long courseId, Long classmateId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        return enrollmentRepository.save(enrollment);
    }
}
