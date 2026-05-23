package com.liveklass.assignment.service;

import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.repository.EnrollmentRepository;
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
public class EnrollmentQueryService {

    private static final int MAX_SIZE = 100;

    private final EnrollmentRepository enrollmentRepository;

    public PageResponse<EnrollmentListItemResponse> listForUser(Long userId, int page, int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int normalizedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(normalizedPage, normalizedSize);

        Page<Enrollment> result = enrollmentRepository.findByClassmateIdOrderByIdDesc(userId, pageable);

        List<EnrollmentListItemResponse> items = result.getContent().stream()
                .map(EnrollmentListItemResponse::from)
                .toList();
        return PageResponse.of(items, normalizedPage, normalizedSize, result.getTotalElements());
    }
}
