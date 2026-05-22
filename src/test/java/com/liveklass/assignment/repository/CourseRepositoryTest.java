package com.liveklass.assignment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.support.AbstractRepositoryTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    CourseRepository courseRepository;

    private Course saveCourse(int capacity, CourseStatus status) {
        Course c = Course.createDraft(1L, "title", "desc", 10000,
                capacity, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        if (status == CourseStatus.OPEN || status == CourseStatus.CLOSED) {
            c.transitionTo(CourseStatus.OPEN);
        }
        if (status == CourseStatus.CLOSED) {
            c.transitionTo(CourseStatus.CLOSED);
        }
        return courseRepository.saveAndFlush(c);
    }

    @Test
    @DisplayName("OPEN 상태이고 정원 여유가 있으면 current_count가 1 증가하고 1행이 영향받는다")
    void tryIncreaseCurrentCount_increments_when_open_and_under_capacity() {
        Course c = saveCourse(2, CourseStatus.OPEN);

        int updated = courseRepository.tryIncreaseCurrentCount(c.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(courseRepository.findById(c.getId()).orElseThrow().getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 가득 찬 경우 tryIncreaseCurrentCount는 0행 영향이고 카운터는 그대로다")
    void tryIncreaseCurrentCount_returns_zero_when_full() {
        Course c = saveCourse(1, CourseStatus.OPEN);
        courseRepository.tryIncreaseCurrentCount(c.getId());

        int updated = courseRepository.tryIncreaseCurrentCount(c.getId());

        assertThat(updated).isZero();
        assertThat(courseRepository.findById(c.getId()).orElseThrow().getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DRAFT 또는 CLOSED 상태이면 tryIncreaseCurrentCount는 0행 영향이다")
    void tryIncreaseCurrentCount_returns_zero_when_not_open() {
        Course draft = saveCourse(5, CourseStatus.DRAFT);
        Course closed = saveCourse(5, CourseStatus.CLOSED);

        assertThat(courseRepository.tryIncreaseCurrentCount(draft.getId())).isZero();
        assertThat(courseRepository.tryIncreaseCurrentCount(closed.getId())).isZero();
    }

    @Test
    @DisplayName("current_count가 양수이면 decreaseCurrentCount는 1 감소시키고 1행이 영향받는다")
    void decreaseCurrentCount_decrements_when_positive() {
        Course c = saveCourse(5, CourseStatus.OPEN);
        courseRepository.tryIncreaseCurrentCount(c.getId());

        int updated = courseRepository.decreaseCurrentCount(c.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(courseRepository.findById(c.getId()).orElseThrow().getCurrentCount()).isZero();
    }

    @Test
    @DisplayName("current_count가 0이면 decreaseCurrentCount는 0행 영향이다")
    void decreaseCurrentCount_returns_zero_when_already_zero() {
        Course c = saveCourse(5, CourseStatus.OPEN);

        int updated = courseRepository.decreaseCurrentCount(c.getId());

        assertThat(updated).isZero();
    }

}
