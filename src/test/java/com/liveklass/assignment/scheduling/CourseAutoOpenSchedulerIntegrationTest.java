package com.liveklass.assignment.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import com.liveklass.assignment.support.MutableClock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseAutoOpenSchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    CourseAutoOpenScheduler scheduler;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Autowired
    MutableClock clock;

    @Test
    @DisplayName("run은 KST 기준 오늘 도래한 DRAFT 강의를 OPEN으로 전환하고 seatCounter를 초기화한다")
    void run_opens_due_drafts_and_initializes_seat_counter() {
        clock.set(LocalDateTime.of(2026, 5, 24, 0, 0).atZone(KST).toInstant());
        Course dueDraft = saveDraft(7L, 30, LocalDate.of(2026, 5, 24));
        Course futureDraft = saveDraft(7L, 30, LocalDate.of(2026, 6, 1));

        scheduler.run();

        assertThat(courseRepository.findById(dueDraft.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.OPEN);
        assertThat(courseRepository.findById(futureDraft.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.DRAFT);
        assertThat(seatCounter.remaining(dueDraft.getId())).isEqualTo(30);
        assertThat(seatCounter.contains(futureDraft.getId())).isFalse();
    }

    @Test
    @DisplayName("run은 대상 강의가 없으면 아무 변경 없이 종료한다")
    void run_noop_when_no_targets() {
        clock.set(LocalDateTime.of(2026, 5, 24, 0, 0).atZone(KST).toInstant());
        Course futureDraft = saveDraft(7L, 30, LocalDate.of(2026, 6, 1));

        scheduler.run();

        assertThat(courseRepository.findById(futureDraft.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseStatus.DRAFT);
        assertThat(seatCounter.contains(futureDraft.getId())).isFalse();
    }

    private Course saveDraft(Long creatorId, int max, LocalDate startDate) {
        Course course = Course.createDraft(creatorId, "t", "d", 0, max,
                startDate, startDate.plusMonths(1));
        return courseRepository.save(course);
    }
}
