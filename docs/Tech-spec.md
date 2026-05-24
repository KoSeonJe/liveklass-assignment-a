# Tech-spec: 수강 신청 시스템

| 항목 | 내용 |
|---|---|
| 문서 버전 | 1.0 |
| 작성일 | 2026-05-21 |
| 작성자 | KoSeonJe |
| 관련 문서 | PRD.md |

---

## 1. 개요

### 1.1 문서 목적

본 문서는 수강 신청 시스템의 기술적 구현 방안을 정의한다. PRD가 "무엇을, 왜"를 다룬다면, 본 문서는 "어떻게"에 집중한다. 특히 과제의 핵심 평가 관점인 **동시성 제어, 상태 전이, 트랜잭션 경계** 세 가지에 대해, 채택안의 근거와 대안과의 비교를 §4·§6·§7과 Appendix A에서 상세히 다룬다.

### 1.2 기술 스택

| 영역 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| ORM | Spring Data JPA |
| Database | MySQL 8 (로컬: docker compose, 테스트: Testcontainers) |
| Build | Gradle (Kotlin DSL) |
| Test | JUnit 5, Spring Boot Test, Testcontainers (`spring-boot-testcontainers`, `testcontainers:mysql`) |
| 기타 | Lombok, Bean Validation |

### 1.3 환경 전제

- **단일 애플리케이션 인스턴스, 단일 DB**: 인메모리 자료구조 기반 동시성 제어가 유효함을 전제로 한다. 다중 인스턴스로 확장하는 경우의 한계는 §11에서 별도로 기술한다.

---

## 2. 아키텍처

### 2.1 레이어 구조

```
┌──────────────────────────────────────────────┐
│  Controller (REST API)                       │
│  - 요청/응답 직렬화, 인증 헤더 추출           │
└──────────────┬───────────────────────────────┘
               │
┌──────────────▼───────────────────────────────┐
│  Facade                                       │
│  - 트랜잭션 외부의 동시성 제어                  │
│  - 인메모리 카운터, 대기열 관리                │
│  - 보상 로직                                  │
└──────────────┬───────────────────────────────┘
               │
┌──────────────▼───────────────────────────────┐
│  Service (@Transactional)                     │
│  - 도메인 로직, DB 조작                       │
│  - 원자적 UPDATE                              │
└──────────────┬───────────────────────────────┘
               │
┌──────────────▼───────────────────────────────┐
│  Repository                                   │
└───────────────────────────────────────────────┘
```

### 2.2 Facade 패턴 채택 이유

수강 신청 흐름에서 트랜잭션 경계와 동시성 제어가 일치하지 않는다는 점이 핵심이다.

- **트랜잭션 내부**에서만 동시성을 제어하면 DB 락에 의존하게 되어 커넥션 점유 시간이 길어진다.
- **트랜잭션 외부**에서 동시성을 제어하면 DB 커넥션을 짧게 점유할 수 있지만, 트랜잭션 실패 시 외부 상태(인메모리 카운터)를 보상해야 한다.

Facade 계층을 두면 트랜잭션 경계를 명확히 하면서 보상 로직을 한 곳에 모을 수 있다.

```java
// Facade: 트랜잭션 외부
public class EnrollmentFacade {
    public void enroll(Long courseId, Long classmateId) {
        // 1) 인메모리 카운터 차감 (트랜잭션 외부)
        // 2) Service 호출 → 내부 트랜잭션
        // 3) 실패 시 보상
    }
}

// Service: 트랜잭션 내부
@Transactional
public class EnrollmentService {
    public void createEnrollment(...) {
        // DB 원자적 UPDATE + INSERT만 수행
    }
}
```

### 2.3 패키지 구조 (예시)

```
com.liveklass.assignment
├── api               # Controller
├── facade            # EnrollmentFacade
├── domain
│   ├── course        # Course 엔티티, InMemoryCourseSeatCounter
│   ├── enrollment    # Enrollment 엔티티 및 도메인 로직
│   ├── payment       # Payment 엔티티 및 도메인 로직
│   └── waitlist      # InMemoryCourseWaitlist
├── service           # 트랜잭션 단위 서비스
├── repository
└── common            # 예외, 공통 응답, 헤더 파싱
```

**일급 컬렉션 (in-memory)**:

| 클래스 | 위치 | 책임 |
|---|---|---|
| `InMemoryCourseSeatCounter` | `domain/course` | 강의별 남은 자리 수 인메모리 관리. `AtomicInteger` 캡슐화. 원자적 차감·반환·초기화·제거 제공. |
| `InMemoryCourseWaitlist` | `domain/waitlist` | 강의별 FIFO 대기열 인메모리 관리. `ConcurrentLinkedQueue` 캡슐화. 등록·추출·순번 조회·폐기 제공. |

**클래스명 컨벤션**: `InMemory` 접두사로 **휘발성·단일 인스턴스 한정** 특성을 식별자 수준에서 드러낸다. 다중 인스턴스 확장 시 `RedisCourseSeatCounter`, `RedisCourseWaitlist`로 치환하는 형태를 가정한다(§11.1).

---

## 3. 데이터 모델

### 3.1 ERD

