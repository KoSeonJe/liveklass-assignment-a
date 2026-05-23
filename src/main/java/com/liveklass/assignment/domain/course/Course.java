package com.liveklass.assignment.domain.course;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "course",
        indexes = {
                @Index(name = "idx_course_status", columnList = "status"),
                @Index(name = "idx_course_creator", columnList = "creator_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private Course(Long creatorId, String title, String description, int price,
                   int maxCapacity, LocalDate startDate, LocalDate endDate) {
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.currentCount = 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CourseStatus.DRAFT;
    }

    public static Course createDraft(Long creatorId, String title, String description, int price,
                                     int maxCapacity, LocalDate startDate, LocalDate endDate) {
        if (creatorId == null) {
            throw new IllegalArgumentException("크리에이터 ID는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("강의 제목은 비어 있을 수 없습니다.");
        }
        if (price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("정원은 1 이상이어야 합니다.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("강의 시작일과 종료일은 필수입니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("강의 종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new Course(creatorId, title, description, price, maxCapacity, startDate, endDate);
    }

    public void verifyCreator(Long requesterId) {
        if (!this.creatorId.equals(requesterId)) {
            throw new UnauthorizedException("요청자가 강의의 크리에이터가 아닙니다. courseId=" + this.id);
        }
    }

    public void transitionTo(CourseStatus next) {
        this.status.verifyTransitionTo(next);
        this.status = next;
    }

    public int remainingCapacity() {
        int remaining = this.maxCapacity - this.currentCount;
        if (remaining < 0) {
            throw new IllegalStateException(
                    "강의 잔여 정원이 음수입니다. courseId=" + this.id + ", remaining=" + remaining);
        }
        return remaining;
    }

    public void validateRemainingCapacity() {
        int remaining = this.maxCapacity - this.currentCount;
        if (remaining <= 0) {
            throw new IllegalStateException("강의 잔여 정원은 1명 이상이어야 합니다.");
        }
    }
}
