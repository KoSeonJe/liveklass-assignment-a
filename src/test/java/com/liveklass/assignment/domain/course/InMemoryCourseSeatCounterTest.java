package com.liveklass.assignment.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCourseSeatCounterTest {

    @Test
    @DisplayName("등록되지 않은 강의에 tryAcquire 시 CourseNotOpenException을 던진다")
    void unregistered_course_throws_on_tryAcquire() {
        InMemoryCourseSeatCounter c = new InMemoryCourseSeatCounter();
        assertThatThrownBy(() -> c.tryAcquire(1L)).isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("tryAcquire는 남은 자리를 소진할 때까지 true, 이후엔 false를 반환한다")
    void tryAcquire_drains_then_returns_false() {
        InMemoryCourseSeatCounter c = new InMemoryCourseSeatCounter();
        c.initialize(1L, 2);
        assertThat(c.tryAcquire(1L)).isTrue();
        assertThat(c.tryAcquire(1L)).isTrue();
        assertThat(c.tryAcquire(1L)).isFalse();
        assertThat(c.remaining(1L)).isZero();
    }

    @Test
    @DisplayName("release 호출 시 자리를 반환해 다시 tryAcquire가 가능하다")
    void release_returns_seat() {
        InMemoryCourseSeatCounter c = new InMemoryCourseSeatCounter();
        c.initialize(1L, 1);
        assertThat(c.tryAcquire(1L)).isTrue();
        c.release(1L);
        assertThat(c.tryAcquire(1L)).isTrue();
    }

    @Test
    @DisplayName("remove 호출 시 카운터가 제거되어 이후 tryAcquire가 CourseNotOpenException을 던진다")
    void remove_clears_counter() {
        InMemoryCourseSeatCounter c = new InMemoryCourseSeatCounter();
        c.initialize(1L, 1);
        c.remove(1L);
        assertThatThrownBy(() -> c.tryAcquire(1L)).isInstanceOf(CourseNotOpenException.class);
    }

    @Test
    @DisplayName("100스레드 동시 tryAcquire 시 정확히 capacity만큼만 성공한다")
    void concurrent_tryAcquire_never_exceeds_capacity() throws Exception {
        int capacity = 10;
        int threads = 100;
        InMemoryCourseSeatCounter counter = new InMemoryCourseSeatCounter();
        counter.initialize(1L, capacity);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (counter.tryAcquire(1L)) {
                        acquired.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(acquired.get()).isEqualTo(capacity);
        assertThat(rejected.get()).isEqualTo(threads - capacity);
        assertThat(counter.remaining(1L)).isZero();
    }
}
