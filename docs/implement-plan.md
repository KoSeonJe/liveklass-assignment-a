# Implementation Plan: 수강 신청 시스템

| 항목 | 내용 |
|---|---|
| 문서 버전 | 1.0 |
| 작성일 | 2026-05-22 |
| 작성자 | KoSeonJe |
| 관련 문서 | `PRD.md`, `Tech-spec.md`, `.claude/rules/test-strategy.md` |

---

## 0. 문서 목적

PRD와 Tech-spec에 정의된 시스템을 **실행 가능한 phase 단위**로 분해한 구현 계획. 각 phase는 다음을 만족한다.

- **단일 책임**: 하나의 API 또는 도메인 단위 산출물만 다룬다.
- **검증 가능**: phase 종료 시점에 해당 부분이 동작하고 4계층 테스트가 그린 상태.
- **PR 단위**: 한 phase = 한 커밋/PR 권장.

테스트 전략은 `.claude/rules/test-strategy.md` 4계층 (도메인 단위 / Repository / Service 통합 / E2E) 규칙을 따른다.

---

## 1. 의존성 그래프

```
0 → 1 → 2 → 3 → 4 ─┐
            │      ├→ 6 → 7 ─┐
            └→ 5 ──┘           ├→ 9 → 10 → 11 → 12
                   8 ──────────┘
```

- Phase 3·4는 Phase 1 완료 후 병렬 가능
- Phase 8 (Payment 기반)은 Phase 5와 병렬 가능

---

## 2. Phase 0 — Foundation (선행)

공통 인프라. 이후 모든 phase가 의존.

| 산출물 | 위치 | 테스트 계층 |
|---|---|---|
| `AbstractIntegrationTest` Testcontainers 베이스 | `test/.../support` | — (이미 존재, 검증만) |
| `BusinessException` + 도메인별 예외 계층 (Tech-spec §9.1) | `common/exception` | 도메인 단위 (전이/검증 예외 발생) |
| `GlobalExceptionHandler` (HTTP 매핑 §9.2) | `common/exception` | E2E (400/403/404/409/502) |
| `X-User-Id` 헤더 리졸버 (`@RequestHeader` 래퍼) | `common/web` | E2E (헤더 누락 시 400) |
| 공통 응답 DTO·페이지 응답 형식 | `common/web` | — |

**산출 확인**: 빈 컨트롤러 띄워 헤더 누락 케이스 E2E 1개 통과.

---

## 3. Phase 1 — Course 도메인 기반

| 산출물 | 위치 | 테스트 |
|---|---|---|
| `CourseStatus` enum + `verifyTransitionTo` (Tech-spec §7.1) | `domain/course` | 도메인 단위: 허용 전이 4건 + 불허 전이 다수 |
| `Course` 엔티티 (필드·`transitionTo`·`verifyCreator`). 카운트 변경은 Repository 책임 (동시성 안전 원자적 UPDATE) | `domain/course` | 도메인 단위 |
| `CourseRepository` + `tryIncreaseCurrentCount` `@Query` (`WHERE status='OPEN' AND current_count < max_capacity`) + `decreaseCurrentCount` `@Query` (`WHERE current_count > 0`) | `repository` | Repository 테스트: 증가 — 만석·CLOSED·정상 영향 행 수 검증. 감소 — `current_count > 0` 정상, `== 0` 영향 0건 |
| `InMemoryCourseSeatCounter` | `domain/course` | 도메인 단위(동시성): `tryAcquire` N-스레드, `release`/`initialize`/`remove` |
| DB 마이그레이션 `course` 테이블 (Tech-spec §3.2) + CHECK 제약 | `resources/db/migration` (Flyway 도입) | Repository 테스트로 제약 위반 검증 |

---

## 4. Phase 2 — `POST /api/courses` (강의 등록)

| 산출물 | 테스트 |
|---|---|
| `CreateCourseRequest` DTO + Bean Validation | — |
| `CourseService.create` (`@Transactional`, DRAFT 생성) | Service 통합: DRAFT 생성·`creator_id` 기록 |
| `CourseController.create` | **E2E**: 201 + Location 헤더 + body, Validation 400, 헤더 누락 400 |

