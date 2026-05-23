package com.liveklass.assignment.service;

import com.liveklass.assignment.domain.course.CourseNotOpenException;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

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
}