```
┌───────────────────┐         ┌──────────────────────┐
│      course       │ 1     N │     enrollment       │
│───────────────────│◄────────│──────────────────────│
│ id (PK)           │         │ id (PK)              │
│ creator_id        │         │ course_id (FK)       │
│ title             │         │ classmate_id         │
│ description       │         │ status               │
│ price             │         │ created_at           │
│ max_capacity      │         │ confirmed_at         │
│ current_count     │         │ cancelled_at         │
│ start_date        │         └──────────┬───────────┘
│ end_date          │                    │ 1
│ status            │                    │
│ created_at        │                    │ 0..1
│ updated_at        │                    ▼
└───────────────────┘         ┌──────────────────────┐
                              │      payment         │
                              │──────────────────────│
                              │ id (PK)              │
                              │ enrollment_id (FK)   │
                              │ idempotency_key (UQ) │
                              │ external_payment_key │
                              │ status               │
                              │ created_at           │
                              │ updated_at           │
                              └──────────────────────┘
```

### 3.2 테이블 정의

#### `course`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| creator_id | BIGINT | NOT NULL | 크리에이터 ID |
| title | VARCHAR(200) | NOT NULL | |
| description | TEXT | | |
| price | INT | NOT NULL, CHECK(price >= 0) | |
| max_capacity | INT | NOT NULL, CHECK(max_capacity > 0) | 정원 |
| current_count | INT | NOT NULL DEFAULT 0, CHECK(current_count >= 0) | 현재 신청 인원 |
| start_date | DATE | NOT NULL | |
| end_date | DATE | NOT NULL | |
| status | VARCHAR(20) | NOT NULL | DRAFT/OPEN/CLOSED |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

추가 제약: `CHECK(current_count <= max_capacity)` — 정원 초과의 마지막 방어선.

인덱스: `idx_course_status (status)`, `idx_course_creator (creator_id)`.

#### `enrollment`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| course_id | BIGINT | NOT NULL, FK | |
| classmate_id | BIGINT | NOT NULL | |
| status | VARCHAR(20) | NOT NULL | PENDING/CONFIRMED/CANCELLED |
| created_at | TIMESTAMP | NOT NULL | |
| confirmed_at | TIMESTAMP | | CONFIRMED 전이 시각 (취소 가능 기간 기준일) |
| cancelled_at | TIMESTAMP | | |

인덱스: `idx_enrollment_course_status (course_id, status)`, `idx_enrollment_classmate (classmate_id)`.

#### `payment`

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| enrollment_id | BIGINT | NOT NULL, FK | |
| idempotency_key | VARCHAR(64) | NOT NULL, UNIQUE | 중복 결제 방지 |
| external_payment_key | VARCHAR(64) | | Mock 결제 시스템 식별자 |
| status | VARCHAR(20) | NOT NULL | PENDING/SUCCESS/FAILED/CANCELLED |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |

---

## 4. 동시성 제어 설계 ★ 핵심

### 4.1 문제 정의

OPEN 상태이며 정원이 1자리 남은 강의에 N명이 동시에 신청을 시도하는 경우, 정확히 1명만 Enrollment를 생성해야 하고 나머지 N-1명은 대기열로 이동하거나 거부되어야 한다.

이 문제의 핵심은 두 가지다.
1. **정확성**: 정원 초과가 발생해서는 안 된다.
2. **성능**: 락 직렬화로 인한 커넥션 점유와 응답 지연을 최소화한다.

설계 대안 4가지를 비교한 결과 4번 안(애플리케이션 세마포어 + 원자적 UPDATE)을 채택했다. **각 대안의 상세 비교는 Appendix A를 참고**한다.

### 4.2 채택 설계

**핵심 아이디어**: 강의별 남은 자리 수를 인메모리 `AtomicInteger`로 관리하고, DB 갱신은 원자적 UPDATE로 처리한다. 인메모리 카운터 차감과 DB 갱신을 논리적 트랜잭션으로 묶어 정합성을 보장한다.

#### 4.2.1 자료구조 (일급 컬렉션)

두 인메모리 컬렉션은 각각 전용 클래스로 캡슐화한다. 원시 `ConcurrentHashMap`을 Facade에 직접 노출하지 않는다.

```java
// domain/course/InMemoryCourseSeatCounter.java
@Component
public class InMemoryCourseSeatCounter {
    private final ConcurrentHashMap<Long, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** 강의 OPEN 전이 시점 호출. 기존 값 덮어씀. */
    public void initialize(Long courseId, int remaining) {
        counters.put(courseId, new AtomicInteger(remaining));
    }

    /**
     * 자리 1개 원자적 점유 시도.
     * @return 성공이면 true, 만석이면 false (이 경우 카운터는 자동 원복).
     * @throws CourseNotOpenException 등록되지 않은 강의
     */
    public boolean tryAcquire(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter == null) throw new CourseNotOpenException(courseId);
        if (counter.decrementAndGet() < 0) {
            counter.incrementAndGet();
            return false;
        }
        return true;
    }

    /** 자리 1개 반환. 수강 취소·보상 시 호출. */
    public void release(Long courseId) {
        AtomicInteger counter = counters.get(courseId);
        if (counter != null) counter.incrementAndGet();
    }

    /** 강의 CLOSED 전이 시점 호출. */
    public void remove(Long courseId) {
        counters.remove(courseId);
    }

    public int remaining(Long courseId) {
        AtomicInteger c = counters.get(courseId);
        return c == null ? 0 : Math.max(c.get(), 0);
    }
}
```