**검증 포인트**: 응답 status 201, `status="DRAFT"`.

---

## 5. Phase 3 — `GET /api/courses`, `GET /api/courses/{id}`

| 산출물 | 테스트 |
|---|---|
| `CourseQueryService.list(page, size)` | Service 통합: 페이지네이션 경계(size>100 = 100 cap, size<1 = 1 정규화) |
| `CourseQueryService.detail(id)` | Service 통합: `currentCount` 포함 검증 |
| Controller + 응답 DTO | **E2E**: 목록 200·페이지 메타, 상세 200, 404 |

**규칙**: 페이지네이션 기본 size 20 / max 100 (PRD §9.3).

---

## 6. Phase 4 — `PATCH /api/courses/{id}/status`

| 산출물 | 테스트 |
|---|---|
| `ChangeCourseStatusRequest` (target status) | — |
| `CourseService.changeStatus` (`@Transactional`): owner 검증 → `transitionTo` → 전이 결과(`courseId`, 신규 status, `remaining`) 반환. DB 조작만 수행, 인메모리 자원 조작 없음 | Service 통합: 각 전이 (DRAFT→OPEN, OPEN→CLOSED, CLOSED→OPEN, DRAFT→CLOSED) + 불허 전이 예외 + 비-크리에이터 403 |
| `CourseFacade.changeStatus`: Service 호출 (커밋) → 반환된 전이 결과에 따라 OPEN 진입 시 `seatCounter.initialize`, CLOSED 진입 시 `seatCounter.remove` + `waitlist.clear` 호출. 모든 인메모리 조작은 트랜잭션 외부에서 수행 | Service 통합: 커밋 성공 후 카운터 켜짐/꺼짐 확인, Service 예외(롤백) 시 카운터 미변경 확인 |
| Controller | **E2E**: 200, 403, 409 (불허 전이) |

**검증 포인트**: OPEN 진입 후 `seatCounter.remaining` = `maxCapacity - currentCount`.

---

## 7. Phase 5 — Enrollment 도메인 기반

| 산출물 | 테스트 |
|---|---|
| `EnrollmentStatus` enum + transition (Tech-spec §7.1) | 도메인 단위 |
| `Enrollment` 엔티티 (`confirm`, `rollbackToPending`, `cancelByClassmate` 7일 검증) | 도메인 단위: 7일 경계, 권한 검증, 전이 불허 |
| `EnrollmentRepository` + 본인 목록·강의별 목록 쿼리 (직접 작성 쿼리) | Repository 테스트 |
| `InMemoryCourseWaitlist` | 도메인 단위(동시성): `enqueue`/`pollNext`/`clear`/`positionOf` 멀티스레드 |
| `EnrollmentResult` (pending/waitlisted sealed type) | — |
| `enrollment` 테이블 마이그레이션 | Repository 테스트 |

---

## 8. Phase 6 — `POST /api/courses/{id}/enrollments` (★ 핵심)

평가 핵심(동시성·상태 전이·트랜잭션 경계)이 모두 모이는 phase.

| 산출물 | 테스트 |
|---|---|
| `EnrollmentService.createEnrollment` (`@Transactional`, DB 원자적 UPDATE + INSERT, Tech-spec §4.2.3) | Service 통합: OPEN 정상, CLOSED 강의 시 `CourseNotOpenException`, 정원 마지막 자리 단일 흐름 |
| `EnrollmentFacade.enroll` (saga: `acquireSeatOrWaitlist` → `persistEnrollment` → `rollbackSeatAcquisition`) | Service 통합: 자리 확보 → PENDING, 만석 → 대기열, DB 강제 실패 시 보상 후 다음 대기자 승급 |
| Controller (201 vs 202 분기) | **E2E**: 201 Pending, 202 WAITLISTED + position, 409 CLOSED, 404 |
| **동시성 E2E** (`CountDownLatch` 100스레드, 정원 10) | **E2E**: 정확히 10명 PENDING + 90명 WAITLISTED + DB `current_count = 10` |

**검증 포인트**: PRD §9.1 비기능 요구사항 (정원 초과 0건).

