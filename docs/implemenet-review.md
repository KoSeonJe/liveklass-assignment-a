# Implement-plan 검토 리뷰

implement-plan.md를 PRD.md, Tech-spec.md, CLAUDE.md, test-strategy.md와 교차 검증한 결과 도출한 7개 이슈와 보정 방향.

---

## 1. Phase 4 `seatCounter.initialize` 트랜잭션 경계 ↔ Tech-spec §4.2.4 모순

### 충돌 지점

- **Tech-spec §4.2.4 코드 예제 (line 405~415)**:
  ```java
  @Transactional
  public void openCourse(Long courseId, Long creatorId) {
      Course course = courseRepository.findById(courseId).orElseThrow(...);
      course.verifyCreator(creatorId);
      course.transitionTo(CourseStatus.OPEN);
      int remaining = course.getMaxCapacity() - course.getCurrentCount();
      seatCounter.initialize(courseId, remaining);  // 트랜잭션 내부
  }
  ```
- **implement-plan Phase 4 (수정 전)**: `TransactionSynchronization.afterCommit` 콜백 등록
- **CLAUDE.md 아키텍처 원칙**: "Facade는 트랜잭션 **외부**. 인메모리 카운터·대기열·보상 로직"

세 문서가 서로 다른 시점을 지시. 코드 작성자가 혼란.

### 왜 트랜잭션 외부가 옳은가

| 시나리오 | Tech-spec 방식 (트랜잭션 내부) | 외부 방식 |
|---|---|---|
| 커밋 성공 | 카운터 ON, DB ON. 동일 결과 | 카운터 ON, DB ON |
| **커밋 실패/롤백** | **카운터 ON, DB OFF → 정합성 깨짐** | 카운터 미변경. 정합성 유지 |
| 커밋 직전 다른 스레드 신청 | DB에 OPEN 아직 미반영 상태에서 신청 들어옴 → DB UPDATE 0건 → 보상 로직 발동 | 커밋 후만 카운터 노출. 안전 |
| Spring `@Transactional` 의미론 | "메서드 본문 = 트랜잭션 범위". 비-DB 사이드이펙트를 안에 넣으면 롤백 불가능한 부수효과 누적 | 부수효과 분리 |

→ 트랜잭션 **외부** (Facade 또는 afterCommit) 채택이 정합성 측면에서 안전.

### 채택안: Option A (Facade-Service 분리)

`@Transactional` 메서드 return = 커밋 완료 시점 (REQUIRED 기본). Facade 다음 줄 자연스레 afterCommit.

