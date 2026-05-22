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
            throw new IllegalArgumentException("creatorId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be > 0");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        return new Course(creatorId, title, description, price, maxCapacity, startDate, endDate);
    }

    public void verifyCreator(Long requesterId) {
        if (requesterId == null || !this.creatorId.equals(requesterId)) {
            throw new UnauthorizedException("Requester is not the creator of course " + this.id);
        }
    }

    public void transitionTo(CourseStatus next) {
        this.status.verifyTransitionTo(next);
        this.status = next;
    }

    public int remainingCapacity() {
        return this.maxCapacity - this.currentCount;
    }
}
