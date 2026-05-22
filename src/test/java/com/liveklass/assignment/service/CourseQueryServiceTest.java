package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.api.dto.CourseResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    CourseQueryService courseQueryService;

    @Autowired
    CourseRepository courseRepository;

    @Test
    @DisplayName("DRAFT/OPEN/CLOSED 모든 상태의 강의가 조회된다")
    void list_returns_all_statuses() {
        save("a", null);
        save("b", CourseStatus.OPEN);
        save("c", CourseStatus.CLOSED);

        PageResponse<CourseResponse> page = courseQueryService.list(0, 20);

        assertThat(page.items()).hasSize(3);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("size가 100을 초과하면 100으로 캡된다")
    void list_caps_size_to_100() {
        save("a", null);

        PageResponse<CourseResponse> page = courseQueryService.list(0, 500);

        assertThat(page.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 0 또는 음수는 1로 정규화된다")
    void list_normalizes_non_positive_size_to_1() {
        save("a", null);
        save("b", null);

        PageResponse<CourseResponse> zero = courseQueryService.list(0, 0);
        PageResponse<CourseResponse> neg = courseQueryService.list(0, -10);

        assertThat(zero.size()).isEqualTo(1);
        assertThat(zero.items()).hasSize(1);
        assertThat(neg.size()).isEqualTo(1);
        assertThat(neg.items()).hasSize(1);
    }

    @Test
    @DisplayName("totalElements와 totalPages가 정확히 계산된다")
    void list_returns_correct_page_meta() {
        for (int i = 0; i < 5; i++) {
            save("c-" + i, null);
        }

        PageResponse<CourseResponse> page = courseQueryService.list(0, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("상세 조회 시 currentCount를 포함해 반환한다")
    void detail_returns_currentCount() {
        Course saved = save("d-1", CourseStatus.OPEN);
        courseRepository.tryIncreaseCurrentCount(saved.getId());

        CourseResponse response = courseQueryService.detail(saved.getId());

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.currentCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
    }

    @Test
    @DisplayName("존재하지 않는 강의 조회 시 CourseNotFoundException이 발생한다")
    void detail_throws_when_not_found() {
        assertThatThrownBy(() -> courseQueryService.detail(9999L))
                .isInstanceOf(CourseNotFoundException.class);
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
