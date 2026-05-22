package com.liveklass.assignment.repository;

import com.liveklass.assignment.domain.course.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findAllByOrderByIdDesc(Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Course c
               SET c.currentCount = c.currentCount + 1,
                   c.updatedAt = CURRENT_TIMESTAMP
             WHERE c.id = :courseId
               AND c.status = com.liveklass.assignment.domain.course.CourseStatus.OPEN
               AND c.currentCount < c.maxCapacity
            """)
    int tryIncreaseCurrentCount(@Param("courseId") Long courseId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Course c
               SET c.currentCount = c.currentCount - 1,
                   c.updatedAt = CURRENT_TIMESTAMP
             WHERE c.id = :courseId
               AND c.currentCount > 0
            """)
    int decreaseCurrentCount(@Param("courseId") Long courseId);
}
