package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class CourseStatusE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Test
    @DisplayName("크리에이터의 DRAFT → OPEN 전이 요청 시 200과 신규 status·remaining을 반환한다")
    void patch_status_returns_200_for_creator() {
        Course saved = saveDraft(7L, 30);

        given().port(port)
                .header("X-User-Id", 7)
                .contentType(ContentType.JSON)
                .body(Map.of("status", "OPEN"))
        .when()
                .patch("/api/courses/{id}/status", saved.getId())
        .then()
                .statusCode(200)
                .body("data.courseId", equalTo(saved.getId().intValue()))
                .body("data.status", equalTo("OPEN"))
                .body("data.remaining", equalTo(30));
    }

    @Test
    @DisplayName("비-크리에이터의 전이 요청 시 403 FORBIDDEN을 반환한다")
    void patch_status_returns_403_for_non_creator() {
        Course saved = saveDraft(7L, 30);

        given().port(port)
                .header("X-User-Id", 99)
                .contentType(ContentType.JSON)
                .body(Map.of("status", "OPEN"))
        .when()
                .patch("/api/courses/{id}/status", saved.getId())
        .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    @DisplayName("불허 전이(OPEN → DRAFT) 요청 시 409 ILLEGAL_STATE_TRANSITION을 반환한다")
    void patch_status_returns_409_on_illegal_transition() {
        Course course = Course.createDraft(7L, "t", "d", 0, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        Course saved = courseRepository.save(course);

        given().port(port)
                .header("X-User-Id", 7)
                .contentType(ContentType.JSON)
                .body(Map.of("status", "DRAFT"))
        .when()
                .patch("/api/courses/{id}/status", saved.getId())
        .then()
                .statusCode(409)
                .body("code", equalTo("ILLEGAL_STATE_TRANSITION"));
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }
}
