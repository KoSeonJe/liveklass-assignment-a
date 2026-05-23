package com.liveklass.assignment.repository;

import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByEnrollmentIdAndStatus(Long enrollmentId, PaymentStatus status);
}
