package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class CourseDetailE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Test
    @DisplayName("정상 조회 시 200과 currentCount, status, creatorId를 포함한 본문을 반환한다")
    void detail_returns_200_with_full_body() {
        Course course = Course.createDraft(
                7L, "Spring", "desc", 49000, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)
        );
        course.transitionTo(CourseStatus.OPEN);
        Course saved = courseRepository.save(course);

        given().port(port)
        .when()
                .get("/api/courses/{id}", saved.getId())
        .then()
                .statusCode(200)
                .body("data.id", equalTo(saved.getId().intValue()))
                .body("data.creatorId", equalTo(7))
                .body("data.title", equalTo("Spring"))
                .body("data.status", equalTo("OPEN"))
                .body("data.maxCapacity", equalTo(30))
                .body("data.currentCount", equalTo(0))
                .body("data.startDate", notNullValue());
    }

    @Test
    @DisplayName("존재하지 않는 강의 조회 시 404 NOT_FOUND를 반환한다")
    void detail_returns_404_when_not_found() {
        given().port(port)
        .when()
                .get("/api/courses/{id}", 9999)
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }
}
