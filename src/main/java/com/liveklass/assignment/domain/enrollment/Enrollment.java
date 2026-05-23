package com.liveklass.assignment.domain.enrollment;

import com.liveklass.assignment.common.auth.UnauthorizedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "enrollment",
        indexes = {
                @Index(name = "idx_enrollment_course_status", columnList = "course_id, status"),
                @Index(name = "idx_enrollment_classmate", columnList = "classmate_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    private static final int CANCELLATION_WINDOW_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "classmate_id", nullable = false)
    private Long classmateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    private Enrollment(Long courseId, Long classmateId) {
        this.courseId = courseId;
        this.classmateId = classmateId;
        this.status = EnrollmentStatus.PENDING;
    }

    public static Enrollment create(Long courseId, Long classmateId) {
        if (courseId == null) {
            throw new IllegalArgumentException("강의 ID는 필수입니다.");
        }
        if (classmateId == null) {
            throw new IllegalArgumentException("수강생 ID는 필수입니다.");
        }
        return new Enrollment(courseId, classmateId);
    }

    public void confirm(LocalDateTime now) {
        this.status.verifyTransitionTo(EnrollmentStatus.CONFIRMED);
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void rollbackToPending() {
        this.status.verifyTransitionTo(EnrollmentStatus.PENDING);
        this.status = EnrollmentStatus.PENDING;
        this.confirmedAt = null;
    }

    public void cancelByClassmate(Long requesterId, LocalDateTime now) {
        if (!this.classmateId.equals(requesterId)) {
            throw new UnauthorizedException(
                    "요청자가 수강 신청의 본인이 아닙니다. enrollmentId=" + this.id);
        }
        this.status.verifyTransitionTo(EnrollmentStatus.CANCELLED);
        if (now.isAfter(this.confirmedAt.plusDays(CANCELLATION_WINDOW_DAYS))) {
            throw new CancellationPeriodExpiredException(this.id);
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void revertCancel() {
        this.status.verifyTransitionTo(EnrollmentStatus.CONFIRMED);
        this.status = EnrollmentStatus.CONFIRMED;
        this.cancelledAt = null;
    }

    public void validateEqualsClassmateId(Long userId) {
        if (!this.classmateId.equals(userId)) {
            throw new UnauthorizedException("요청자가 수강 신청의 본인이 아닙니다. enrollmentId=" + id+", classmateId=" + userId);
        }
    }
}