```java
// Service: 트랜잭션 내부. DB만.
@Transactional
public CourseStatusChangeResult changeStatus(Long courseId, Long creatorId, CourseStatus next) {
    Course course = courseRepository.findById(courseId).orElseThrow(...);
    course.verifyCreator(creatorId);
    course.transitionTo(next);
    return new CourseStatusChangeResult(
        courseId, next, course.getMaxCapacity() - course.getCurrentCount());
}

// Facade: 트랜잭션 외부.
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

### 보정 작업

- [x] implement-plan Phase 4 문구 수정 완료 (Facade-Service 분리 패턴)
- [ ] **Tech-spec §4.2.4 코드 예제도 같은 패턴으로 정렬** — 잔존 시 plan ↔ tech-spec 영구 불일치

### 대안 비교

| 방식 | 코드 노이즈 | 테스트 용이성 | 추천도 |
|---|---|---|---|
| Facade-Service 분리 (채택) | 낮음 (반환 타입만 추가) | 높음. Service 단독 테스트 + Facade 단독 테스트 분리 가능 | ★★★ |
| `@TransactionalEventListener(AFTER_COMMIT)` | 중간 (이벤트 클래스 추가) | 중간. 비동기·간접 호출 추적 어려움 | ★★ (과제 규모엔 과함) |
| `TransactionSynchronizationManager.register` | 높음 (Spring 내부 API 직접 사용) | 낮음 | ★ (비추천) |

---

## 2. `Clock` 주입 시점 충돌

### 충돌 지점

- **Phase 10 E2E** (line 170): "9일 후 취소 409 (`Clock` 주입으로 시간 조작)"
- **Phase 12 Hardening** (line 187): "`Clock` 의존성 주입으로 7일 검증을 테스트에서 조작 가능하게"

Phase 10에서 이미 `Clock` 의존성이 활성화되어 있어야 9일 후 시나리오 E2E가 가능. Phase 12로 미루면 Phase 10 종료 조건("4계층 테스트 전부 그린") 불충족.

### 의존성 분석

Phase 5 (Enrollment 도메인) `Enrollment.cancelByClassmate(requesterId, now)` 시그니처가 이미 `LocalDateTime` 인수를 받음 (Tech-spec §7.2 line 640). 즉 도메인 객체는 시간을 **주입받음** — `LocalDateTime.now()`를 내부에서 호출하지 않음.

→ Phase 5 도메인 단위 테스트는 `Clock` 없이도 `LocalDateTime` 인수로 직접 시간 조작 가능. **도메인 단위는 Phase 5에서 그린 달성.**

문제는 **Service/E2E 계층**. Service에서 `LocalDateTime.now()` 호출 시 테스트 시간 조작 불가능. → `Clock`을 빈으로 주입해야 함.

### 보정 방향

Phase별 책임 재분배:

| Phase | 책임 |
|---|---|
| Phase 5 | `Enrollment.cancelByClassmate(requesterId, now)` 시그니처 확정. 도메인 단위 테스트는 `now` 인수 직접 주입. **`Clock` 빈 불필요.** |
| **Phase 10 (수정)** | `Clock` 빈 도입 (`@Configuration` + `Clock.systemDefaultZone()`). `EnrollmentService.cancel`에서 `clock.instant()` 사용. E2E에서는 `@TestConfiguration`으로 `Clock.fixed` 주입. |
| Phase 12 | "Clock 주입" 항목 제거. 이미 Phase 10에서 완료. |

### 구현 가이드

```java
// 운영 빈
@Configuration
class TimeConfig {
    @Bean Clock clock() { return Clock.systemDefaultZone(); }
}

// Service
@Transactional
public Long cancelEnrollment(Long enrollmentId, Long classmateId) {
    Enrollment e = enrollmentRepository.findById(enrollmentId).orElseThrow(...);
    e.cancelByClassmate(classmateId, LocalDateTime.now(clock));  // clock 주입
    ...
}

