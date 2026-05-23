package com.liveklass.assignment.domain.waitlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

@Component
public class InMemoryCourseWaitlist {

    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Long>> queues = new ConcurrentHashMap<>();

    public int enqueue(Long courseId, Long classmateId) {
        ConcurrentLinkedQueue<Long> queue = queues.computeIfAbsent(courseId, k -> new ConcurrentLinkedQueue<>());
        queue.offer(classmateId);
        return positionOf(courseId, classmateId).orElse(queue.size());
    }

    public Optional<Long> pollNext(Long courseId) {
        ConcurrentLinkedQueue<Long> queue = queues.get(courseId);
        if (queue == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(queue.poll());
    }

    public void clear(Long courseId) {
        queues.remove(courseId);
    }

    public OptionalInt positionOf(Long courseId, Long classmateId) {
        ConcurrentLinkedQueue<Long> queue = queues.get(courseId);
        if (queue == null) {
            return OptionalInt.empty();
        }
        List<Long> snapshot = new ArrayList<>(queue);
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).equals(classmateId)) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    public void clearAll() {
        queues.clear();
    }
}
