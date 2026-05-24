package com.liveklass.assignment.domain.course;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCourseSeatCounter {

    private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void initialize(Long courseId, int remaining) {
        counters.put(courseId, new AtomicInteger(remaining));
    }

    public void add(Long courseId, int remaining) {
        counters.put(courseId, new AtomicInteger(remaining));
    }

    public boolean contains(Long courseId) {
        return counters.containsKey(courseId);
    }

    public boolean tryAcquire(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter == null) {
            throw new CourseNotOpenException(courseId);
        }
        int prev;
        do {
            prev = counter.get();
            if (prev <= 0) {
                return false;
            }
        } while (!counter.compareAndSet(prev, prev - 1));
        return true;
    }

    public void acquire(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter == null) {
            throw new CourseNotOpenException(courseId);
        }
        int prev;
        do {
            prev = counter.get();
            if (prev <= 0) {
                throw new IllegalArgumentException("정원이 0명 이하입니다.");
            }
        } while (!counter.compareAndSet(prev, prev - 1));
    }

    public void release(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter == null) {
            throw new IllegalArgumentException("해당 courseId로 정원을 조회할 수 없습니다.");
        }
        counter.incrementAndGet();
    }

    public void remove(Long courseId) {
        counters.remove(courseId);
    }

    public void clearAll() {
        counters.clear();
    }

    public int remaining(Long courseId) {
        AtomicInteger c = counters.get(courseId);
        return c == null ? 0 : Math.max(c.get(), 0);
    }
}