// E2E 테스트
@TestConfiguration
class FixedClockConfig {
    @Bean @Primary Clock clock() {
        return Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC);
    }
}
```

---

## 3. PaymentStatus 전이 매핑 누락 위험

### 충돌 지점

- **Tech-spec §5.3 다이어그램 (line 210~216)**: `PENDING → SUCCESS/FAILED/CANCELLED` 화살표만 존재. `SUCCESS → CANCELLED` 누락.
- **Tech-spec §8.2 표 (line 664~669)**: `CANCELLED | CANCELLED | 수강 취소 또는 강의 취소로 인한 환불`
- **PRD §6.5 (line 262)**: "Payment를 CANCELLED로 전이(환불 처리, Mock)" — 수강 취소 시 호출. 이 시점 Payment는 **SUCCESS** 상태 (결제 완료 후 7일 이내 취소 가능 규칙).

또한 다이어그램에 표기된 `PENDING → CANCELLED` 전이는 **실제 트리거가 없는 죽은 경로**.

### `PENDING → CANCELLED` 전이가 죽은 이유

Payment row INSERT 시점 = 결제 시도 시 tx#1 (Tech-spec §6.2). 그 전에는 row 자체가 없음.

| 가상 트리거 | 실제 동작 | PENDING → CANCELLED 발생? |
|---|---|---|
| 결제 미시도 상태에서 Enrollment 취소 | Payment row 없음. 전이 대상 부재 | ❌ |
| 외부 결제 API 타임아웃/실패 | `FAILED`로 감 (§5.3 정의) | ❌ |
| 사용자가 결제 창 중도 이탈 | PRD §2.3 범위 외 (PENDING TTL 미구현) | ❌ |
| 결제 시도 중 수강 취소 동시 요청 | tx#1 진행 중에는 락/원자성으로 충돌 차단. 실제 발생 시 §2.3 범위 외 | ❌ |

→ `PENDING → CANCELLED`는 본 시스템 흐름상 트리거 없음. 다이어그램에 남기면 죽은 경로 + 도메인 단위 테스트도 불가능 (호출 시나리오 없음).

### 실제 필요한 Payment 전이

| From | To | 트리거 |
|---|---|---|
| (없음) | PENDING | 결제 진행 tx#1: Payment INSERT |
| PENDING | SUCCESS | 외부 결제 Mock 성공 응답 |
| PENDING | FAILED | 외부 결제 Mock 실패 응답 |
| SUCCESS | CANCELLED | 수강 취소 (7일 이내) 환불 처리 |

### 보정 방향

**Phase 8 산출물 — `PaymentStatus` enum**:

```java
public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
        PENDING, Set.of(SUCCESS, FAILED),  // CANCELLED 제거
        SUCCESS, Set.of(CANCELLED)          // 환불 경로 추가
    );
    ...
}
```

**Phase 8 도메인 단위 테스트 케이스**:
- `PENDING → SUCCESS` 허용
- `PENDING → FAILED` 허용
- `PENDING → CANCELLED` **불허** (트리거 부재)
- `SUCCESS → CANCELLED` 허용 (환불)
- `FAILED → CANCELLED` 불허 (실패한 결제 환불 의미 없음)
- `CANCELLED → *` 불허 (종단)

**Tech-spec 문서 정리**:
- §5.3 다이어그램에서 `PENDING → CANCELLED` 화살표 제거
- §5.3 다이어그램에 `SUCCESS → CANCELLED` 화살표 추가
- §8.2 표 그대로 유지 (이미 SUCCESS/CANCELLED 의미 일치)

---

## 4. 멱등키 입력 경로 미명시

### 충돌 지점

- **Tech-spec §8.1 (line 660)**: "클라이언트가 멱등키를 헤더로 전달하거나, 서버가 `enrollment_id + timestamp` 기반으로 생성한다"
- **implement-plan Phase 9**: 멱등키 처리 흐름 명시 없음. Controller DTO 설계 시 결정 사항 누락.

### 결정 필요 항목

| 항목 | 옵션 A: 클라이언트 헤더 | 옵션 B: 서버 생성 |
|---|---|---|
| 헤더명 | `Idempotency-Key` (관행) | 불필요 |
| 키 생성 책임 | 클라이언트 | Server (`enrollment_id + timestamp` 또는 UUID) |
| 동일 결제 재시도 시 | 같은 키 재전송 → DB UNIQUE 위반 → 409 | 매번 새 키 → 중복 차단 불가 |
| 결제 실패 후 재결제 | 다른 키 필요 (Tech-spec §8.3) | 자동으로 다름 |
| 추천도 | ★★★ (멱등성 의미 충실) | ★ (멱등키 의미 무력화) |

→ **옵션 A 권장**. 멱등키는 "동일 결제 시도 식별자". 서버 자동 생성이면 매번 다른 키 → UNIQUE 제약이 무의미.

### 멱등키 저장 위치: DB UNIQUE 단독 (메모리 캐시 미도입)

| 책임 | DB UNIQUE 단독 | + 메모리 캐시 |
|---|---|---|
| 정확성 (동일 키 중복 INSERT 차단) | ✅ DB 레벨 보장. 단일 진실 공급원 | ✅ (단, 캐시 race window 존재 → 결국 DB 재검증 필수) |
| 동시성 (같은 키 동시 두 요청) | ✅ DB UNIQUE = 둘 중 하나만 성공 | 캐시 hit 둘 다 통과 가능. DB 의존 |
| 재기동 후 멱등 보장 | ✅ DB 영속 | ❌ 캐시 손실 → DB 재검증 없으면 중복 결제 위험 |
| 성능 | INSERT 시 B-tree lookup 1회. 본 과제 트래픽엔 충분 | hot-path 캐시 hit 시 DB 회피 |
| 코드 분량 | catch + 변환 ~5줄 | 캐시 빈 + TTL + sync 로직 추가 |

**판단**: 메모리 캐시 = 오버엔지니어링.
- 정확성은 DB UNIQUE가 단독 책임. 캐시는 **성능 최적화 전용**이며, 본 과제 평가 키워드(상태/정원/동시성)와 무관.
- 단일 인스턴스 + Mock 결제 환경에서 결제 트래픽이 평가 대상 아님.
- 캐시 도입 시 재기동/TTL/sync 등 추가 복잡도가 평가 핵심 흐려뜨림.

### 보정 방향

**Phase 9 산출물에 명시**:
- Request 헤더 `Idempotency-Key` (필수, `VARCHAR(64)`).
- 누락 시 400 Bad Request.
- 저장 = `payment.idempotency_key` UNIQUE 인덱스 단독. 별도 캐시 레이어 없음.
- UNIQUE 위반 catch → `DuplicatePaymentException` → 409 Conflict.

```java
@PostMapping("/api/enrollments/{enrollmentId}/payment")
public ResponseEntity<PaymentResponse> pay(
    @PathVariable Long enrollmentId,
    @RequestHeader("X-User-Id") Long userId,
    @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
    ...
}