```java
// domain/waitlist/InMemoryCourseWaitlist.java
@Component
public class InMemoryCourseWaitlist {
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<Long>> queues = new ConcurrentHashMap<>();

    /** 대기열 등록. 등록 시점의 본인 순번 반환 (1-based). */
    public int enqueue(Long courseId, Long classmateId) {
        ConcurrentLinkedQueue<Long> queue = queues.computeIfAbsent(
            courseId, k -> new ConcurrentLinkedQueue<>());
        queue.offer(classmateId);
        return queue.size();
    }

    /** 최선두 사용자 1명 추출. 비어 있으면 Optional.empty(). */
    public Optional<Long> pollNext(Long courseId) {
        ConcurrentLinkedQueue<Long> queue = queues.get(courseId);
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.poll());
    }

    /** 강의 CLOSED·취소 시 대기열 폐기. */
    public void clear(Long courseId) {
        queues.remove(courseId);
    }

    /** 본인 순번 조회 (1-based). 미존재 시 empty. */
    public OptionalInt positionOf(Long courseId, Long classmateId) {
        ConcurrentLinkedQueue<Long> queue = queues.get(courseId);
        if (queue == null) return OptionalInt.empty();
        int idx = 0;
        for (Long id : queue) {
            idx++;
            if (id.equals(classmateId)) return OptionalInt.of(idx);
        }
        return OptionalInt.empty();
    }
}
```

**캡슐화 효과**:
- Facade는 도메인 의미 메서드(`tryAcquire`, `enqueue`)만 호출. `AtomicInteger.decrementAndGet`·`computeIfAbsent` 같은 저수준 호출이 비즈니스 코드에 새지 않음.
- 실패 시 카운터 원복 로직이 `tryAcquire` 내부에 위치하여 Facade에서 누락 위험 제거.
- 향후 `RedisCourseSeatCounter`로 치환 시 인터페이스 추출만 하면 됨.

#### 4.2.2 신청 흐름

```
[Facade]
  1. seatCounter.tryAcquire(courseId)
     - true  → 자리 확보, Service 호출
     - false → waitlist.enqueue(courseId, classmateId) 후 202 응답

  2. Service 호출 (DB 트랜잭션)
       - DB 원자적 UPDATE 시도
       - 성공 → Enrollment INSERT (PENDING)
       - 실패 → 예외 발생, Facade로 전파

  3. 예외 시 보상
       - waitlist.pollNext(courseId)로 다음 대기자 승급 시도
       - 대기열 비었으면 seatCounter.release(courseId)
```

#### 4.2.3 핵심 코드

```java
// Facade
public class EnrollmentFacade {
    private final InMemoryCourseSeatCounter seatCounter;
    private final InMemoryCourseWaitlist waitlist;
    private final EnrollmentService enrollmentService;

    public EnrollmentResult enroll(Long courseId, Long classmateId) {
        // 1. 자리 점유 시도 (인메모리, 트랜잭션 외부)
        if (!seatCounter.tryAcquire(courseId)) {
            int position = waitlist.enqueue(courseId, classmateId);
            return EnrollmentResult.waitlisted(position);
        }

        // 2. DB 트랜잭션 진입
        try {
            Long enrollmentId = enrollmentService.createEnrollment(courseId, classmateId);
            return EnrollmentResult.pending(enrollmentId);
        } catch (Exception e) {
            // 3. DB 실패 시 보상
            compensateOnFailure(courseId);
            throw e;
        }
    }

    private void compensateOnFailure(Long courseId) {
        waitlist.pollNext(courseId).ifPresentOrElse(
            next -> {
                try {
                    enrollmentService.createEnrollment(courseId, next);
                } catch (Exception e) {
                    compensateOnFailure(courseId); // 다음 대기자로 재귀
                }
            },
            () -> seatCounter.release(courseId)
        );
    }
}
```

```java
// Service
@Transactional
public Long createEnrollment(Long courseId, Long classmateId) {
    // 1. DB 원자적 UPDATE (정원 검증을 SQL 단에서 수행)
    int updated = courseRepository.tryIncreaseCurrentCount(courseId);
    if (updated == 0) {
        // Facade fast-fail 이후 도달했음에도 DB UPDATE 0건:
        //  - 강의 상태가 OPEN → CLOSED로 전이됨 (사용자 시나리오)
        //  - 인메모리/DB 카운터 정합성 깨짐 (내부 이슈, 보상 로직 처리)
        throw new CourseNotOpenException(courseId);
    }

    // 2. Enrollment INSERT
    Enrollment enrollment = Enrollment.create(courseId, classmateId);
    enrollmentRepository.save(enrollment);
    return enrollment.getId();
}
```

```sql
-- CourseRepository.tryIncreaseCurrentCount
UPDATE course
SET current_count = current_count + 1,
    updated_at = NOW()
WHERE id = :courseId
  AND status = 'OPEN'
  AND current_count < max_capacity;
```

#### 4.2.4 인메모리 카운터 초기화

