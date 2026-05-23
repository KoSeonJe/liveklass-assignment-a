package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotOpenException;
import com.liveklass.assignment.domain.course.CourseStatus;
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

class EnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentService enrollmentService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Test
    @DisplayName("OPEN 강의에 정원 미달 시 current_count가 1 증가하고 PENDING Enrollment가 저장된다")
    void createEnrollment_open_course_under_capacity() {
        Course saved = saveOpen(7L, 10);

        Long enrollmentId = enrollmentService.createEnrollment(saved.getId(), 42L);

        assertThat(enrollmentId).isNotNull();
        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(enrollment.getCourseId()).isEqualTo(saved.getId());
        assertThat(enrollment.getClassmateId()).isEqualTo(42L);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("DRAFT 강의 신청 시 CourseNotOpenException이 발생한다")
    void createEnrollment_draft_throws() {
        Course saved = saveDraft(7L, 10);

        assertThatThrownBy(() -> enrollmentService.createEnrollment(saved.getId(), 42L))
                .isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("CLOSED 강의 신청 시 CourseNotOpenException이 발생한다")
    void createEnrollment_closed_throws() {
        Course course = Course.createDraft(7L, "t", "d", 0, 10,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        course.transitionTo(CourseStatus.CLOSED);
        Course saved = courseRepository.save(course);

        assertThatThrownBy(() -> enrollmentService.createEnrollment(saved.getId(), 42L))
                .isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("미존재 강의 ID 신청 시 CourseNotOpenException이 발생한다 (404 분리 미구현)")
    void createEnrollment_missing_course_throws() {
        assertThatThrownBy(() -> enrollmentService.createEnrollment(9999L, 42L))
                .isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("정원 마지막 자리 신청은 정상 처리되며 current_count = max_capacity가 된다")
    void createEnrollment_last_seat() {
        Course saved = saveOpenWithCount(7L, 3, 2);

        Long enrollmentId = enrollmentService.createEnrollment(saved.getId(), 42L);

        assertThat(enrollmentId).isNotNull();
        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("DB 기준 만석 강의 신청 시 CourseNotOpenException이 발생한다")
    void createEnrollment_db_full_throws() {
        Course saved = saveOpenWithCount(7L, 2, 2);

        assertThatThrownBy(() -> enrollmentService.createEnrollment(saved.getId(), 42L))
                .isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("rollbackToPending은 CONFIRMED Enrollment를 PENDING으로 되돌린다")
    void rollback_to_pending_reverts_status() {
        Course course = saveOpen(7L, 10);
        Long enrollmentId = enrollmentService.createEnrollment(course.getId(), 42L);
        Enrollment confirmed = enrollmentRepository.findById(enrollmentId).orElseThrow();
        confirmed.confirm(LocalDateTime.now());
        enrollmentRepository.saveAndFlush(confirmed);

        enrollmentService.rollbackToPending(enrollmentId);

        Enrollment reloaded = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(reloaded.getConfirmedAt()).isNull();
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }

    private Course saveOpen(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        return courseRepository.save(course);
    }

    private Course saveOpenWithCount(Long creatorId, int max, int initialCount) {
        Course saved = saveOpen(creatorId, max);
        for (int i = 0; i < initialCount; i++) {
            int updated = courseRepository.tryIncreaseCurrentCount(saved.getId());
            assertThat(updated).isEqualTo(1);
        }
        return courseRepository.findById(saved.getId()).orElseThrow();
    }
}
