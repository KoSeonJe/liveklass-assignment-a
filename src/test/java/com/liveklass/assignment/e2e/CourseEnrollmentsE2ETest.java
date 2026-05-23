package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class CourseEnrollmentsE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("크리에이터가 본인 강의의 신청 목록 조회 시 200과 페이지 메타를 반환한다")
    void listEnrollments_returns_200_for_creator() {
        Course course = saveCourse(10L);
        savePending(200L, course.getId());
        savePending(201L, course.getId());

        given().port(port)
                .header("X-User-Id", 10)
        .when()
                .get("/api/courses/{id}/enrollments", course.getId())
        .then()
                .statusCode(200)
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.totalElements", equalTo(2))
                .body("data.items", hasSize(2))
                .body("data.items.classmateId", containsInAnyOrder(200, 201))
                .body("data.items.status", contains("PENDING", "PENDING"));
    }

    @Test
    @DisplayName("다른 사용자가 호출하면 403 FORBIDDEN을 반환한다")
    void listEnrollments_returns_403_for_non_creator() {
        Course course = saveCourse(10L);
        savePending(200L, course.getId());

        given().port(port)
                .header("X-User-Id", 99)
        .when()
                .get("/api/courses/{id}/enrollments", course.getId())
        .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 courseId 호출 시 404 NOT_FOUND를 반환한다")
    void listEnrollments_returns_404_for_missing_course() {
        given().port(port)
                .header("X-User-Id", 10)
        .when()
                .get("/api/courses/{id}/enrollments", 9999)
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 400 MISSING_HEADER를 반환한다")
    void listEnrollments_returns_400_when_header_missing() {
        Course course = saveCourse(10L);

        given().port(port)
        .when()
                .get("/api/courses/{id}/enrollments", course.getId())
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }

    private Course saveCourse(Long creatorId) {
        Course course = Course.createDraft(creatorId, "title", "desc", 10000, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }

    private Enrollment savePending(Long classmateId, Long courseId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        return enrollmentRepository.save(enrollment);
    }
}
