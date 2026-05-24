# Handoff — 다음 세션 인계 문서

작성일: 2026-05-23 (Phase 10 완료 시점 갱신)

## 진행 상황 요약

`docs/implement-plan.md` 12-phase 중 **Phase 0~10 완료**. 다음은 **Phase 11** (`GET /api/courses/{id}/enrollments` 크리에이터 전용).

### 커밋 히스토리 (최근)

```
c9d96a8 feat(phase-10): 수강 신청 취소 + Controller→Facade 단방향 정리
3b2f4bc feat(phase-9): 결제 API POST /api/enrollments/{id}/payment + 멱등키 + saga
08f0446 feat(phase-8): Payment 도메인 + Mock 결제 게이트웨이
e7b0e71 feat(phase-7): 내 수강 신청 목록 조회 GET /api/me/enrollments
a3a0ce4 feat(phase-6): 수강 신청 POST API + saga 보상 + 동시성 E2E
491c8f9 feat(phase-5): Enrollment 도메인·EnrollmentStatus·InMemoryCourseWaitlist
1da4d07 feat(phase-4): 강의 상태 전이 PATCH API
c2c1fd8 feat(phase-3): 강의 목록·상세 조회
7908986 feat(phase-2): 강의 등록 API (POST /api/courses)
026b6fc feat(phase-1): Course 도메인·CourseStatus·InMemoryCourseSeatCounter
417266d chore(phase-0): 공통 예외·헤더·응답 인프라
e3babed chore: 프로젝트 초기 셋업
```

브랜치: `main`.

---

## 작업 워크플로우 (필수)

1. **한 phase = 한 커밋**. phase 종료 조건(테스트 그린) 충족 후에만 커밋.
2. **커밋 전 사용자 검토 필수**. 변경 요약·diff 제시 후 명시적 승인.
3. **커밋 직후 작업 중단**. 다음 phase 자동 진행 금지.
4. `docs/`는 `.gitignore` 처리됨. 커밋 대상 아님.
5. 커밋 메시지 한국어, prefix `feat(phase-N):` / `chore(phase-N):`.
6. 외부 API 호출은 트랜잭션 외부. `@Transactional` 내부 결제 Mock 호출 금지.

---

## 사용자 결정 사항 (누적)

### 인프라
- **Flyway 미도입**. Hibernate `ddl-auto` (local: update, test: create-drop).
- **RestAssured 의존성 추가 완료** (Phase 0).
- **브랜치명**: `main`.
- **CHECK 제약 미도입**. 도메인 로직 + 원자적 UPDATE WHERE가 방어선.
- **`@DisplayName` 필수**. 한글 시나리오 서술 (`.claude/rules/test-strategy.md`).
- **테스트 `@Transactional` 금지**. DB 초기화는 `DatabaseCleaner` JdbcTemplate TRUNCATE.
- **Lombok**. Service/Controller `@RequiredArgsConstructor`. DTO record.
- **CAVEMAN MODE 활성** (full). 코드·커밋 일반체, 응답은 caveman.

### Phase 6 결정 (saga·대기열)
- **Saga 보상 깊이**: 단순 release만. DB 실패 시 `seatCounter.release(courseId)` + 예외 전파. 대기열 자동 승급 미구현 (PRD §2.3 정신).
- **카운터 miss 통합**: DRAFT/CLOSED/미존재 모두 409 `CourseNotOpenException`. 404 분리 없음.
- **응답 DTO**: 단일 `EnrollmentResponse` + nullable (`@JsonInclude(NON_NULL)`). status로 분기.
- **대기열 Phase 6 시점은 write-only**: 자동 승급 없음. Phase 10에서 도입.

### Phase 10 결정 (취소 saga·역전이·CAS·계층 정리)
- **`EnrollmentStatus` 상태머신 확장**: `CANCELLED → CONFIRMED` 역전이 허용 (saga revert 전용). PRD §5와 차이 — README 한계로 명시 권장.
- **`Enrollment.revertCancel()`**: status CONFIRMED 복귀 + `cancelledAt = null`. `confirmedAt` 보존.
- **`EnrollmentService.revertCancel(enrollmentId)`**: 엔티티 dirty 변경 후 `courseRepository.tryIncreaseCurrentCount`. 순서 중요 (`clearAutomatically=true`로 PC clear 전에 dirty 변경 필요).
- **`EnrollmentFacade.cancelEnrollment` saga 순서**:
  1. `enrollmentService.cancel` (DB tx — enrollment CANCELLED + currentCount -1)
  2. `cancelPaymentIfSuccess`: PG cancel 실패 시 → `enrollmentService.revertCancel` 호출, 다시 throw. revert 자체 실패 시 CRITICAL log.
  3. 성공 후 `seatCounter.release` → `promoteFromWaitlist`
  - **포인트**: `seatCounter.release`는 payment 성공 후에만. payment 실패 catch에서 인메모리 카운터 안 건드려 oversold 방지.
