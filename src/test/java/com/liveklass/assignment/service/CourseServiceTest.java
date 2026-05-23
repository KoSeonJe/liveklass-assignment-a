package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.IllegalCourseStateTransitionException;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseServiceTest extends AbstractIntegrationTest {

    @Autowired
    CourseService courseService;

    @Autowired
    CourseRepository courseRepository;

    @Test
    @DisplayName("create는 DRAFT 상태의 강의를 영속화하고 creator_id를 X-User-Id로 기록한다")
    void create_persists_draft_course_with_creator_id() {
        CreateCourseRequest request = new CreateCourseRequest(
                "Spring 심화",
                "트랜잭션·동시성",
                49000,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1)
        );

        Course created = courseService.create(7L, request);

        assertThat(created.getId()).isNotNull();
        Course found = courseRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getCreatorId()).isEqualTo(7L);
        assertThat(found.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(found.getCurrentCount()).isZero();
        assertThat(found.getTitle()).isEqualTo("Spring 심화");
        assertThat(found.getMaxCapacity()).isEqualTo(30);
        assertThat(found.getPrice()).isEqualTo(49000);
    }

    @Test
    @DisplayName("changeStatus는 DRAFT → OPEN 전이 시 remaining=maxCapacity를 반환한다")
    void changeStatus_draft_to_open() {
        Course saved = saveDraft(7L, 30);

        CourseStatusChangeResult result = courseService.changeStatus(saved.getId(), 7L, CourseStatus.OPEN);

        assertThat(result.courseId()).isEqualTo(saved.getId());
        assertThat(result.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(result.remaining()).isEqualTo(30);
        assertThat(courseRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("changeStatus는 OPEN → CLOSED 전이를 처리한다")
    void changeStatus_open_to_closed() {
        Course saved = saveOpen(7L, 30);

        CourseStatusChangeResult result = courseService.changeStatus(saved.getId(), 7L, CourseStatus.CLOSED);

        assertThat(result.status()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("changeStatus는 CLOSED → OPEN 재전이를 처리한다")
    void changeStatus_closed_to_open() {
        Course course = Course.createDraft(7L, "t", "d", 0, 30,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        course.transitionTo(CourseStatus.OPEN);
        course.transitionTo(CourseStatus.CLOSED);
        Course saved = courseRepository.save(course);

        CourseStatusChangeResult result = courseService.changeStatus(saved.getId(), 7L, CourseStatus.OPEN);

        assertThat(result.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(result.remaining()).isEqualTo(30);
    }

    @Test
    @DisplayName("changeStatus는 DRAFT → CLOSED 전이를 처리한다")
    void changeStatus_draft_to_closed() {
        Course saved = saveDraft(7L, 30);

        CourseStatusChangeResult result = courseService.changeStatus(saved.getId(), 7L, CourseStatus.CLOSED);

        assertThat(result.status()).isEqualTo(CourseStatus.CLOSED);
    }

    @Test
    @DisplayName("changeStatus는 불허 전이(OPEN → DRAFT) 시 IllegalCourseStateTransitionException을 발생시킨다")
    void changeStatus_throws_on_illegal_transition() {
        Course saved = saveOpen(7L, 30);

        assertThatThrownBy(() -> courseService.changeStatus(saved.getId(), 7L, CourseStatus.DRAFT))
                .isInstanceOf(IllegalCourseStateTransitionException.class);
    }

    @Test
    @DisplayName("changeStatus는 비-크리에이터 요청 시 UnauthorizedException을 발생시킨다")
    void changeStatus_throws_when_requester_is_not_creator() {
        Course saved = saveDraft(7L, 30);

        assertThatThrownBy(() -> courseService.changeStatus(saved.getId(), 99L, CourseStatus.OPEN))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("changeStatus는 미존재 강의 요청 시 CourseNotFoundException을 발생시킨다")
    void changeStatus_throws_when_course_missing() {
        assertThatThrownBy(() -> courseService.changeStatus(9999L, 7L, CourseStatus.OPEN))
                .isInstanceOf(CourseNotFoundException.class);
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
}
