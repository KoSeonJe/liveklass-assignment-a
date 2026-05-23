package com.liveklass.assignment.api;

import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.web.ApiResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.service.EnrollmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentQueryService enrollmentQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentListItemResponse>>> listMine(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(enrollmentQueryService.listForUser(userId, page, size)));
    }
}