// Service
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

**Phase 9 E2E 테스트 추가**:
- 헤더 누락 → 400
- 동일 헤더로 재요청 → 409 (DuplicatePayment)
- 다른 헤더로 재요청 → 정상 처리

**README 미구현/제약사항에 명시**:
- "멱등키 중복 검증은 DB UNIQUE 인덱스 단독. 캐시 레이어 미도입 (단일 인스턴스 + 평가 핵심 외 항목)"

---

## 5. PRD 자체 모순 (implement-plan 책임 아님)

### 충돌 지점

- **PRD §3.3 (line 102)**: "강의 상태 변경 시에만 `creator_id == X-User-Id` 검증을 수행한다. 그 외의 조회는 모든 사용자에게 열려 있다."
- **PRD §8.1 (line 316)**: `GET /api/courses/{courseId}/enrollments` — **"강의별 수강생 목록 조회 (크리에이터 전용)"**

§3.3 = "조회 전체 허용". §8.1 = "이 조회는 크리에이터 전용". 모순.

### implement-plan의 선택

Phase 11 (line 179): `EnrollmentQueryService.listForCourse(courseId, requesterId, page, size)` (소유자 검증) → §8.1 따름.

→ implement-plan은 합리적 선택. 문제는 PRD 자체.

### 보정 방향

**PRD §3.3 문구 수정 권장**:

> 수정 전: "강의 상태 변경 시에만 `creator_id == X-User-Id` 검증을 수행한다. 그 외의 조회는 모든 사용자에게 열려 있다."
>
> 수정 후: "강의 상태 변경 및 **강의별 수강생 목록 조회** 시 `creator_id == X-User-Id` 검증을 수행한다. 강의 목록·상세 조회 및 본인 수강 신청 조회는 모든 사용자에게 열려 있다."

implement-plan Phase 11 자체는 수정 불필요. PRD 정합성만 맞추면 됨.

---

## 6. Tech-spec §6.4 강의 취소 / CLAUDE.md `CourseCancelFacade` 잔존

### 충돌 지점

- **PRD §2.3 (line 74)**: "크리에이터의 강의 취소 (전체 수강생 일괄 환불·보상 트랜잭션 포함) — 다단계 외부 결제 취소·보상 트랜잭션 설계는 과제 범위 대비 복잡도가 크며, 평가 핵심과 직접 관련이 없어 제외. 크리에이터는 강의를 CLOSED로 전이시키는 것까지만 가능하다."
- **Tech-spec §6.4 (line 569~585)**: `CourseCancelFacade` 흐름 다이어그램 + 보상 트랜잭션 명세 그대로 존재
- **CLAUDE.md 패키지 구조 (line 81)**: `facade` 디렉토리에 `CourseCancelFacade` 명시
- **implement-plan**: 강의 취소 phase 없음. PRD 따름. → 정상.

### 영향

implement-plan은 PRD §2.3을 정확히 준수. 그러나 Tech-spec과 CLAUDE.md를 읽고 작업하는 개발자는 `CourseCancelFacade` 구현이 필요하다고 오해 가능. 작업 일관성 깨짐.

### 보정 방향

3가지 선택지:

