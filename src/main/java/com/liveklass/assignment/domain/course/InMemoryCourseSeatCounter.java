package com.liveklass.assignment.domain.course;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCourseSeatCounter {

    private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    public void initialize(Long courseId, int remaining) {
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must be >= 0");
        }
        counters.put(courseId, new AtomicInteger(remaining));
    }

    public boolean tryAcquire(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter == null) {
            throw new CourseNotOpenException(courseId);
        }
        if (counter.decrementAndGet() < 0) {
            counter.incrementAndGet();
            return false;
        }
        return true;
    }

    public void release(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter != null) {
            counter.incrementAndGet();
        }
    }

    public void remove(Long courseId) {
        counters.remove(courseId);
    }

    public int remaining(Long courseId) {
        AtomicInteger c = counters.get(courseId);
        return c == null ? 0 : Math.max(c.get(), 0);
    }
}
