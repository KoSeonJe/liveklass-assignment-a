package com.liveklass.assignment.service;

import com.liveklass.assignment.domain.course.CourseNotOpenException;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentNotFoundException;
import com.liveklass.assignment.dto.CancelledEnrollment;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final Clock clock;

    @Transactional
    public Long createEnrollment(Long courseId, Long classmateId) {
        int updated = courseRepository.tryIncreaseCurrentCount(courseId);
        if (updated == 0) {
            throw new CourseNotOpenException(courseId);
        }
        Enrollment enrollment = Enrollment.create(courseId, classmateId);
        enrollmentRepository.save(enrollment);
        return enrollment.getId();
    }

    @Transactional
    public void rollbackToPending(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        enrollment.rollbackToPending();
    }

    @Transactional
    public CancelledEnrollment cancel(Long enrollmentId, Long userId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        enrollment.cancelByClassmate(userId, LocalDateTime.now(clock));
        courseRepository.decreaseCurrentCount(enrollment.getCourseId());
        return new CancelledEnrollment(enrollment.getId(), enrollment.getCourseId());
    }

    @Transactional
    public void revertCancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        enrollment.revertCancel();
        int updated = courseRepository.tryIncreaseCurrentCount(enrollment.getCourseId());
        if (updated == 0) {
            throw new CourseNotOpenException(enrollment.getCourseId());
        }
    }
}
