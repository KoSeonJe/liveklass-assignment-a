package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.payment.PaymentGatewayException;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

class PaymentE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @MockBean
    PaymentGateway paymentGateway;

    @Test
    @DisplayName("결제 성공 시 200과 status=SUCCESS, enrollmentStatus=CONFIRMED를 반환한다")
    void pay_returns_200_success() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-key-1");

        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-1")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(200)
                .body("data.status", equalTo("SUCCESS"))
                .body("data.enrollmentStatus", equalTo("CONFIRMED"))
                .body("data.paymentId", notNullValue());
    }

    @Test
    @DisplayName("Idempotency-Key 헤더 누락 시 400을 반환한다")
    void pay_returns_400_when_idempotency_key_missing() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 400을 반환한다")
    void pay_returns_400_when_user_id_missing() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        given().port(port)
                .header("Idempotency-Key", "key-1")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }

    @Test
    @DisplayName("게이트웨이 실패 시 502와 PAYMENT_GATEWAY_FAILURE 코드를 반환하고 Enrollment는 PENDING으로 롤백된다")
    void pay_returns_502_on_gateway_failure() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);
        given(paymentGateway.charge(anyLong(), anyInt()))
                .willThrow(new PaymentGatewayException("카드 한도 초과"));

        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-fail")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(502)
                .body("code", equalTo("PAYMENT_GATEWAY_FAILURE"));

        Enrollment reloaded = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("동일 Idempotency-Key로 두 번째 요청 시 409와 DUPLICATE_PAYMENT 코드를 반환한다")
    void pay_returns_409_on_duplicate_key() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment first = savePendingEnrollment(course.getId(), 42L);
        Enrollment second = savePendingEnrollment(course.getId(), 43L);
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-1");

        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-dup")
        .when()
                .post("/api/enrollments/{id}/payment", first.getId())
        .then()
                .statusCode(200);

        given().port(port)
                .header("X-User-Id", 43)
                .header("Idempotency-Key", "key-dup")
        .when()
                .post("/api/enrollments/{id}/payment", second.getId())
        .then()
                .statusCode(409)
                .body("code", equalTo("DUPLICATE_PAYMENT"));
    }

    @Test
    @DisplayName("결제 실패 후 새로운 Idempotency-Key로 재결제 시 200을 반환한다")
    void pay_retry_after_failure_returns_200() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        given(paymentGateway.charge(anyLong(), anyInt()))
                .willThrow(new PaymentGatewayException("일시 오류"))
                .willReturn("ext-retry");

        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-1")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(502);

        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-2")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(200)
                .body("data.status", equalTo("SUCCESS"));
    }

    @Test
    @DisplayName("타인이 결제 시도 시 403과 FORBIDDEN 코드를 반환한다")
    void pay_returns_403_for_other_user() {
        Course course = saveOpenCourse(1L, 10, 10000);
        Enrollment enrollment = savePendingEnrollment(course.getId(), 42L);

        given().port(port)
                .header("X-User-Id", 99)
                .header("Idempotency-Key", "key-x")
        .when()
                .post("/api/enrollments/{id}/payment", enrollment.getId())
        .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 enrollmentId는 404를 반환한다")
    void pay_returns_404_for_missing_enrollment() {
        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-x")
        .when()
                .post("/api/enrollments/{id}/payment", 9999)
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
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
