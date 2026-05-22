package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.liveklass.assignment.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PingE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Test
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
