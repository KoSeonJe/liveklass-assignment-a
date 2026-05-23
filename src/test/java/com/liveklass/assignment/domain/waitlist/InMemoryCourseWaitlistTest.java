package com.liveklass.assignment.domain.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryCourseWaitlistTest {

    @Test
    @DisplayName("enqueue는 1부터 시작하는 순번을 반환하고 pollNext는 FIFO 순서로 꺼낸다")
    void enqueue_returns_1_based_and_poll_is_fifo() {
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();
        assertThat(w.enqueue(1L, 10L)).isEqualTo(1);
        assertThat(w.enqueue(1L, 20L)).isEqualTo(2);
        assertThat(w.enqueue(1L, 30L)).isEqualTo(3);

        assertThat(w.pollNext(1L)).isEqualTo(Optional.of(10L));
        assertThat(w.pollNext(1L)).isEqualTo(Optional.of(20L));
        assertThat(w.pollNext(1L)).isEqualTo(Optional.of(30L));
        assertThat(w.pollNext(1L)).isEmpty();
    }

    @Test
    @DisplayName("pollNext는 등록되지 않은 강의에 대해 빈 Optional을 반환한다")
    void pollNext_returns_empty_for_unknown_course() {
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();
        assertThat(w.pollNext(99L)).isEmpty();
    }

    @Test
    @DisplayName("positionOf는 큐 내 1-based 위치를, 없으면 empty를 반환한다")
    void positionOf_returns_1_based_or_empty() {
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();
        w.enqueue(1L, 10L);
        w.enqueue(1L, 20L);
        assertThat(w.positionOf(1L, 10L)).isEqualTo(OptionalInt.of(1));
        assertThat(w.positionOf(1L, 20L)).isEqualTo(OptionalInt.of(2));
        assertThat(w.positionOf(1L, 99L)).isEqualTo(OptionalInt.empty());
        assertThat(w.positionOf(2L, 10L)).isEqualTo(OptionalInt.empty());
    }

    @Test
    @DisplayName("clear는 해당 강의 큐만 제거하고 다른 강의는 보존한다")
    void clear_removes_only_target_course() {
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();
        w.enqueue(1L, 10L);
        w.enqueue(2L, 20L);
        w.clear(1L);
        assertThat(w.pollNext(1L)).isEmpty();
        assertThat(w.pollNext(2L)).isEqualTo(Optional.of(20L));
    }

    @Test
    @DisplayName("clearAll은 모든 강의의 큐를 제거한다")
    void clearAll_removes_all() {
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();
        w.enqueue(1L, 10L);
        w.enqueue(2L, 20L);
        w.clearAll();
        assertThat(w.pollNext(1L)).isEmpty();
        assertThat(w.pollNext(2L)).isEmpty();
    }

    @Test
    @DisplayName("100스레드 동시 enqueue 시 1..N 순번이 중복 없이 발급된다")
    void concurrent_enqueue_assigns_unique_positions() throws Exception {
        int threads = 100;
        InMemoryCourseWaitlist w = new InMemoryCourseWaitlist();

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Integer> positions = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            long classmateId = i + 1;
            pool.submit(() -> {
                try {
                    start.await();
                    positions.add(w.enqueue(1L, classmateId));
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

        Set<Integer> unique = new HashSet<>(positions);
        assertThat(positions).hasSize(threads);
        assertThat(unique).hasSize(threads);
        assertThat(unique).allMatch(p -> p >= 1 && p <= threads);
    }
}