강의가 OPEN으로 전이되는 시점에 인메모리 카운터를 초기화한다. 인메모리 조작은 **트랜잭션 외부**(Facade 계층)에서 수행한다. DB 커밋 전 카운터를 켜면 롤백 시 인메모리만 ON 상태로 남는 정합성 문제가 발생하므로, Service가 커밋 완료 후 Facade가 인메모리 자원을 조작한다.

`@Transactional` 메서드 return = 트랜잭션 커밋 완료(REQUIRED 기본). Facade 다음 줄은 자연스레 afterCommit 시점이다.

```java
// Service: 트랜잭션 내부. DB 조작만.
@Transactional
public CourseStatusChangeResult changeStatus(Long courseId, Long creatorId, CourseStatus next) {
    Course course = courseRepository.findById(courseId).orElseThrow(...);
    course.verifyCreator(creatorId);
    course.transitionTo(next);
    return new CourseStatusChangeResult(
        courseId, next, course.getMaxCapacity() - course.getCurrentCount());
}

// Facade: 트랜잭션 외부. 인메모리 자원 조작.
public void changeStatus(Long courseId, Long creatorId, CourseStatus next) {
    CourseStatusChangeResult result = courseService.changeStatus(courseId, creatorId, next);
    switch (result.status()) {
        case OPEN -> seatCounter.initialize(result.courseId(), result.remaining());
        case CLOSED -> {
            seatCounter.remove(result.courseId());
            waitlist.clear(result.courseId());
        }
        default -> {}
    }
}
```

CLOSED로 전이되는 경우 `seatCounter.remove(courseId)` + `waitlist.clear(courseId)`를 호출하여 인메모리 자원을 정리한다.

### 4.3 정합성 보장 메커니즘

| 시나리오 | 인메모리 카운터 | DB | 정합성 |
|---|---|---|---|
| 신청 성공 | 1 차감 | UPDATE 성공 후 INSERT | ✅ 동기 |
| 정원 만석 (인메모리에서 거부) | 차감 후 원복 | 변화 없음 | ✅ 동기 |
| 인메모리 통과, DB UPDATE 실패 | 보상 로직으로 원복 또는 대기자 승급 | 변화 없음 | ✅ 동기 |
| 인메모리 통과, DB INSERT 실패 | 보상 로직으로 원복 | 트랜잭션 롤백 | ✅ 동기 |

### 4.4 트레이드오프

| 항목 | 평가 |
|---|---|
| 정확성 | ✅ 정원 초과 불가 (DB CHECK 제약이 마지막 방어선) |
| 성능 | ✅ DB 락 미사용, 커넥션 점유 시간은 INSERT/UPDATE 시점에 국한 |
| 단일 인스턴스 가정 | ⚠️ 다중 인스턴스 환경에서는 인메모리 카운터가 인스턴스 간에 공유되지 않음. Redis 등 분산 카운터로 대체 필요. (§11 참고) |
| 서버 재기동 시 | ⚠️ 인메모리 카운터는 OPEN 강의 조회를 통해 복원 가능. 대기열은 손실됨(제약사항). |

---

## 5. 대기열 설계

### 5.1 자료구조

`InMemoryCourseWaitlist` 일급 컬렉션이 강의별 대기열을 캡슐화한다 (§4.2.1 정의 참고).

- 내부 자료구조: `ConcurrentHashMap<Long, ConcurrentLinkedQueue<Long>>`.
- `ConcurrentLinkedQueue`: FIFO 보장, 락 없는 동시성 안전.
- 영속화 없음: 서버 재기동 시 대기열 손실 (의도된 제약).
- 외부에 노출되는 메서드: `enqueue`, `pollNext`, `clear`, `positionOf`. 원시 큐 자료구조는 노출하지 않음.

### 5.2 진입 시점

다음 두 가지 경우에 대기열에 진입한다.

1. **정원 만석 시 신청** (4.2.3의 첫 번째 분기)
2. **DB 실패 시 보상의 일부로 진입했다가 다시 빠지지 못한 경우** — 실제로는 보상 로직이 즉시 다음 사람을 승급시키므로 이중 진입은 발생하지 않음.

### 5.3 추출(승급) 시점

| 사건 | 처리 |
|---|---|
| 수강 취소 (CONFIRMED → CANCELLED) | 1명 추출 → PENDING Enrollment 생성 |
| 강의 취소 | 대기열 전체 제거 (승급 없이 폐기) |
| DB 실패 보상 시 | 1명 추출 → PENDING Enrollment 생성 |

### 5.4 승급 처리 흐름

수강 취소 시 대기열 승급은 다음과 같이 처리된다. 인메모리 자원 조작은 모두 일급 컬렉션 메서드로 표현된다.

