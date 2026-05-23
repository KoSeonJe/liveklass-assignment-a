package com.liveklass.assignment.facade;

import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.domain.enrollment.EnrollmentResult;
import com.liveklass.assignment.domain.waitlist.InMemoryCourseWaitlist;
import com.liveklass.assignment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentFacade {

    private final InMemoryCourseSeatCounter seatCounter;
    private final InMemoryCourseWaitlist waitlist;
    private final EnrollmentService enrollmentService;

    public EnrollmentResult enroll(Long courseId, Long classmateId) {
        if (!seatCounter.tryAcquire(courseId)) {
            int position = waitlist.enqueue(courseId, classmateId);
            return EnrollmentResult.waitlisted(position);
        }
        try {
            Long enrollmentId = enrollmentService.createEnrollment(courseId, classmateId);
            return EnrollmentResult.pending(enrollmentId);
        } catch (RuntimeException e) {
            seatCounter.release(courseId);
            throw e;
        }
    }
}
