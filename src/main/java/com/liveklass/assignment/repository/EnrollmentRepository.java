package com.liveklass.assignment.repository;

import com.liveklass.assignment.domain.enrollment.Enrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Page<Enrollment> findByClassmateIdOrderByIdDesc(Long classmateId, Pageable pageable);

    Page<Enrollment> findByCourseIdOrderByIdDesc(Long courseId, Pageable pageable);
}
