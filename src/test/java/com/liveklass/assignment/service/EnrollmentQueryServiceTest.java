package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.api.dto.CourseEnrollmentItemResponse;
import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentQueryService enrollmentQueryService;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    CourseRepository courseRepository;

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

    @Test
    @DisplayName("크리에이터가 본인 강의의 신청 목록을 조회하면 모든 상태의 신청을 반환한다")
    void listForCourse_returns_all_statuses_for_creator() {
        Course course = saveCourse(10L);
        savePending(200L, course.getId());
        saveConfirmed(201L, course.getId());
        saveCancelled(202L, course.getId());

        PageResponse<CourseEnrollmentItemResponse> page =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, 20);

        assertThat(page.items()).hasSize(3);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.items()).extracting(CourseEnrollmentItemResponse::classmateId)
                .containsExactlyInAnyOrder(200L, 201L, 202L);
        assertThat(page.items()).extracting(CourseEnrollmentItemResponse::status)
                .containsExactlyInAnyOrder(
                        EnrollmentStatus.PENDING,
                        EnrollmentStatus.CONFIRMED,
                        EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("다른 강의의 신청은 결과에 포함되지 않는다")
    void listForCourse_excludes_other_courses() {
        Course courseA = saveCourse(10L);
        Course courseB = saveCourse(10L);
        savePending(200L, courseA.getId());
        savePending(201L, courseA.getId());
        savePending(300L, courseB.getId());

        PageResponse<CourseEnrollmentItemResponse> page =
                enrollmentQueryService.listForCourse(courseA.getId(), 10L, 0, 20);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(CourseEnrollmentItemResponse::classmateId)
                .containsExactlyInAnyOrder(200L, 201L);
    }

    @Test
    @DisplayName("id DESC 정렬로 최신 신청이 먼저 반환된다")
    void listForCourse_orders_by_id_desc() {
        Course course = saveCourse(10L);
        Enrollment first = savePending(200L, course.getId());
        Enrollment second = savePending(201L, course.getId());
        Enrollment third = savePending(202L, course.getId());

        PageResponse<CourseEnrollmentItemResponse> page =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, 20);

        assertThat(page.items()).extracting(CourseEnrollmentItemResponse::enrollmentId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    @DisplayName("requesterId가 creatorId와 다르면 UnauthorizedException을 던진다")
    void listForCourse_throws_when_requester_is_not_creator() {
        Course course = saveCourse(10L);

        assertThatThrownBy(() ->
                enrollmentQueryService.listForCourse(course.getId(), 99L, 0, 20))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 courseId면 CourseNotFoundException을 던진다")
    void listForCourse_throws_when_course_not_found() {
        assertThatThrownBy(() ->
                enrollmentQueryService.listForCourse(9999L, 10L, 0, 20))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    @DisplayName("size가 100을 초과하면 100으로 캡된다")
    void listForCourse_caps_size_to_100() {
        Course course = saveCourse(10L);
        savePending(200L, course.getId());

        PageResponse<CourseEnrollmentItemResponse> page =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, 500);

        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 0 또는 음수는 1로 정규화된다")
    void listForCourse_normalizes_non_positive_size_to_1() {
        Course course = saveCourse(10L);
        savePending(200L, course.getId());
        savePending(201L, course.getId());

        PageResponse<CourseEnrollmentItemResponse> zero =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, 0);
        PageResponse<CourseEnrollmentItemResponse> neg =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, -5);

        assertThat(zero.size()).isEqualTo(1);
        assertThat(zero.items()).hasSize(1);
        assertThat(neg.size()).isEqualTo(1);
        assertThat(neg.items()).hasSize(1);
    }

    @Test
    @DisplayName("신청이 0건인 강의는 빈 items와 totalElements=0을 반환한다")
    void listForCourse_returns_empty_when_no_enrollments() {
        Course course = saveCourse(10L);

        PageResponse<CourseEnrollmentItemResponse> page =
                enrollmentQueryService.listForCourse(course.getId(), 10L, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
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