- **`cancelPaymentIfSuccess` invariant 강제**: CONFIRMED enrollment ⟹ SUCCESS payment 존재. 없으면 `IllegalStateException`. 무료 강의 미허용 가정.
- **`promoteFromWaitlist` 보상 일관화**: `waitlist.pollNext` → `seatCounter.acquire` (실패 시 log+return) → `enrollmentService.createEnrollment` (실패 시 `seatCounter.release` + log). 각 단계 실패 보상 명시.
- **`InMemoryCourseSeatCounter` tryAcquire/acquire CAS 루프**: `decrementAndGet < 0 → incrementAndGet` 보상 패턴 폐기. `compareAndSet` 사전 검사로 음수 윈도우 제거.
- **Controller → Facade 단방향**: `CourseController`·`EnrollmentController`에서 Service 직접 주입 제거. `CourseFacade.create/list/detail` + `EnrollmentFacade.listForUser` thin passthrough 추가. `CourseStatusChangeResult` 타입 참조는 잔존(Component 의존 아님).
- **Clock 빈 도입**: `ClockConfig` + 테스트는 `MutableClock` + `TestClockConfig`로 시간 조작.
- **DTO 위치**: `CancelledEnrollment` 는 `com.liveklass.assignment.dto` 패키지로 격리 (Service/Facade 공용).
- **남은 한계 (Phase 12 / outbox 가능성)**:
  - revertCancel 자체 실패 시 (course CLOSED, 다른 사용자가 정원 채움) → CRITICAL log + 수동 처리. 자동 복구 없음.
  - PG cancel 멱등성 보장 안 됨 — 재호출 안전성은 게이트웨이 구현에 위임.

### Phase 9 결정 (결제 saga)
- **트랜잭션 통합**: `EnrollPaymentService.confirmAndPreparePayment`로 `Enrollment.confirm` + `Payment` INSERT를 단일 `@Transactional`로 묶음. UNIQUE 위반/검증 실패 시 tx auto-rollback → Enrollment도 PENDING 복원. 별도 보상 코드 불필요.
- **`PaymentPreparedInfo` record DTO**: `paymentId` + `amount`. Facade와 Service 간 결과 전달.
- **`MockPaymentGateway` 단순화**: `setNext*`/`Outcome` 상태 모두 제거. `charge`는 `Thread.sleep(2000ms)` + UUID 반환만. 실패 시나리오는 테스트에서 `@MockBean PaymentGateway` + Mockito stub으로 제어.
- **`markSuccess` 실패 보정 보류**: 현 코드는 `enrollmentService.rollbackToPending` + rethrow만 수행. 게이트웨이 환불·`markFailed` 보정은 Phase 10에서 `PaymentGateway.cancel` API 도입 시 추가 (`implement-plan.md §12 "Phase 9 잔여 보정 항목"` 참조).
- **Idempotency-Key**: 헤더 `@RequestHeader("Idempotency-Key") @NotBlank`. DB UNIQUE 단독. 메모리 캐시 미도입.
- **HTTP 매핑**: 성공 200, 게이트웨이 실패 502 `PAYMENT_GATEWAY_FAILURE`, 중복 키 409 `DUPLICATE_PAYMENT`, 타인 403, 미존재 404, 헤더 누락 400.
- **`@Transactional(readOnly = true)` 클래스 레벨 + 쓰기 메서드 `@Transactional` override 패턴**: `PaymentService`에 적용.
- **`Enrollment.validateEqualsClassmateId(userId)`** 도메인 메서드로 본인 검증 캡슐화.

---

## 테스트 인프라

