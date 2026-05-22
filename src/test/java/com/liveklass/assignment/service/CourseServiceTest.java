package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.api.dto.CreateCourseRequest;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
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
}