---

## 9. Phase 7 — `GET /api/me/enrollments`

| 산출물 | 테스트 |
|---|---|
| `EnrollmentQueryService.listForUser(userId, page, size)` | Service 통합: 페이지네이션·소유자 필터 |
| Controller | **E2E**: 200, 페이지 메타 |

---

## 10. Phase 8 — Payment 도메인 + Mock 클라이언트

| 산출물 | 테스트 |
|---|---|
| `PaymentStatus` enum + `verifyTransitionTo`. `ALLOWED = { PENDING → {SUCCESS, FAILED}, SUCCESS → {CANCELLED} }`. `PENDING → CANCELLED`는 본 시스템 트리거 부재로 **불허** | 도메인 단위: `PENDING → SUCCESS/FAILED` 허용, `PENDING → CANCELLED` 불허, `SUCCESS → CANCELLED` 허용, `FAILED → CANCELLED` 불허, `CANCELLED → *` 불허 |
| `Payment` 엔티티 + 멱등키 UNIQUE | 도메인 단위 + Repository 테스트(UNIQUE 위반) |
| `PaymentGateway` 인터페이스 + `MockPaymentGateway` 구현 (성공/실패 결정 주입 가능) | 도메인 단위 |
| `payment` 테이블 마이그레이션 | Repository 테스트 |

---

## 11. Phase 9 — `POST /api/enrollments/{id}/payment`

결제 saga: tx#1 → 외부 API → tx#2 (Tech-spec §6.2).

**멱등키 입력**: 클라이언트가 `Idempotency-Key` 헤더로 전달 (필수, `VARCHAR(64)`). 누락 시 400. `payment.idempotency_key` UNIQUE 인덱스 위반 → `DuplicatePaymentException` → 409.

**저장 위치**: DB UNIQUE 단독. 메모리 캐시 미도입 (단일 인스턴스 + 평가 핵심 외 항목. README 미구현/제약사항에 명시).

