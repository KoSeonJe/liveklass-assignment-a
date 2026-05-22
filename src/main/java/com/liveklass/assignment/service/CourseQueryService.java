package com.liveklass.assignment.service;

import com.liveklass.assignment.api.dto.CourseResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.repository.CourseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private static final int MAX_SIZE = 100;

    private final CourseRepository courseRepository;

    public PageResponse<CourseResponse> list(int page, int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int normalizedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);

        Page<Course> result = courseRepository.findAllByOrderByIdDesc(pageable);

        List<CourseResponse> items = result.getContent().stream()
                .map(CourseResponse::from)
                .toList();
        return PageResponse.of(items, normalizedPage, normalizedSize, result.getTotalElements());
    }

    public CourseResponse detail(Long courseId) {
        return courseRepository.findById(courseId)
                .map(CourseResponse::from)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
    }
}
