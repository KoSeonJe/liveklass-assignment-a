package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.liveklass.assignment.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class PingE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    @DisplayName("X-User-Id 헤더와 함께 호출하면 200과 userId를 반환한다")
    void ping_with_user_header_returns_200() {
        given()
                .port(port)
                .header("X-User-Id", 42)
        .when()
                .get("/api/ping")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("userId", equalTo(42));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 누락되면 400 MISSING_HEADER를 반환한다")
    void ping_missing_header_returns_400() {
        given()
                .port(port)
        .when()
                .get("/api/ping")
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }
}