```
support/
├── MySqlIntegrationSupport.java   # Testcontainers MySQL + DatabaseCleaner + seatCounter·waitlist clearAll @BeforeEach
├── AbstractIntegrationTest.java   # @SpringBootTest(RANDOM_PORT)
├── AbstractRepositoryTest.java    # @DataJpaTest + JdbcTemplate
└── DatabaseCleaner.java
```

### 핵심 규칙
- E2E: RestAssured + `AbstractIntegrationTest` + `@LocalServerPort`
- Service 통합: `AbstractIntegrationTest` + Service/Facade 직접 호출
- Repository: `AbstractRepositoryTest` + 직접 작성 쿼리만 검증
- 도메인 단위: POJO

### 외부 시스템 Mock 패턴 (Phase 9 신규)
- `PaymentGateway`는 외부 시스템 — 테스트에서 `@MockBean PaymentGateway` 허용.
- `MockPaymentGateway` 프로덕션 구현은 `Thread.sleep(2s) + UUID`만 — 테스트에선 `@MockBean`이 빈 교체 → sleep 미실행.
- 성공: `given(paymentGateway.charge(anyLong(), anyInt())).willReturn("ext-key")`
- 실패: `given(...).willThrow(new PaymentGatewayException("reason"))`
- 재시도 체이닝: `.willThrow(...).willReturn(...)`

### `@Modifying @Query` 패턴
```java
@Transactional
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Course c SET ... WHERE ...")
int xxx(@Param("courseId") Long courseId);
```

### 동시성 테스트 패턴
`CountDownLatch start/done` + `ExecutorService` 32 thread pool. `EnrollmentE2ETest.concurrent_enrollment_does_not_exceed_capacity` 참고 (100 thread, capacity 10).

---

## 현재 구현된 코드 트리 (Phase 10 완료 시점)

Phase 10에서 추가/변경된 핵심 파일:
- `api/EnrollmentController.java` — `POST /api/enrollments/{id}/cancel`
- `api/dto/EnrollmentCancelResponse.java`
- `common/time/ClockConfig.java` — `Clock` 빈
- `domain/enrollment/Enrollment.java` — `revertCancel()`
- `domain/enrollment/EnrollmentStatus.java` — `CANCELLED → CONFIRMED` 허용
- `domain/course/InMemoryCourseSeatCounter.java` — CAS 루프
- `domain/payment/PaymentGateway.java` + `MockPaymentGateway.java` — `cancel(externalPaymentKey)`
- `dto/CancelledEnrollment.java` — record (enrollmentId, courseId)
- `facade/EnrollmentFacade.java` — saga revert + promote 보상 + listForUser passthrough
- `facade/CourseFacade.java` — create/list/detail passthrough
- `service/EnrollmentService.java` — `cancel`, `revertCancel`
- `repository/PaymentRepository.java` — `findSuccessByEnrollmentId` (추가됐다면)
- 테스트: `e2e/CancelEnrollmentE2ETest.java`, `service/EnrollmentCancelServiceTest.java`, `facade/EnrollmentCancelFacadeTest.java`, `domain/enrollment/EnrollmentTest.java`에 `revertCancel` 케이스 2개
- 테스트 인프라: `support/MutableClock.java`, `support/TestClockConfig.java`

### 기존 트리 (Phase 9 시점) — 참고용

### main
```
com.liveklass.assignment
├── AssignmentApplication.java
├── api
│   ├── CourseController.java
│   ├── PaymentController.java                  # POST /api/enrollments/{id}/payment
│   └── dto
│       ├── CreateCourseRequest.java
│       ├── CourseResponse.java
│       ├── ChangeCourseStatusRequest.java
│       ├── CourseStatusChangeResponse.java
│       ├── EnrollmentResponse.java
│       └── PaymentResponse.java                # paymentId/status/enrollmentStatus
├── common/...
├── domain
│   ├── course/
│   ├── enrollment/                             # +validateEqualsClassmateId
│   ├── payment
│   │   ├── Payment.java                        # +amount 필드
│   │   ├── PaymentStatus.java
│   │   ├── MockPaymentGateway.java             # sleep 2s + UUID
│   │   ├── PaymentGateway.java                 # String charge(...)
│   │   ├── PaymentGatewayException.java
│   │   ├── PaymentFailedException.java
│   │   └── DuplicatePaymentException.java
│   └── waitlist/
├── dto
│   └── PaymentPreparedInfo.java                # record(paymentId, amount)
├── facade
│   ├── CourseFacade.java
│   ├── EnrollmentFacade.java
│   └── PaymentFacade.java                      # pay: prepare → gateway → markSuccess/Failed
├── repository
│   ├── CourseRepository.java
│   ├── EnrollmentRepository.java
│   └── PaymentRepository.java
└── service
    ├── CourseService.java
    ├── CourseQueryService.java
    ├── CourseStatusChangeResult.java
    ├── EnrollmentService.java                  # +rollbackToPending
    ├── EnrollmentQueryService.java
    ├── EnrollPaymentService.java               # tx#1 통합 (confirm + payment INSERT)
    └── PaymentService.java                     # markSuccess/markFailed, @Transactional(readOnly=true)
```

