package com.liveklass.assignment.facade;

import com.liveklass.assignment.api.dto.CourseEnrollmentItemResponse;
import com.liveklass.assignment.api.dto.EnrollmentListItemResponse;
import com.liveklass.assignment.common.web.PageResponse;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.domain.enrollment.EnrollmentResult;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.waitlist.InMemoryCourseWaitlist;
import com.liveklass.assignment.dto.CancelledEnrollment;
import com.liveklass.assignment.service.EnrollmentQueryService;
import com.liveklass.assignment.service.EnrollmentService;
import com.liveklass.assignment.service.PaymentService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentFacade {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentFacade.class);

    private final InMemoryCourseSeatCounter seatCounter;
    private final InMemoryCourseWaitlist waitlist;
    private final EnrollmentService enrollmentService;
    private final EnrollmentQueryService enrollmentQueryService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PageResponse<EnrollmentListItemResponse> listForUser(Long userId, int page, int size) {
        return enrollmentQueryService.listForUser(userId, page, size);
    }

    public PageResponse<CourseEnrollmentItemResponse> listForCourse(
            Long courseId, Long requesterId, int page, int size) {
        return enrollmentQueryService.listForCourse(courseId, requesterId, page, size);
    }

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

    public Long cancelEnrollment(Long enrollmentId, Long userId) {
        CancelledEnrollment cancelled = enrollmentService.cancel(enrollmentId, userId);
        try {
            cancelPaymentIfSuccess(enrollmentId);
        } catch (RuntimeException e) {
            try {
                enrollmentService.revertCancel(cancelled.enrollmentId());
            } catch (RuntimeException revertEx) {
                log.error("CRITICAL: 결제 취소 실패 후 enrollment 보상도 실패. 수동 처리 필요. enrollmentId={}",
                        cancelled.enrollmentId(), revertEx);
            }
            throw e;
        }
        seatCounter.release(cancelled.courseId());
        promoteFromWaitlist(cancelled.courseId());
        return cancelled.enrollmentId();
    }

    private void promoteFromWaitlist(Long courseId) {
        Optional<Long> rawNextClassmateId = waitlist.pollNext(courseId);
        if (rawNextClassmateId.isEmpty()) {
            return;
        }
        Long nextClassmateId = rawNextClassmateId.get();
        try {
            seatCounter.acquire(courseId);
        } catch (RuntimeException e) {
            log.warn("대기열 승급 실패: 카운터 확보 불가. courseId={}, classmateId={}", courseId, nextClassmateId, e);
            return;
        }
        try {
            enrollmentService.createEnrollment(courseId, nextClassmateId);
        } catch (RuntimeException e) {
            seatCounter.release(courseId);
            log.warn("대기열 승급 실패: Enrollment 생성 실패. courseId={}, classmateId={}", courseId, nextClassmateId, e);
        }
    }

    private void cancelPaymentIfSuccess(Long enrollmentId) {
        paymentService.findSuccessPayment(enrollmentId).ifPresent(payment -> {
            paymentGateway.cancel(payment.getExternalPaymentKey());
            paymentService.cancel(payment.getId());
        });
    }
}