| 옵션 | 작업량 | 추천 |
|---|---|---|
| A. Tech-spec §6.4 + CLAUDE.md `CourseCancelFacade` 삭제 | 소 | ★★★ |
| B. Tech-spec §6.4에 "본 절은 향후 확장 시 참고용. 본 과제 범위 외(PRD §2.3 참조)" 주석 추가, CLAUDE.md 동일 처리 | 소 | ★★ |
| C. 그대로 두고 README 미구현 사항에만 명시 | 무 | ★ (혼란 잔존) |

→ **옵션 A 또는 B 권장**. 평가자가 코드와 문서를 함께 볼 때 일관성 중요.

---

## 7. Phase 1 `Course.decreaseCount` 의미 모호

### 충돌 지점

- **Phase 1 (line 60)**: `Course` 엔티티 산출물에 `increaseCount`·`decreaseCount` 포함
- **Tech-spec §5.4 (line 475)**: `courseRepository.decreaseCurrentCount(enrollment.getCourseId())` — Repository 메서드 호출
- **Phase 10 (line 167)**: "`course.decreaseCount`" — 엔티티 메서드처럼 표기

엔티티 메서드인가, Repository 메서드인가? 둘 다 필요한가?

### 두 접근 비교

| 접근 | 흐름 | 장점 | 단점 |
|---|---|---|---|
| 엔티티 메서드 (`course.decreaseCount()`) | `findById` → 엔티티 메서드 호출 → JPA dirty checking이 UPDATE 발급 | 도메인 객체 표현력. 비즈니스 규칙(예: 0 미만 불가)을 객체에 캡슐화 | SELECT + UPDATE 2회 쿼리. 동시성 시 lost update 위험 (`@Version` 필요) |
| Repository 원자적 UPDATE (`decreaseCurrentCount`) | `UPDATE course SET current_count = current_count - 1 WHERE id = ? AND current_count > 0` 단발 | 1회 쿼리. 원자적. 동시성 안전 | 도메인 규칙이 SQL로 새어 나감 |

### 현재 시스템 일관성

Phase 1에서 `tryIncreaseCurrentCount`는 **이미 Repository 원자적 UPDATE 방식** 채택 (Tech-spec §4.2.3). 감소도 같은 방식으로 통일하는 것이 자연스러움.

### 보정 방향

**Phase 1 산출물 표 수정**:
- `Course` 엔티티에서 `decreaseCount` 제거. `increaseCount`도 Repository로 일원화.
- `CourseRepository`에 다음 두 `@Query` 명시:
  - `tryIncreaseCurrentCount(courseId)` — `WHERE status='OPEN' AND current_count < max_capacity`
  - `decreaseCurrentCount(courseId)` — `WHERE current_count > 0`
- `Course` 엔티티는 `getMaxCapacity`, `getCurrentCount`, `transitionTo`, `verifyCreator`만 보유. 카운트 변경은 Repository를 통해.

**Phase 10 문구 수정**:
- "`course.decreaseCount`" → "`courseRepository.decreaseCurrentCount`"

**Phase 1 Repository 테스트 케이스**:
- `decreaseCurrentCount`: current_count > 0 정상 감소, current_count == 0 영향 0건

---

## 종합 우선순위

| # | 이슈 | 영향 범위 | 긴급도 |
|---|---|---|---|
| 1 | `seatCounter.initialize` 트랜잭션 경계 | 아키텍처 핵심 | 높음 (구현 직전 정리 필요) |
| 2 | Clock 주입 Phase 배치 | Phase 10 실행 가능성 | 높음 |
| 3 | PaymentStatus 전이 매핑 | Phase 8/10 결합 | 중간 |
| 4 | 멱등키 헤더명 결정 | Phase 9 API 계약 | 중간 |
| 7 | `decreaseCount` 위치 | Phase 1 산출물 정의 | 중간 |
| 5 | PRD §3.3 ↔ §8.1 권한 모순 | PRD 문서 정합성 | 낮음 (plan은 합리적 선택) |
| 6 | `CourseCancelFacade` 잔존 | Tech-spec/CLAUDE.md 문서 정합성 | 낮음 |

1·2·3·4·7은 구현 시작 전 결정 박아두기 권장. 5·6은 문서 정리 작업.
