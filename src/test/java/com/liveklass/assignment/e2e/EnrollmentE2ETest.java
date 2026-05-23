package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class EnrollmentE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    CourseFacade courseFacade;

    @Test
    @DisplayName("OPEN 강의 신청 시 201 Created + Location 헤더 + PENDING 응답")
    void enroll_returns_201_pending() {
        Course saved = openCourse(7L, 10);

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/courses/{id}/enrollments", saved.getId())
        .then()
                .statusCode(201)
                .header("Location", startsWith("/api/enrollments/"))
                .body("data.status", equalTo("PENDING"))
                .body("data.enrollmentId", notNullValue());
    }

    @Test
    @DisplayName("정원 만석 강의 신청 시 202 Accepted + WAITLISTED + position=1")
    void enroll_returns_202_waitlisted_when_full() {
        Course saved = openCourse(7L, 1);
        given().port(port).header("X-User-Id", 100)
                .post("/api/courses/{id}/enrollments", saved.getId())
                .then().statusCode(201);

        given().port(port)
                .header("X-User-Id", 101)
        .when()
                .post("/api/courses/{id}/enrollments", saved.getId())
        .then()
                .statusCode(202)
                .body("data.status", equalTo("WAITLISTED"))
                .body("data.position", equalTo(1))
                .body("data.courseId", equalTo(saved.getId().intValue()));
    }

    @Test
    @DisplayName("DRAFT 강의 신청 시 409 CONFLICT")
    void enroll_returns_409_for_draft_course() {
        Course draft = saveDraft(7L, 10);

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/courses/{id}/enrollments", draft.getId())
        .then()
                .statusCode(409)
                .body("code", equalTo("CONFLICT"));
    }

    @Test
    @DisplayName("CLOSED 강의 신청 시 409 CONFLICT")
    void enroll_returns_409_for_closed_course() {
        Course saved = openCourse(7L, 10);
        courseFacade.changeStatus(saved.getId(), 7L, CourseStatus.CLOSED);

        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/courses/{id}/enrollments", saved.getId())
        .then()
                .statusCode(409)
                .body("code", equalTo("CONFLICT"));
    }

    @Test
    @DisplayName("미존재 강의 ID 신청 시 409 CONFLICT (404 분리 미구현)")
    void enroll_returns_409_for_missing_course() {
        given().port(port)
                .header("X-User-Id", 42)
        .when()
                .post("/api/courses/{id}/enrollments", 9999)
        .then()
                .statusCode(409)
                .body("code", equalTo("CONFLICT"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 400 MISSING_HEADER")
    void enroll_returns_400_when_header_missing() {
        Course saved = openCourse(7L, 10);

        given().port(port)
        .when()
                .post("/api/courses/{id}/enrollments", saved.getId())
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }

    @Test
    @DisplayName("100스레드 동시 신청 시 정확히 10명 PENDING + 90명 WAITLISTED, DB current_count=10")
    void concurrent_enrollment_does_not_exceed_capacity() throws Exception {
        int capacity = 10;
        int threads = 100;
        Course saved = openCourse(7L, capacity);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger pending = new AtomicInteger();
        AtomicInteger waitlisted = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int userId = 1000 + i;
            pool.submit(() -> {
                try {
                    start.await();
                    int status = given().port(port).header("X-User-Id", userId)
                            .post("/api/courses/{id}/enrollments", saved.getId())
                            .statusCode();
                    if (status == 201) {
                        pending.incrementAndGet();
                    } else if (status == 202) {
                        waitlisted.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(other.get()).isZero();
        assertThat(pending.get()).isEqualTo(capacity);
        assertThat(waitlisted.get()).isEqualTo(threads - capacity);

        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(capacity);

        List<com.liveklass.assignment.domain.enrollment.Enrollment> all =
                enrollmentRepository.findAll();
        long pendingCount = all.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING)
                .count();
        assertThat(pendingCount).isEqualTo(capacity);
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }

    private Course openCourse(Long creatorId, int max) {
        Course saved = saveDraft(creatorId, max);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        return saved;
    }
}
