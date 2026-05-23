package com.liveklass.assignment.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = "idempotency_key")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "external_payment_key", length = 64)
    private String externalPaymentKey;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Payment(Long enrollmentId, String idempotencyKey, int amount) {
        this.enrollmentId = enrollmentId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long enrollmentId, String idempotencyKey, int amount) {
        if (enrollmentId == null) {
            throw new IllegalArgumentException("수강 신청 ID는 필수입니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등키는 비어 있을 수 없습니다.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("결제 금액은 0 이상이어야 합니다.");
        }
        return new Payment(enrollmentId, idempotencyKey, amount);
    }

    public void markSuccess(String externalPaymentKey) {
        if (externalPaymentKey == null || externalPaymentKey.isBlank()) {
            throw new IllegalArgumentException("외부 결제 키는 비어 있을 수 없습니다.");
        }
        this.status.verifyTransitionTo(PaymentStatus.SUCCESS);
        this.status = PaymentStatus.SUCCESS;
        this.externalPaymentKey = externalPaymentKey;
    }

    public void markFailed() {
        this.status.verifyTransitionTo(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }

    public void cancel() {
        this.status.verifyTransitionTo(PaymentStatus.CANCELLED);
        this.status = PaymentStatus.CANCELLED;
    }
}