### test (4계층 모두 그린)
```
e2e/
├── PingE2ETest, CourseCreateE2ETest, CourseListE2ETest, CourseDetailE2ETest
├── CourseStatusE2ETest
├── EnrollmentE2ETest
└── PaymentE2ETest                              # @MockBean PaymentGateway
service/
├── CourseServiceTest, CourseQueryServiceTest
├── EnrollmentServiceTest, EnrollmentQueryServiceTest
├── EnrollPaymentServiceTest                    # 5 케이스
└── PaymentServiceTest                          # markSuccess/markFailed only
facade/
├── CourseFacadeTest
├── EnrollmentFacadeTest
└── PaymentFacadeTest                           # @MockBean PaymentGateway
repository/{CourseRepositoryTest, PaymentRepositoryTest}
domain/{course,enrollment,payment,waitlist}/*Test
common/exception/ErrorCodeTest
```

전체 `./gradlew test` **BUILD SUCCESSFUL**.

---

## 다음 작업 — Phase 11: `GET /api/courses/{id}/enrollments` (크리에이터 전용)

`docs/implement-plan.md` §13.

### 산출물
- `EnrollmentQueryService.listForCourse(courseId, requesterId, page, size)` — 소유자(`creator_id == requesterId`) 검증 + 페이지네이션.
- `CourseController` 또는 새 `EnrollmentController` 메서드 — `GET /api/courses/{courseId}/enrollments?page=&size=`.
- DTO: 기존 `EnrollmentListItemResponse` 재사용 가능. 크리에이터 시점이라 `classmateId` 노출 필요 — 신규 DTO 검토.

### 인가
- `X-User-Id` 헤더 = `course.creator_id` 검증. 불일치 시 403 `UnauthorizedException`.
- Course 미존재 시 404 `CourseNotFoundException`.

### 테스트 (4계층)
- 도메인 단위: 필요 없음 (단순 query).
- Repository: 페이지네이션·정렬 쿼리 검증 (직접 작성 시).
- Service 통합:
  - 정상 크리에이터 요청 → enrollment 목록 반환.
  - 비-크리에이터 → 403.
  - Course 미존재 → 404.
  - 페이지네이션 (size=20 기본, 100 max).
- E2E:
  - 200 정상.
  - 403 비-크리에이터.
  - 404 course 미존재.

### 커밋 메시지
```
feat(phase-11): 강의별 수강 신청 목록 GET API (크리에이터 전용)
```

---

## 후속 Phase 요약

- Phase 12: Hardening, README, 동시성 매트릭스, 재기동 복원 (`OPEN` 강의 `seatCounter.initialize`).

---

## 주의 사항

- **PRD §2.3 범위 외 절대 구현 금지**: PENDING TTL, 중복 신청 검증, CLOSED→OPEN 자동 승급, 강의 취소(크리에이터), 실제 결제, JWT, 멱등키 메모리 캐시.
- **도메인 네이밍**: `Class` 금지. `Course`/`Enrollment`/`Classmate`/`Payment`/`Waitlist`.
- **트랜잭션 경계**: Facade 외부, Service 내부. 외부 API/인메모리 조작은 Facade에서.
- 모든 테스트 메서드에 `@DisplayName("한글 시나리오")` 필수.
- Phase 6 EnrollmentE2ETest의 100스레드 동시성 테스트는 무거움(60s timeout).
- `PaymentStatus` 직접 전이 규칙: `PENDING → SUCCESS / FAILED`, `SUCCESS → CANCELLED` 만 허용. `PENDING → CANCELLED` 금지.
