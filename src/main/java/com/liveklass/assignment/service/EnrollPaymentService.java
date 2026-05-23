package com.liveklass.assignment.service;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseNotFoundException;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentNotFoundException;
import com.liveklass.assignment.domain.payment.DuplicatePaymentException;
import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.dto.PaymentPreparedInfo;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.repository.PaymentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollPaymentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentPreparedInfo confirmAndPreparePayment(Long enrollmentId, Long userId, String idempotencyKey) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

        enrollment.validateEqualsClassmateId(userId);
        enrollment.confirm(LocalDateTime.now());

        Course course = courseRepository.findById(enrollment.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.getCourseId()));
        int amount = course.getPrice();

        Payment payment = Payment.create(enrollmentId, idempotencyKey, amount);
        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicatePaymentException(idempotencyKey);
        }
        return new PaymentPreparedInfo(payment.getId(), amount);
    }
}