```java
// Service: DB 트랜잭션
@Transactional
public Long cancelEnrollment(Long enrollmentId, Long classmateId) {
    Enrollment enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow(...);
    enrollment.cancelByClassmate(classmateId, LocalDateTime.now()); // 7일 검증 포함
    courseRepository.decreaseCurrentCount(enrollment.getCourseId());
    return enrollment.getCourseId();
}

// Facade: 트랜잭션 커밋 후 후처리
public void cancelEnrollment(Long enrollmentId, Long classmateId) {
    Long courseId = enrollmentService.cancelEnrollment(enrollmentId, classmateId);

    // 자리 반환 → 다음 대기자 승급 시도
    seatCounter.release(courseId);
    promoteFromWaitlist(courseId);

    // Payment 환불 (Mock)
    paymentFacade.cancel(enrollmentId);
}

private void promoteFromWaitlist(Long courseId) {
    waitlist.pollNext(courseId).ifPresent(nextClassmate -> {
        // 방금 release로 +1 한 자리를 다시 점유. 이론상 항상 성공.
        if (!seatCounter.tryAcquire(courseId)) {
            // 방어적: 만약 동시에 다른 신청이 자리를 가져갔다면 대기열 맨 뒤로
            waitlist.enqueue(courseId, nextClassmate);
            return;
        }
        try {
            enrollmentService.createEnrollment(courseId, nextClassmate);
        } catch (Exception e) {
            compensateOnFailure(courseId);
        }
    });
}
```

### 5.5 순서 보장

`ConcurrentLinkedQueue`는 `offer()`와 `poll()`이 각각 원자적이며 호출 순서대로 FIFO를 보장한다. 동일 강의 내 대기열은 단일 큐로 통합 관리되므로 강의 단위의 공정성이 보장된다.

---

## 6. 트랜잭션 경계

### 6.1 수강 신청 흐름

```
┌─ Facade ─────────────────────────────────┐
│  ① 인메모리 카운터 차감 (트랜잭션 외부)    │
│                                          │
│  ┌─ Service @Transactional ────────────┐ │
│  │ ② DB 원자적 UPDATE                  │ │
│  │ ③ Enrollment INSERT                 │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  ④ 실패 시 보상 (트랜잭션 외부)            │
└──────────────────────────────────────────┘
```

### 6.2 결제 확정 흐름

```
┌─ PaymentFacade ──────────────────────────────┐
│                                              │
│  ┌─ Service @Transactional ────────────────┐ │
│  │ ① Enrollment: PENDING → CONFIRMED       │ │
│  │ ② Payment INSERT (status=PENDING)       │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  ③ 외부 결제 Mock API 호출 (트랜잭션 외부)    │
│                                              │
│  ┌─ Service @Transactional ────────────────┐ │
│  │ ④ Payment: PENDING → SUCCESS/FAILED     │ │
│  │ ⑤ 실패 시 Enrollment: CONFIRMED→PENDING │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

외부 API 호출을 트랜잭션 외부로 빼는 이유는 두 가지다.
1. DB 커넥션을 외부 API 응답 시간 동안 점유하지 않기 위함.
2. 외부 API의 응답에 따라 후속 트랜잭션을 분기하기 위함.

### 6.3 수강 취소 흐름

```
┌─ EnrollmentFacade ─────────────────────────┐
│  ┌─ Service @Transactional ──────────────┐ │
│  │ ① Enrollment: CONFIRMED → CANCELLED   │ │
│  │    (7일 기간 검증 포함)                │ │
│  │ ② course.current_count 1 감소          │ │
│  └───────────────────────────────────────┘ │
│  ③ 인메모리 카운터 +1                       │
│  ④ 대기자 승급 시도                         │
│  ⑤ Payment 환불 (Mock)                     │
└────────────────────────────────────────────┘
```

### 6.4 강의 취소 흐름 (보상 트랜잭션) — **본 과제 범위 외**

> ⚠️ **본 절은 향후 확장 시 참고용**. PRD §2.3에 따라 크리에이터의 강의 취소(전체 수강생 일괄 환불·다단계 보상 트랜잭션)는 평가 핵심(상태/정원/동시성)과 직접 관련이 없어 구현하지 않는다. 크리에이터는 강의를 CLOSED로 전이시키는 것까지만 가능하다. `CourseCancelFacade`는 구현 산출물에 포함되지 않는다.

```
┌─ CourseCancelFacade (미구현) ────────────────┐
│  ┌─ Service @Transactional ──────────────┐ │
│  │ ① 모든 Enrollment를 CANCELLED로       │ │
│  │ ② course.current_count = 0             │ │
│  │ ③ course.status = CLOSED               │ │
│  └───────────────────────────────────────┘ │
│  ④ 인메모리 카운터 제거                     │
│  ⑤ 대기열 비움                              │
│  ⑥ 각 Payment에 대해 외부 결제 취소(Mock)   │
│     - 실패 시 보상 트랜잭션 호출            │
└────────────────────────────────────────────┘
```

보상 트랜잭션은 위 ①②③을 역으로 되돌린다. 단, 보상 트랜잭션 자체가 실패하는 경우는 분산 상태 불일치이므로 운영 로그에 "관리자 확인 필요" 마커를 남기고 종료한다(범위 외 명시).

---

## 7. 상태 머신 구현

### 7.1 Enum과 전이 검증

```java
public enum CourseStatus {
    DRAFT, OPEN, CLOSED;

    private static final Map<CourseStatus, Set<CourseStatus>> ALLOWED = Map.of(
        DRAFT,  Set.of(OPEN, CLOSED),
        OPEN,   Set.of(CLOSED),
        CLOSED, Set.of(OPEN)
    );

    public void verifyTransitionTo(CourseStatus next) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(next)) {
            throw new IllegalStateTransitionException(this, next);
        }
    }
}

public enum EnrollmentStatus {
    PENDING, CONFIRMED, CANCELLED;

