package com.liveklass.assignment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotOpenException;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.domain.enrollment.EnrollmentResult;
import com.liveklass.assignment.domain.waitlist.InMemoryCourseWaitlist;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentFacadeTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentFacade enrollmentFacade;

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Autowired
    InMemoryCourseWaitlist waitlist;

    @Test
    @DisplayName("자리 확보 시 PENDING 결과 반환 + seatCounter 1 차감 + DB current_count +1")
    void enroll_acquires_seat_and_returns_pending() {
        Course saved = openCourse(7L, 10);

        EnrollmentResult result = enrollmentFacade.enroll(saved.getId(), 42L);

        assertThat(result).isInstanceOf(EnrollmentResult.Pending.class);
        assertThat(seatCounter.remaining(saved.getId())).isEqualTo(9);
        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("만석 시 대기열 등록 + position 누적 + DB 변화 없음")
    void enroll_full_routes_to_waitlist() {
        Course saved = openCourse(7L, 1);
        enrollmentFacade.enroll(saved.getId(), 100L);

        EnrollmentResult r1 = enrollmentFacade.enroll(saved.getId(), 101L);
        EnrollmentResult r2 = enrollmentFacade.enroll(saved.getId(), 102L);
        EnrollmentResult r3 = enrollmentFacade.enroll(saved.getId(), 103L);

        assertThat(r1).isInstanceOf(EnrollmentResult.Waitlisted.class);
        assertThat(((EnrollmentResult.Waitlisted) r1).position()).isEqualTo(1);
        assertThat(((EnrollmentResult.Waitlisted) r2).position()).isEqualTo(2);
        assertThat(((EnrollmentResult.Waitlisted) r3).position()).isEqualTo(3);

        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("카운터 미존재 강의 신청 시 CourseNotOpenException이 전파된다")
    void enroll_no_counter_throws() {
        Course draft = saveDraft(7L, 10);

        assertThatThrownBy(() -> enrollmentFacade.enroll(draft.getId(), 42L))
                .isInstanceOf(CourseNotOpenException.class);
        assertThat(waitlist.positionOf(draft.getId(), 42L)).isEmpty();
    }

    @Test
    @DisplayName("Service 실패(DB 상 강의가 CLOSED) 시 seatCounter.release로 카운터가 복구된다")
    void enroll_releases_counter_on_service_failure() {
        Course saved = openCourse(7L, 10);
        // DB만 CLOSED로 강제 (인메모리 카운터는 그대로) → race 상황 모사
        Course course = courseRepository.findById(saved.getId()).orElseThrow();
        course.transitionTo(CourseStatus.CLOSED);
        courseRepository.save(course);

        assertThatThrownBy(() -> enrollmentFacade.enroll(saved.getId(), 42L))
                .isInstanceOf(CourseNotOpenException.class);

        assertThat(seatCounter.remaining(saved.getId())).isEqualTo(10);
        Course reloaded = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isZero();
    }

    private Course saveDraft(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        return courseRepository.save(course);
    }

    private Course openCourse(Long creatorId, int max) {
        Course saved = saveDraft(creatorId, max);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        return saved;
    }
}
