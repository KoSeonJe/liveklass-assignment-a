package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class MeEnrollmentsE2ETest extends AbstractIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("기본 파라미터 호출 시 200과 본인 신청 목록·페이지 메타를 반환한다")
    void listMine_returns_default_page_meta_for_owner() {
        savePending(10L, 100L);
        savePending(10L, 101L);
        savePending(11L, 100L);

        given().port(port)
                .header("X-User-Id", 10)
        .when()
                .get("/api/me/enrollments")
        .then()
                .statusCode(200)
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.totalElements", equalTo(2))
                .body("data.items", hasSize(2));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 400을 반환한다")
    void listMine_returns_400_when_header_missing() {
        given().port(port)
        .when()
                .get("/api/me/enrollments")
        .then()
                .statusCode(400)
                .body("code", equalTo("MISSING_HEADER"));
    }

    @Test
    @DisplayName("신청 내역이 없을 때 빈 items와 totalElements=0을 반환한다")
    void listMine_returns_empty_when_no_enrollments() {
        given().port(port)
                .header("X-User-Id", 999)
        .when()
                .get("/api/me/enrollments")
        .then()
                .statusCode(200)
                .body("data.items", hasSize(0))
                .body("data.totalElements", equalTo(0))
                .body("data.totalPages", equalTo(0));
    }

    @Test
    @DisplayName("size=200 요청 시 응답 메타의 size가 100으로 정규화된다")
    void listMine_caps_size_to_100() {
        savePending(10L, 100L);

        given().port(port)
                .header("X-User-Id", 10)
                .queryParam("page", 0)
                .queryParam("size", 200)
        .when()
                .get("/api/me/enrollments")
        .then()
                .statusCode(200)
                .body("data.size", equalTo(100));
    }

    private Enrollment savePending(Long classmateId, Long courseId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        return enrollmentRepository.save(enrollment);
    }
}
