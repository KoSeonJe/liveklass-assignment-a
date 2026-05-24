package com.liveklass.assignment.common.boot;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.repository.CourseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeatCounterRestorer {

    private static final Logger log = LoggerFactory.getLogger(SeatCounterRestorer.class);

    private final CourseRepository courseRepository;
    private final InMemoryCourseSeatCounter seatCounter;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        restore();
    }

    public int restore() {
        List<Course> openCourses = courseRepository.findAllByStatus(CourseStatus.OPEN);
        for (Course course : openCourses) {
            int remaining = course.getMaxCapacity() - course.getCurrentCount();
            seatCounter.add(course.getId(), Math.max(remaining, 0));
        }
        log.info("Restored {} OPEN courses", openCourses.size());
        return openCourses.size();
    }
}
