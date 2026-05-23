package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentQueryService enrollmentQueryService;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("본인의 수강 신청만 반환하고 다른 사용자 신청은 제외한다")
    void listForUser_returns_only_own_enrollments() {
        savePending(1L, 100L);
        savePending(1L, 101L);
        savePending(1L, 102L);
        savePending(2L, 100L);
        savePending(2L, 101L);

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(1L, 0, 20);

        assertThat(page.items()).hasSize(3);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).allMatch(item -> item.courseId() != null);
    }

    @Test
    @DisplayName("id DESC 정렬로 최신 신청이 먼저 반환된다")
    void listForUser_orders_by_id_desc() {
        Enrollment first = savePending(1L, 100L);
        Enrollment second = savePending(1L, 100L);
        Enrollment third = savePending(1L, 100L);

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(1L, 0, 20);

        assertThat(page.items()).hasSize(3);
        assertThat(page.items().get(0).enrollmentId()).isEqualTo(third.getId());
        assertThat(page.items().get(1).enrollmentId()).isEqualTo(second.getId());
        assertThat(page.items().get(2).enrollmentId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("size가 100을 초과하면 100으로 캡된다")
    void listForUser_caps_size_to_100() {
        savePending(1L, 100L);

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(1L, 0, 500);

        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 0 또는 음수는 1로 정규화된다")
    void listForUser_normalizes_non_positive_size_to_1() {
        savePending(1L, 100L);
        savePending(1L, 100L);

        PageResponse<EnrollmentListItemResponse> zero = enrollmentQueryService.listForUser(1L, 0, 0);
        PageResponse<EnrollmentListItemResponse> neg = enrollmentQueryService.listForUser(1L, 0, -5);

        assertThat(zero.size()).isEqualTo(1);
        assertThat(zero.items()).hasSize(1);
        assertThat(neg.size()).isEqualTo(1);
        assertThat(neg.items()).hasSize(1);
    }

    @Test
    @DisplayName("신청 내역이 없는 사용자는 빈 목록과 totalElements=0을 반환한다")
    void listForUser_returns_empty_for_unknown_user() {
        savePending(1L, 100L);

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(999L, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    @DisplayName("totalElements와 totalPages가 정확히 계산된다")
    void listForUser_returns_correct_page_meta() {
        for (int i = 0; i < 5; i++) {
            savePending(1L, 100L);
        }

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(1L, 0, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("CONFIRMED 상태 신청은 confirmedAt을, CANCELLED 상태는 cancelledAt을 응답에 포함한다")
    void listForUser_includes_timestamp_fields_per_status() {
        savePending(1L, 100L);
        saveConfirmed(1L, 101L);
        saveCancelled(1L, 102L);

        PageResponse<EnrollmentListItemResponse> page = enrollmentQueryService.listForUser(1L, 0, 20);

        assertThat(page.items()).hasSize(3);

        EnrollmentListItemResponse cancelled = page.items().stream()
                .filter(item -> item.status() == EnrollmentStatus.CANCELLED)
                .findFirst().orElseThrow();
        EnrollmentListItemResponse confirmed = page.items().stream()
                .filter(item -> item.status() == EnrollmentStatus.CONFIRMED)
                .findFirst().orElseThrow();
        EnrollmentListItemResponse pending = page.items().stream()
                .filter(item -> item.status() == EnrollmentStatus.PENDING)
                .findFirst().orElseThrow();

        assertThat(pending.confirmedAt()).isNull();
        assertThat(pending.cancelledAt()).isNull();
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.cancelledAt()).isNull();
        assertThat(cancelled.confirmedAt()).isNotNull();
        assertThat(cancelled.cancelledAt()).isNotNull();
    }

    private Enrollment savePending(Long classmateId, Long courseId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        return enrollmentRepository.save(enrollment);
    }

    private Enrollment saveConfirmed(Long classmateId, Long courseId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        enrollment.confirm(LocalDateTime.now());
        return enrollmentRepository.save(enrollment);
    }

    private Enrollment saveCancelled(Long classmateId, Long courseId) {
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        LocalDateTime confirmedAt = LocalDateTime.now().minusDays(1);
        enrollment.confirm(confirmedAt);
        enrollment.cancelByClassmate(classmateId, LocalDateTime.now());
        return enrollmentRepository.save(enrollment);
    }
}
