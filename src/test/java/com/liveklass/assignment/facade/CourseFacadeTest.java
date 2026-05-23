package com.liveklass.assignment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseFacadeTest extends AbstractIntegrationTest {

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Test
    @DisplayName("OPEN 진입 시 seatCounter가 maxCapacity로 초기화된다")
    void changeStatus_initializes_counter_on_open() {
        Course saved = saveDraft(7L, 30);

        courseFacade.changeStatus(saved.getId(), 7L, CourseStatus.OPEN);

        assertThat(seatCounter.remaining(saved.getId())).isEqualTo(30);
    }

    @Test
    @DisplayName("CLOSED 진입 시 seatCounter가 제거된다")
    void changeStatus_removes_counter_on_closed() {
        Course saved = saveDraft(7L, 30);
        courseFacade.changeStatus(saved.getId(), 7L, CourseStatus.OPEN);
        assertThat(seatCounter.remaining(saved.getId())).isEqualTo(30);

        courseFacade.changeStatus(saved.getId(), 7L, CourseStatus.CLOSED);

        assertThat(seatCounter.remaining(saved.getId())).isZero();
    }

    @Test
    @DisplayName("DRAFT → CLOSED 직접 전이 시 seatCounter는 초기화되지 않는다")
    void changeStatus_draft_to_closed_does_not_initialize_counter() {
        Course saved = saveDraft(7L, 30);

        courseFacade.changeStatus(saved.getId(), 7L, CourseStatus.CLOSED);

        assertThat(seatCounter.remaining(saved.getId())).isZero();
    }

    @Test
    @DisplayName("Service 예외(비-크리에이터) 발생 시 seatCounter는 변경되지 않는다")
    void changeStatus_does_not_touch_counter_when_service_throws() {
        Course saved = saveDraft(7L, 30);

        assertThatThrownBy(() -> courseFacade.changeStatus(saved.getId(), 99L, CourseStatus.OPEN))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(seatCounter.remaining(saved.getId())).isZero();
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }
}
