package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import com.liveklass.assignment.support.MutableClock;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

class CancelEnrollmentE2ETest extends AbstractIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    MutableClock clock;

    @MockBean
    PaymentGateway paymentGateway;

    @Test
    @DisplayName("CONFIRMED 후 1일 경과 시점 취소는 200을 반환하고 status=CANCELLED를 응답한다")
    void cancel_returns_200_within_window() {
        Course course = openCourse(7L, 10, 10000);
        Long enrollmentId = enrollAndPay(course.getId(), 42L, "key-1", "ext-1");
        markConfirmedAt(enrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/cancel", enrollmentId)
        .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"))
                .body("data.enrollmentId", equalTo(enrollmentId.intValue()));

        Enrollment reloaded = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("CONFIRMED 후 9일 경과 시점 취소는 409 CANCELLATION_PERIOD_EXPIRED를 반환한다")
    void cancel_returns_409_after_window_expired() {
        Course course = openCourse(7L, 10, 10000);
        Long enrollmentId = enrollAndPay(course.getId(), 42L, "key-2", "ext-2");
        markConfirmedAt(enrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(9));

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/cancel", enrollmentId)
        .then()
                .statusCode(409)
                .body("code", equalTo("CANCELLATION_PERIOD_EXPIRED"));

        Enrollment reloaded = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("정원 1 강의에 PENDING+WAITLISTED 구성에서 취소 시 대기자가 PENDING으로 자동 승급된다")
    void cancel_promotes_waitlisted_user() {
        Course course = openCourse(7L, 1, 10000);

        given().port(port).header("X-User-Id", 42)
                .post("/api/courses/{id}/enrollments", course.getId())
                .then().statusCode(201);

        given().port(port).header("X-User-Id", 43)
                .post("/api/courses/{id}/enrollments", course.getId())
                .then().statusCode(202);

        Long firstEnrollmentId = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassmateId().equals(42L))
                .findFirst().orElseThrow().getId();
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-promote");
        given().port(port)
                .header("X-User-Id", 42)
                .header("Idempotency-Key", "key-promote")
                .post("/api/enrollments/{id}/payment", firstEnrollmentId)
                .then().statusCode(200);

        markConfirmedAt(firstEnrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/cancel", firstEnrollmentId)
        .then()
                .statusCode(200);

        Enrollment promoted = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassmateId().equals(43L))
                .findFirst().orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타인이 취소 시도 시 403 FORBIDDEN을 반환한다")
    void cancel_returns_403_for_other_user() {
        Course course = openCourse(7L, 10, 10000);
        Long enrollmentId = enrollAndPay(course.getId(), 42L, "key-3", "ext-3");
        markConfirmedAt(enrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        given().port(port)
                .header("X-User-Id", 99)
        .when()
                .post("/api/enrollments/{id}/cancel", enrollmentId)
        .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("PENDING 수강 신청 취소 시 409 ILLEGAL_STATE_TRANSITION을 반환한다")
    void cancel_returns_409_for_pending() {
        Course course = openCourse(7L, 10, 10000);
        given().port(port).header("X-User-Id", 42)
                .post("/api/courses/{id}/enrollments", course.getId())
                .then().statusCode(201);
        Long enrollmentId = enrollmentRepository.findAll().get(0).getId();

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/cancel", enrollmentId)
        .then()
                .statusCode(409)
                .body("code", equalTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("미존재 enrollmentId 취소 시 404 NOT_FOUND를 반환한다")
    void cancel_returns_404_for_missing_enrollment() {
        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/enrollments/{id}/cancel", 9999)
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    private Course openCourse(Long creatorId, int max, int price) {
        Course course = Course.createDraft(creatorId, "t", "d", price, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        Course saved = courseRepository.save(course);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        return courseRepository.findById(saved.getId()).orElseThrow();
    }

    private Long enrollAndPay(Long courseId, Long userId, String idempotencyKey, String externalKey) {
        given().port(port).header("X-User-Id", userId)
                .post("/api/courses/{id}/enrollments", courseId)
                .then().statusCode(201);
        Long enrollmentId = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassmateId().equals(userId))
                .findFirst().orElseThrow().getId();
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn(externalKey);
        given().port(port)
                .header("X-User-Id", userId)
                .header("Idempotency-Key", idempotencyKey)
                .post("/api/enrollments/{id}/payment", enrollmentId)
                .then().statusCode(200);
        return enrollmentId;
    }

    private void markConfirmedAt(Long enrollmentId, LocalDateTime confirmedAt) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        try {
            Field f = Enrollment.class.getDeclaredField("confirmedAt");
            f.setAccessible(true);
            f.set(enrollment, confirmedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        enrollmentRepository.saveAndFlush(enrollment);
    }
}