    private static final Map<EnrollmentStatus, Set<EnrollmentStatus>> ALLOWED = Map.of(
        PENDING,   Set.of(CONFIRMED),
        CONFIRMED, Set.of(PENDING, CANCELLED)
    );

    public void verifyTransitionTo(EnrollmentStatus next) { ... }
}
```

### 7.2 도메인 객체 내 전이 메서드

상태 전이는 도메인 객체의 메서드를 통해서만 가능하도록 하여, 잘못된 전이가 컴파일 또는 런타임에 거부되도록 한다.

```java
public class Enrollment {
    public void confirm() {
        this.status.verifyTransitionTo(EnrollmentStatus.CONFIRMED);
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void rollbackToPending() {
        this.status.verifyTransitionTo(EnrollmentStatus.PENDING);
        this.status = EnrollmentStatus.PENDING;
        this.confirmedAt = null;
    }

    public void cancelByClassmate(Long requesterId, LocalDateTime now) {
        this.status.verifyTransitionTo(EnrollmentStatus.CANCELLED);
        if (!this.classmateId.equals(requesterId)) {
            throw new UnauthorizedException();
        }
        if (now.isAfter(this.confirmedAt.plusDays(7))) {
            throw new CancellationPeriodExpiredException();
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
    }
}
```

---

## 8. 결제 시스템 연동 (Mock)

### 8.1 멱등키 설계

**입력 경로**: 클라이언트가 `Idempotency-Key` HTTP 헤더로 전달한다 (필수, `VARCHAR(64)`). 누락 시 400 Bad Request. 서버 자동 생성 방식은 채택하지 않는다 — 매 요청마다 다른 키가 생성되어 UNIQUE 제약의 멱등성 의미가 무력화되기 때문.

**저장 위치**: `payment.idempotency_key` UNIQUE 인덱스 단독. 별도 메모리 캐시 레이어를 두지 않는다. 정확성은 DB UNIQUE가 단독 책임이며, 캐시는 성능 최적화 전용으로 본 과제 평가 핵심(상태/정원/동시성)과 무관하다. 단일 인스턴스 + Mock 결제 환경에서 결제 트래픽은 평가 대상 아님.

**중복 결제 처리**:
- 동일 키로 두 번째 요청 → `DataIntegrityViolationException` catch → `DuplicatePaymentException` → 409 Conflict.
- 결제 실패 후 재결제 시 클라이언트는 **새로운 키**를 발급해야 한다 (Tech-spec §8.3 결제 실패 처리 참고).

```java
@PostMapping("/api/enrollments/{enrollmentId}/payment")
public ResponseEntity<PaymentResponse> pay(
    @PathVariable Long enrollmentId,
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    ...
}

@Transactional
public Long createPayment(Long enrollmentId, String idempotencyKey) {
    try {
        Payment p = Payment.create(enrollmentId, idempotencyKey);
        return paymentRepository.save(p).getId();
    } catch (DataIntegrityViolationException e) {
        throw new DuplicatePaymentException(idempotencyKey);
    }
}
```

### 8.2 결제와 Enrollment 상태의 관계

| Payment 상태 | Enrollment 상태 | 의미 |
|---|---|---|
| PENDING | CONFIRMED | 외부 결제 API 호출 중 |
| SUCCESS | CONFIRMED | 결제 완료, 수강 확정 |
| FAILED | PENDING (롤백됨) | 결제 실패, 재시도 가능 |
| CANCELLED | CANCELLED | 수강 취소 또는 강의 취소로 인한 환불 |

### 8.3 결제 실패 처리

외부 결제 Mock API가 실패 응답을 반환하면, Payment를 FAILED로 기록한 뒤 Enrollment를 CONFIRMED → PENDING으로 롤백한다. 사용자는 동일한 Enrollment에 대해 결제를 재시도할 수 있다(멱등키가 달라야 함).

### 8.4 결제 흐름 선택의 근거

본 시스템은 "애플리케이션 내부 로직 모두 성공 후 외부 API 호출"의 순서를 따른다.

**대안과의 비교**:
- "외부 API 호출 → 성공 시 내부 상태 전이"의 경우, 외부 결제가 성공한 뒤 내부 상태 전이가 실패하면 외부 결제 취소 API를 호출해야 한다. 외부 결제 취소는 비용(시간, 수수료, 호출 실패 가능성)이 크다.
- "내부 상태 전이 → 외부 API 호출 → 실패 시 내부 롤백"의 경우, 외부 API 실패 시 내부 롤백은 단순 UPDATE 한 번이므로 비용이 작다.

따라서 후자를 채택한다.

---

## 9. 예외 처리

### 9.1 예외 계층

```
BusinessException (RuntimeException)
 ├── CourseException
 │    ├── CourseNotFoundException
 │    ├── IllegalCourseStateTransitionException
 │    └── CourseNotOpenException
 ├── EnrollmentException
 │    ├── EnrollmentNotFoundException
 │    ├── IllegalEnrollmentStateTransitionException
 │    └── CancellationPeriodExpiredException
 ├── PaymentException
 │    ├── PaymentFailedException
 │    └── DuplicatePaymentException
 └── AuthException
      └── UnauthorizedException
```

### 9.2 HTTP 응답 매핑

#### 성공 응답

| 시나리오 | HTTP | 응답 body |
|---|---|---|
| 강의 등록 | 201 Created | `{"courseId":..., "status":"DRAFT"}` + `Location: /api/courses/{id}` |
| 수강 신청 — 자리 확보 (Enrollment 생성) | **201 Created** | `{"enrollmentId":..., "status":"PENDING"}` + `Location: /api/enrollments/{id}` |
| 수강 신청 — 정원 만석 (대기열 등록) | **202 Accepted** | `{"status":"WAITLISTED", "courseId":..., "position":N}` |
| 결제 진행 (성공) | 200 OK | `{"paymentId":..., "status":"SUCCESS", "enrollmentStatus":"CONFIRMED"}` |
| 수강 취소 | 200 OK | `{"enrollmentId":..., "status":"CANCELLED"}` |
| 강의 상태 전이 | 200 OK | `{"courseId":..., "status":...}` |
| 목록·상세 조회 | 200 OK | 도메인 표현 |

201/202 구분 근거:
- **201 Created** — Enrollment 레코드가 실제로 INSERT되어 영속 리소스가 생성됨.
- **202 Accepted** — 요청은 수락됐으나 Enrollment는 아직 미생성. 대기열은 인메모리 등록일 뿐이며, 자리가 발생할 때 시스템이 비동기적으로 Enrollment를 생성한다. 사용자는 대기 응답을 받고 떠나도 되며, 추후 본인 신청 목록 조회(`GET /api/me/enrollments`)로 상태를 확인한다. 결제 가능 시점의 푸시/이메일 통지는 본 과제 범위 외.

#### 예외 응답

| 예외 | HTTP 상태 | 비고 |
|---|---|---|
| CourseNotFoundException, EnrollmentNotFoundException | 404 | |
| IllegalStateTransitionException 계열 | 409 | 잘못된 상태 전이 |
| CourseNotOpenException | 409 | DRAFT/CLOSED 강의에 신청 시. 정원 만석은 예외가 아닌 202 응답으로 처리되므로 본 예외에서 분리됨 |
| CancellationPeriodExpiredException | 409 | 7일 경과 |
| UnauthorizedException | 403 | |
| DuplicatePaymentException | 409 | 멱등키 중복 |
| PaymentFailedException | 502 | 외부 결제 실패 (재시도 가능) |
| 검증 실패 (Bean Validation) | 400 | |
| 그 외 RuntimeException | 500 | |

---

## 10. 테스트 전략

### 10.1 단위 테스트

- 도메인 객체의 상태 전이 검증 (허용/불허 케이스)
- 7일 취소 기간 검증
- Enum 전이 매핑 검증

### 10.2 동시성 테스트 ★ 필수

`CountDownLatch`를 사용하여 N개의 스레드가 동시에 한 강의에 신청하는 시나리오를 검증한다.

```java
@Test
void concurrent_enrollment_does_not_exceed_capacity() throws InterruptedException {
    // given: 정원 10명인 강의, 100명 동시 신청
    Long courseId = createOpenCourse(10);
    int threadCount = 100;
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger waitlistCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        long classmateId = i + 1;
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                EnrollmentResult result = enrollmentFacade.enroll(courseId, classmateId);
                if (result.isPending()) successCount.incrementAndGet();
                else if (result.isWaitlisted()) waitlistCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        });
    }

    ready.await();
    start.countDown();
    done.await();

    // then
    assertThat(successCount.get()).isEqualTo(10);
    assertThat(waitlistCount.get()).isEqualTo(90);

    Course course = courseRepository.findById(courseId).orElseThrow();
    assertThat(course.getCurrentCount()).isEqualTo(10);
}
```

### 10.3 통합 테스트 시나리오

- 정상 신청 → 결제 → 확정
- 정원 만석 → 대기열 진입 → 다른 사용자 취소 → 대기자 승급
- 결제 실패 → Enrollment 롤백 → 재결제 성공
- 7일 경과 후 취소 시도 → 거부
- 강의 취소 → 모든 Enrollment CANCELLED + 환불

---

## 11. 한계 및 향후 개선

### 11.1 다중 인스턴스 확장 시

인메모리 카운터와 대기열은 단일 인스턴스에서만 유효하다. 다중 인스턴스 환경에서는 다음 대체가 필요하다.

| 현재 | 다중 인스턴스 환경 대체안 |
|---|---|
| `InMemoryCourseSeatCounter` (`ConcurrentHashMap<Long, AtomicInteger>`) | `RedisCourseSeatCounter` — Redis `DECR`/`INCR` 또는 Lua 스크립트 기반 원자적 카운터 |
| `InMemoryCourseWaitlist` (`ConcurrentLinkedQueue`) | `RedisCourseWaitlist` — Redis List(`LPUSH`/`RPOP`) 또는 DB 영속 대기열 테이블 |
| Facade 내 보상 로직 | 동일 유지. 일급 컬렉션을 인터페이스로 추출(`CourseSeatCounter`, `CourseWaitlist`) 후 구현체만 치환 |

### 11.2 대기열 영속화

현재 대기열은 서버 재기동 시 손실된다. 영속화가 필요한 경우 다음을 고려할 수 있다.

- DB `waitlist` 테이블에 강의 ID, 사용자 ID, 등록 순서 컬럼을 두고 영속화
- Redis List + AOF persistence
- 다만 영속화 시 대기열 입출 작업이 트랜잭션 또는 외부 시스템 호출로 변경되어 신청 흐름이 복잡해진다는 트레이드오프 존재

### 11.3 PENDING TTL

현재는 PENDING 상태가 영구히 유지되며, 결제하지 않은 신청이 정원을 점유한다. 운영 환경에서는 일정 시간(예: 30분) 후 자동 취소 + 대기자 승급 로직이 필요하다. 본 과제에서는 범위 외로 명시.

### 11.4 동일 사용자 중복 신청 방지

현재 동일 사용자가 같은 강의에 여러 PENDING/CONFIRMED Enrollment를 생성하는 것을 시스템 차원에서 막지 않는다. 운영 환경에서는 `UNIQUE(course_id, classmate_id)` 부분 인덱스 또는 애플리케이션 검증이 필요하다. 본 과제에서는 범위 외로 명시.

---

## Appendix A. 동시성 제어 대안 비교

본 부록은 §4의 채택 설계에 이르기까지 검토한 4가지 대안과 그 트레이드오프를 정리한다.

### A.1 Option 1: Row-level 비관적 락 (`SELECT ... FOR UPDATE`)

**흐름**:
1. 강의 ID로 강의 조회 (비관적 락)
2. 상태가 OPEN인지 검증
3. 정원에서 1 차감 (UPDATE)
4. Enrollment INSERT (PENDING)

**장점**:
- 구현이 단순하고 정확성이 보장된다.

**단점**:
- 로직 수행 시간 전체에 row-level 락이 걸린다.
- 동시 신청 시 모든 요청이 락을 대기한다(직렬화).
- DB 커넥션을 락 대기 시간만큼 점유하여 다른 API 응답에도 영향을 미친다.
- Fast-fail이 불가능하다 (락 획득 전에는 정원 만석을 알 수 없음).

### A.2 Option 2: 원자적 UPDATE 단독

**흐름**:
1. 강의 ID로 강의 조회 (락 없음)
2. 상태가 OPEN인지 검증
3. `UPDATE course SET current_count = current_count + 1 WHERE id = ? AND status = 'OPEN' AND current_count < max_capacity`
4. 영향받은 행이 0이면 예외, 아니면 Enrollment INSERT

**개선점**:
- 락 점유 시간이 UPDATE 시점으로 한정되어 커넥션 점유가 줄어듦.

**단점**:
- DB에서 동일 행에 대한 UPDATE는 내부적으로 row-level 락으로 직렬화됨.
- 트래픽이 증가하면 여전히 커넥션 점유 문제 발생.
- DB까지 가야 자리 만석을 알 수 있으므로 fast-fail이 늦음.

### A.3 Option 3: 애플리케이션 레벨 락 + 원자적 UPDATE

**흐름**:
1. 강의 ID 기반 애플리케이션 레벨 락 획득 (예: `ReentrantLock` per course)
2. (락 내부) 강의 조회 → 상태 검증 → 원자적 UPDATE
3. (락 해제 후) Enrollment INSERT

**장점**:
- DB 커넥션 점유 문제를 애플리케이션 레벨 직렬화로 해결.

**단점**:
- 여전히 직렬 처리이므로 응답 시간이 누적됨.
- DB 트랜잭션 내부에서 락을 잡으면 데드락 위험.
- 락 외부에서 INSERT를 수행하면 정합성 관리가 복잡해짐.

### A.4 Option 4: 애플리케이션 세마포어 + 원자적 UPDATE ★ 채택

**흐름**:
1. 강의 OPEN 전이 시점에 인메모리 카운터 초기화 (강의별 남은 자리)
2. 신청 요청 시 카운터에서 원자적 차감
   - 차감 실패 → 대기열로 직행 (fast-fail)
   - 차감 성공 → DB 트랜잭션 진입
3. DB 원자적 UPDATE + Enrollment INSERT
4. DB 실패 시 보상 로직 (카운터 원복 또는 대기자 승급)

**장점**:
- DB 락에 의존하지 않음. 커넥션 점유 시간 최소.
- 정원 만석은 인메모리에서 즉시 판별 (fast-fail).
- 직렬 처리 문제 해소: 인메모리 카운터의 원자 연산만으로 동시성 제어.

**단점**:
- 정원 수의 관리 지점이 두 곳(인메모리, DB)으로 분리되어 정합성 관리 복잡도 증가.
- 단일 인스턴스에 한정된 설계.

**정합성 보장 전략**:
- 인메모리 카운터 차감과 DB UPDATE를 논리적 트랜잭션으로 묶음.
- DB UPDATE 실패 시 보상 로직으로 카운터를 즉시 원복하거나, 대기열의 다음 사용자에게 자리를 양도.
- DB의 `CHECK(current_count <= max_capacity)` 제약을 마지막 방어선으로 둠.

### A.5 결론

본 시스템은 **단일 인스턴스 단일 DB**라는 환경 가정 하에서, **fast-fail과 커넥션 점유 최소화**를 우선순위로 두어 Option 4를 채택한다. 다중 인스턴스로 확장하는 경우 인메모리 자료구조를 Redis 등 분산 자료구조로 치환하는 방향으로 진화 가능하다(§11).
