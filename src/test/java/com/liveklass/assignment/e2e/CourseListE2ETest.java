package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class CourseListE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Test
    @DisplayName("기본 파라미터 호출 시 200과 페이지 메타(page=0, size=20)를 반환한다")
    void list_returns_default_page_meta() {
        save("a", null);
        save("b", null);

        given().port(port)
        .when()
                .get("/api/courses")
        .then()
                .statusCode(200)
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.totalElements", equalTo(2))
                .body("data.items", hasSize(2));
    }

    @Test
    @DisplayName("size=200 요청 시 응답 메타의 size가 100으로 정규화된다")
    void list_caps_size_to_100() {
        save("a", null);

        given().port(port).queryParam("page", 0).queryParam("size", 200)
        .when()
                .get("/api/courses")
        .then()
                .statusCode(200)
                .body("data.size", equalTo(100));
    }

    private Course save(String title, CourseStatus targetStatus) {
        Course course = Course.createDraft(
                7L,
                title,
                "desc",
                1000,
                10,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1)
        );
        if (targetStatus != null && targetStatus != CourseStatus.DRAFT) {
            course.transitionTo(CourseStatus.OPEN);
            if (targetStatus == CourseStatus.CLOSED) {
                course.transitionTo(CourseStatus.CLOSED);
            }
        }
        return courseRepository.save(course);
    }
}
