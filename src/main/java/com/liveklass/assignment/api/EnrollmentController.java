package com.liveklass.assignment.api;

import com.liveklass.assignment.api.dto.EnrollmentCancelResponse;
import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.web.ApiResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.facade.EnrollmentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentFacade enrollmentFacade;

    @GetMapping("/api/me/enrollments")
    public ResponseEntity<ApiResponse<PageResponse<EnrollmentListItemResponse>>> listMine(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(enrollmentFacade.listForUser(userId, page, size)));
    }

    @PostMapping("/api/enrollments/{enrollmentId}/cancel")
    public ResponseEntity<ApiResponse<EnrollmentCancelResponse>> cancel(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        Long cancelledId = enrollmentFacade.cancelEnrollment(enrollmentId, userId);
        return ResponseEntity.ok(ApiResponse.of(EnrollmentCancelResponse.cancelled(cancelledId)));
    }
}