| 산출물 | 테스트 |
|---|---|
| Controller 헤더 시그니처: `@RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey` | E2E: 헤더 누락 → 400 |
| `PaymentService` tx#1 (Enroll PENDING→CONFIRMED + Payment PENDING INSERT). UNIQUE 위반 catch → `DuplicatePaymentException` | Service 통합 |
| `PaymentService` tx#2 (Payment 결과 반영 + 실패 시 Enroll CONFIRMED→PENDING 롤백) | Service 통합 |
| `PaymentFacade.pay` (tx#1 → 외부 호출 → tx#2 분기) | Service 통합: 성공 흐름·실패 롤백·중복 멱등키 거부 |
| Controller | **E2E**: 200 성공 (CONFIRMED+SUCCESS), 502 실패 (PENDING으로 롤백 확인), 409 동일 키 재요청, 200 다른 키 재요청 정상 |

---

## 12. Phase 10 — `POST /api/enrollments/{id}/cancel` + 대기열 승급

**선행 산출물**: `Clock` 빈 도입 (`@Bean Clock clock() { return Clock.systemDefaultZone(); }`). Service에서 `LocalDateTime.now(clock)` 사용. E2E는 `@TestConfiguration` + `Clock.fixed`로 시간 조작.

| 산출물 | 테스트 |
|---|---|
| `Clock` 빈 + Service 시간 주입 | — |
| `EnrollmentService.cancel` (`@Transactional`: CANCELLED 전이 + `courseRepository.decreaseCurrentCount` 호출 + 7일 검증) | Service 통합: 정상, 7일 경과 409, 비-본인 403, PENDING 취소 거부 409 |
| `EnrollmentFacade.cancelEnrollment` (saga: tx → `seatCounter.release` → `promoteFromWaitlist` → `paymentFacade.cancel`) | Service 통합 |
| `promoteFromWaitlist` (Tech-spec §5.4) | Service 통합 |
| Controller | **E2E**: 200, 9일 후 취소 409 (`Clock.fixed`로 시간 조작) |
| **승급 E2E** | **E2E**: 정원 1 + 2명 신청 → 1번 PENDING, 2번 WAITLISTED → 1번 취소 → 2번 자동 PENDING 승급 |

### Phase 9 잔여 보정 항목 (Phase 10에서 처리)

Phase 9 시점 `PaymentFacade.pay`의 `markSuccess` 실패 catch 블록은 **Enrollment rollback + rethrow만** 수행. Payment row는 PENDING으로 잔존, 게이트웨이 환불 미수행. 이는 다음 조건 때문에 Phase 10으로 미룬다:

- `PaymentGateway.cancel(externalPaymentKey)` API 미존재 (Phase 10 cancel saga에서 도입)
- `PaymentStatus`는 `PENDING → CANCELLED` 직접 전이 금지 (`PENDING → FAILED`만 합법)
- `markSuccess` 실패 = DB 이슈 → 후속 DB 보정도 같은 이유로 실패 가능 → best-effort 패턴

**Phase 10 작업 시 추가할 보정**: `PaymentFacade.pay`의 `markSuccess` catch 블록에 다음 best-effort 보정 추가.

```java
try {
    paymentService.markSuccess(prepared.paymentId(), externalPaymentKey);
} catch (RuntimeException e) {
    try { paymentGateway.cancel(externalPaymentKey); } catch (RuntimeException ignored) {}
    try { paymentService.markFailed(prepared.paymentId()); } catch (RuntimeException ignored) {}
    try { enrollmentService.rollbackToPending(enrollmentId); } catch (RuntimeException ignored) {}
    throw e;
}
```

- `paymentGateway.cancel`: 이미 charge된 외부 결제 환불. Mock은 호출 흔적 기록.
- `markFailed`: PENDING→FAILED (PENDING→CANCELLED 금지).
- 모든 보정은 swallow, 원본 예외만 rethrow.
- 테스트(Service 통합): `markSuccess`를 강제 실패시키는 stub 사용. 실패 시 `paymentGateway.cancel` 호출됨 + Payment FAILED + Enrollment PENDING 검증.

---

## 13. Phase 11 — `GET /api/courses/{id}/enrollments` (크리에이터 전용)

| 산출물 | 테스트 |
|---|---|
| `EnrollmentQueryService.listForCourse(courseId, requesterId, page, size)` (소유자 검증) | Service 통합: 비-크리에이터 403 |
| Controller | E2E: 200, 403, 404 |

---

## 14. Phase 12 — Hardening + 문서

- **README** 작성 (`docs/assingment.md` 템플릿 충족): API 목록, ERD, 미구현/제약(대기열 휘발성, 멱등키 캐시 미도입, 강의 취소 미구현 등), AI 활용 범위, 실행 방법
- **재기동 복원 로직**: 부팅 시 OPEN 강의 전체 조회 → `seatCounter.initialize`. 대기열은 손실(제약사항 README 명시)
- **Flyway 마이그레이션 정리** 및 인덱스 검증
- **부하·동시성 보완 테스트** (Phase 6 보강): 다양한 정원·스레드 수 매트릭스

---

## 15. 작업 규칙

1. **phase 종료 조건**: 해당 phase의 4계층 테스트 전부 그린 상태. 다음 phase로 넘어가지 않는다.
2. **테스트 작성 순서**: 도메인 단위 → Repository → Service 통합 → E2E. 낮은 계층에서 검증 가능한 것은 거기서 끝낸다.
3. **신규 예외 추가 시** Tech-spec §9.2 표 동기화.
4. **신규 API 추가 시** PRD §8 표 동기화.
5. **동시성 영향 코드는 E2E 동시성 테스트 필수** (Tech-spec §10.2 패턴).
6. **한 phase = 한 PR/커밋 단위** (가능하면).
7. **외부 API 호출은 트랜잭션 외부**. `@Transactional` 메서드 내부에서 결제 Mock 호출 금지.
8. **PRD §2.3 범위 외 항목은 절대 구현하지 않는다**. 범위 추가는 PRD 갱신 후 별도 phase로.

---

## 16. 시간 추정

각 phase 30분 ~ 2시간. Phase 6·10이 가장 무거움 (saga·동시성 테스트).

전체 12 phase, 누적 약 12~18 시간 단위 작업.
