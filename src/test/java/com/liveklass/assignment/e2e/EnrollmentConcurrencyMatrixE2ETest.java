package com.liveklass.assignment.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.liveklass.assignment.domain.course.Course;
import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.domain.course.InMemoryCourseSeatCounter;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.facade.CourseFacade;
import com.liveklass.assignment.repository.CourseRepository;
import com.liveklass.assignment.repository.EnrollmentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import com.liveklass.assignment.support.MutableClock;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;

class EnrollmentConcurrencyMatrixE2ETest extends AbstractIntegrationTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 0, 0);

    @LocalServerPort
    int port;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    @Autowired
    CourseFacade courseFacade;

    @Autowired
    InMemoryCourseSeatCounter seatCounter;

    @Autowired
    MutableClock clock;

    @MockBean
    PaymentGateway paymentGateway;

    @ParameterizedTest(name = "정원={0}, 동시스레드={1} → PENDING={0} / WAITLISTED=threads-capacity")
    @CsvSource({"1,50", "10,100", "50,100"})
    @DisplayName("정원·스레드 매트릭스: invariant(confirmed_count ≤ max_capacity, seatCounter == DB.current_count) 유지")
    void enrollment_matrix_invariants(int capacity, int threads) throws Exception {
        Course course = openCourse(7L, capacity);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger pending = new AtomicInteger();
        AtomicInteger waitlisted = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            int userId = 1000 + i;
            pool.submit(() -> {
                try {
                    start.await();
                    int status = given().port(port).header("X-User-Id", userId)
                            .post("/api/courses/{id}/enrollments", course.getId())
                            .statusCode();
                    if (status == 201) {
                        pending.incrementAndGet();
                    } else if (status == 202) {
                        waitlisted.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(other.get()).isZero();
        assertThat(pending.get()).isEqualTo(capacity);
        assertThat(waitlisted.get()).isEqualTo(threads - capacity);

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(capacity);
        assertThat(reloaded.getCurrentCount()).isLessThanOrEqualTo(reloaded.getMaxCapacity());
        assertThat(seatCounter.remaining(course.getId()))
                .isEqualTo(reloaded.getMaxCapacity() - reloaded.getCurrentCount());

        long pendingInDb = enrollmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING)
                .count();
        assertThat(pendingInDb).isEqualTo(capacity);
    }

    @Test
    @DisplayName("정원1·취소1건과 신규5건 동시 발사 시 정원 초과 0건이며 대기자 1명이 PENDING으로 승급한다")
    void enrollment_cancel_promotes_one_from_waitlist() throws Exception {
        Course course = openCourse(7L, 1);

        Long confirmedEnrollmentId = enrollAndPay(course.getId(), 42L, "key-0", "ext-0");
        markConfirmedAt(confirmedEnrollmentId, CONFIRMED_AT);
        clock.set(CONFIRMED_AT.plusDays(1));

        for (int i = 0; i < 5; i++) {
            given().port(port).header("X-User-Id", 100 + i)
                    .post("/api/courses/{id}/enrollments", course.getId())
                    .then().statusCode(202);
        }

        int newcomers = 5;
        int totalThreads = newcomers + 1;
        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);
        AtomicInteger overflow = new AtomicInteger();

        pool.submit(() -> {
            try {
                start.await();
                given().port(port).header("X-User-Id", 42)
                        .post("/api/enrollments/{id}/cancel", confirmedEnrollmentId)
                        .then().statusCode(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        for (int i = 0; i < newcomers; i++) {
            int userId = 200 + i;
            pool.submit(() -> {
                try {
                    start.await();
                    int status = given().port(port).header("X-User-Id", userId)
                            .post("/api/courses/{id}/enrollments", course.getId())
                            .statusCode();
                    if (status != 201 && status != 202) {
                        overflow.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(overflow.get()).isZero();

        Course reloaded = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(1);
        assertThat(reloaded.getCurrentCount()).isLessThanOrEqualTo(reloaded.getMaxCapacity());
        assertThat(seatCounter.remaining(course.getId()))
                .isEqualTo(reloaded.getMaxCapacity() - reloaded.getCurrentCount());

        List<Enrollment> all = enrollmentRepository.findAll();
        long pendingCount = all.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING)
                .count();
        assertThat(pendingCount).isEqualTo(1);
    }

    private Course openCourse(Long creatorId, int max) {
        Course course = Course.createDraft(creatorId, "t", "d", 10000, max,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        Course saved = courseRepository.save(course);
        courseFacade.changeStatus(saved.getId(), creatorId, CourseStatus.OPEN);
        return saved;
    }

    private Long enrollAndPay(Long courseId, Long userId, String idempotencyKey, String externalKey) {
        given().port(port).header("X-User-Id", userId)
                .post("/api/courses/{id}/enrollments", courseId)
                .then().statusCode(201);
        Long enrollmentId = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassmateId().equals(userId))
                .findFirst().orElseThrow().getId();
        given(paymentGateway.charge(anyLong(), anyInt())).willReturn(externalKey);
        given().port(port)
                .header("X-User-Id", userId)
                .header("Idempotency-Key", idempotencyKey)
                .post("/api/enrollments/{id}/payment", enrollmentId)
                .then().statusCode(200);
        return enrollmentId;
    }

    private void markConfirmedAt(Long enrollmentId, LocalDateTime confirmedAt) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        try {
            Field f = Enrollment.class.getDeclaredField("confirmedAt");
            f.setAccessible(true);
            f.set(enrollment, confirmedAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        enrollmentRepository.saveAndFlush(enrollment);
    }
}
