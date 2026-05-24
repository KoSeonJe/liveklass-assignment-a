package com.liveklass.assignment.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SeatCounterRestorerTest extends AbstractIntegrationTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Autowired
    SeatCounterRestorer restorer;

    @Test
    @DisplayName("부팅 시 OPEN 강의의 current_count로 seatCounter가 초기화된다")
    void restore_initializes_seatCounter_from_open_courses() {
        Course openA = openCourseWithCount(7L, 10, 3);
        Course openB = openCourseWithCount(7L, 20, 7);
        Course draft = saveDraft(7L, 10);

        seatCounter.clearAll();

        int restored = restorer.restore();

        assertThat(restored).isEqualTo(2);
        assertThat(seatCounter.remaining(openA.getId())).isEqualTo(7);
        assertThat(seatCounter.remaining(openB.getId())).isEqualTo(13);
        assertThat(seatCounter.contains(draft.getId())).isFalse();
    }

    @Test
    @DisplayName("OPEN 강의가 없으면 복원 후 카운터는 비어있다")
    void restore_no_open_courses_leaves_counter_empty() {
        Course draft = saveDraft(7L, 10);
        seatCounter.clearAll();

        int restored = restorer.restore();

        assertThat(restored).isZero();
        assertThat(seatCounter.contains(draft.getId())).isFalse();
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }

    private Course openCourseWithCount(Long creatorId, int max, int initialCount) {
        Course saved = saveDraft(creatorId, max);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        for (int i = 0; i < initialCount; i++) {
            int updated = courseRepository.tryIncreaseCurrentCount(saved.getId());
            assertThat(updated).isEqualTo(1);
        }
        return courseRepository.findById(saved.getId()).orElseThrow();
    }
}
