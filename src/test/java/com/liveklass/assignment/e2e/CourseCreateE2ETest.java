package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.liveklass.assignment.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class CourseCreateE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    @DisplayName("정상 요청 시 201과 Location 헤더, DRAFT 상태의 본문을 반환한다")
    void create_returns_201_with_location_and_draft_body() {
        Map<String, Object> body = Map.of(
                "title", "Spring 심화",
                "description", "트랜잭션·동시성",
                "price", 49000,
                "maxCapacity", 30,
                "startDate", "2026-06-01",
                "endDate", "2026-07-01"
        );

        String location = given()
                .port(port)
                .header("X-User-Id", 7)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/api/courses")
        .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .contentType(ContentType.JSON)
                .body("data.id", notNullValue())
                .body("data.creatorId", equalTo(7))
                .body("data.title", equalTo("Spring 심화"))
                .body("data.status", equalTo("DRAFT"))
                .body("data.currentCount", equalTo(0))
                .body("data.maxCapacity", equalTo(30))
                .extract().header("Location");

        assertThat(location).startsWith("/api/courses/");
    }

    @Test
    @DisplayName("필수 필드(title)가 비어 있으면 400 INVALID_REQUEST를 반환한다")
    void create_validation_fails_on_blank_title() {
        Map<String, Object> body = Map.of(
                "title", "",
                "price", 0,
                "maxCapacity", 10,
                "startDate", "2026-06-01",
                "endDate", "2026-07-01"
        );

        given()
                .port(port)
                .header("X-User-Id", 7)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/api/courses")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("maxCapacity가 0 이하이면 400 INVALID_REQUEST를 반환한다")
    void create_validation_fails_on_non_positive_capacity() {
        Map<String, Object> body = Map.of(
                "title", "t",
                "price", 0,
                "maxCapacity", 0,
                "startDate", "2026-06-01",
                "endDate", "2026-07-01"
        );

        given()
                .port(port)
                .header("X-User-Id", 7)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/api/courses")
        .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 누락되면 400 MISSING_HEADER를 반환한다")
    void create_returns_400_when_header_missing() {
        Map<String, Object> body = Map.of(
                "title", "Spring 심화",
                "price", 49000,
                "maxCapacity", 30,
                "startDate", "2026-06-01",
                "endDate", "2026-07-01"
        );

        given()
                .port(port)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/api/courses")
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }
}
