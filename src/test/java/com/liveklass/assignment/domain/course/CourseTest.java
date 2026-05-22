package com.liveklass.assignment.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CourseTest {

    private static Course draft(Long creatorId, int capacity) {
        return Course.createDraft(creatorId, "title", "desc", 10000,
                capacity, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
    }

    @Test
    @DisplayName("createDraft는 status=DRAFT, currentCount=0인 강의를 생성한다")
    void create_draft_starts_with_zero_count_and_DRAFT() {
        Course c = draft(1L, 30);
        assertThat(c.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(c.getCurrentCount()).isZero();
        assertThat(c.getMaxCapacity()).isEqualTo(30);
        assertThat(c.remainingCapacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("createDraft는 빈 제목을 거부한다")
    void create_draft_rejects_blank_title() {
        assertThatThrownBy(() -> Course.createDraft(1L, "  ", "d", 0, 10,
                LocalDate.now(), LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createDraft는 0 이하 정원을 거부한다")
    void create_draft_rejects_non_positive_capacity() {
        assertThatThrownBy(() -> Course.createDraft(1L, "t", "d", 0, 0,
                LocalDate.now(), LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createDraft는 음수 가격을 거부한다")
    void create_draft_rejects_negative_price() {
        assertThatThrownBy(() -> Course.createDraft(1L, "t", "d", -1, 10,
                LocalDate.now(), LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createDraft는 종료일이 시작일보다 빠른 경우를 거부한다")
    void create_draft_rejects_end_before_start() {
        assertThatThrownBy(() -> Course.createDraft(1L, "t", "d", 0, 10,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("verifyCreator는 creatorId가 일치하는 요청자를 허용한다")
    void verify_creator_accepts_owner() {
        Course c = draft(7L, 10);
        assertThatCode(() -> c.verifyCreator(7L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verifyCreator는 다른 사용자·null 요청자를 UnauthorizedException으로 거부한다")
    void verify_creator_rejects_other() {
        Course c = draft(7L, 10);
        assertThatThrownBy(() -> c.verifyCreator(8L)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> c.verifyCreator(null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("transitionTo는 DRAFT에서 OPEN으로의 전이를 적용한다")
    void transition_DRAFT_to_OPEN() {
        Course c = draft(1L, 10);
        c.transitionTo(CourseStatus.OPEN);
        assertThat(c.getStatus()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("transitionTo는 OPEN에서 DRAFT로의 전이를 거부한다")
    void transition_OPEN_to_DRAFT_throws() {
        Course c = draft(1L, 10);
        c.transitionTo(CourseStatus.OPEN);
        assertThatThrownBy(() -> c.transitionTo(CourseStatus.DRAFT))
                .isInstanceOf(IllegalCourseStateTransitionException.class);
    }
}
