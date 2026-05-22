# CLAUDE.md

@.claude/rules/test-strategy.md

LiveKlass BE 과제 A — 수강 신청 시스템. Claude Code가 작업할 때 참고할 프로젝트 컨텍스트.

## 문서 우선순위

1. `docs/assingment.md` — 원본 과제 요구사항 (변경 금지, 출처 보존)
2. `docs/PRD.md` — 요구사항 해석·범위·상태 머신·유스케이스
3. `docs/Tech-spec.md` — 아키텍처·동시성 설계·코드 스케치·테이블 정의

작업 전 PRD §2.2(명세 공백 결정), §2.3(범위 외)과 Tech-spec §4(동시성), §6(트랜잭션 경계) 필독.

## 도메인 네이밍 규칙

| 개념 | 코드 이름 | 비고 |
|---|---|---|
| 강의 | `Course` | **`Class`는 Java 예약어로 금지**. 엔티티/테이블/패키지/URL(`/api/courses`) 모두 통일 |
| 수강 신청 | `Enrollment` | |
| 수강생 | `Classmate` / `classmate_id` | `class` 단어 단독이 아니므로 허용 |
| 크리에이터 | `creator_id` | 별도 role 필드 없음 |
| 결제 | `Payment` | |
| 대기열 | `Waitlist` | 인메모리 |

신규 식별자 생성 시 위 표 준수. `Class`·`classId`·`classRepository` 등 출현 금지.

## 기술 스택

- Java 21, Spring Boot 3.3.5
- Spring Data JPA, Bean Validation
- MySQL 8 (로컬: docker compose / 테스트: Testcontainers)
- Lombok
- JUnit 5

## 실행·빌드·테스트

```bash
docker compose up -d        # MySQL 기동
./gradlew bootRun           # 앱 부팅
./gradlew test              # Testcontainers 통합 테스트
./gradlew clean build       # 전체 빌드
```

테스트 베이스 클래스: `src/test/java/com/liveklass/assignment/support/AbstractIntegrationTest.java`.

## 아키텍처 핵심

**Facade ↔ Service 2-layer** (Tech-spec §2.2).

- Facade: 트랜잭션 **외부**. 인메모리 카운터(`AtomicInteger`)·대기열(`ConcurrentLinkedQueue`)·보상 로직.
- Service: `@Transactional` 내부. DB 원자적 UPDATE + INSERT만.

흐름별 트랜잭션 경계는 Tech-spec §6.1~6.3 다이어그램 참고 (§6.4 강의 취소는 범위 외). 외부 결제 Mock 호출 및 인메모리 카운터/대기열 조작은 항상 트랜잭션 **외부**.

## 동시성 설계 (Tech-spec §4)

채택안: **인메모리 AtomicInteger + DB 원자적 UPDATE** (Appendix A Option 4).

- 정원 만석은 인메모리에서 fast-fail → 대기열로 라우팅.
- DB UPDATE: `WHERE status='OPEN' AND current_count < max_capacity`.
- DB CHECK 제약 `current_count <= max_capacity`이 마지막 방어선.
- 실패 시 보상: 대기열 다음 사람 승급 또는 카운터 원복.

동시성 테스트는 `CountDownLatch` 기반 필수 (Tech-spec §10.2 예제).

## 상태 머신 (PRD §5)

- `CourseStatus`: DRAFT → OPEN ↔ CLOSED, DRAFT → CLOSED
- `EnrollmentStatus`: PENDING ↔ CONFIRMED → CANCELLED (PENDING → CANCELLED 직접 전이 **금지**)
- `PaymentStatus`: PENDING → SUCCESS / FAILED, SUCCESS → CANCELLED (PENDING → CANCELLED 직접 전이 **금지**: Payment row는 결제 시도 시점 생성, 트리거 부재)

전이 검증은 enum의 `verifyTransitionTo()` + 도메인 객체 메서드 (Tech-spec §7).

## 인증·인가

- `X-User-Id` 헤더로 식별. JWT/OAuth 없음.
- `creator_id == X-User-Id` 검증 대상: **강의 상태 변경, 강의별 수강생 목록 조회**.
- 강의 목록·상세 조회, 본인 수강 신청 목록 조회는 전체 허용.

## 정원·기간 규칙

- PENDING + CONFIRMED 모두 정원 점유 (PRD §2.2)
- 취소 가능: CONFIRMED 시점부터 **7일 이내**
- 페이지네이션: offset/limit, 기본 size 20 / 최대 100

## 범위 외 (PRD §2.3) — 구현 금지

- PENDING TTL (자동 취소)
- 동일 사용자 중복 신청 검증
- CLOSED → OPEN 재전이 시 대기열 자동 승급
- 크리에이터의 강의 취소 (전체 수강생 일괄 환불·다단계 보상 트랜잭션). `CourseCancelFacade` 미구현. 크리에이터는 CLOSED 전이까지만 가능
- 실제 결제 게이트웨이
- 본격 인증 시스템
- 멱등키 메모리 캐시 (DB UNIQUE 인덱스 단독)

위 항목은 README "미구현/제약사항"에 명시되어야 함.

## 패키지 구조 (Tech-spec §2.3)

```
com.liveklass.assignment
├── api          # Controller
├── facade       # CourseFacade, EnrollmentFacade, PaymentFacade
├── domain
│   ├── course
│   ├── enrollment
│   ├── payment
│   └── waitlist
├── service
├── repository
└── common       # 예외, 헤더 파싱, 공통 응답
```

## 예외·HTTP 매핑 (Tech-spec §9)

`BusinessException` 하위로 도메인별 계층화. 404(NotFound) / 409(상태 전이·정원·취소 기간) / 403(권한) / 400(검증) / 500(그 외).

## 작업 가이드

- **신규 기능 추가 전** PRD §2.3 "범위 외" 확인. 범위 외면 사용자에게 확인.
- **상태 전이 추가 시** PRD §5의 전이표 + Tech-spec §7의 enum 매핑 동시 갱신.
- **API 추가 시** PRD §8 목록과 일치 유지.
- **DB 컬럼 추가 시** Tech-spec §3.2 테이블 정의 동시 갱신.
- **동시성 영향 코드 수정 시** `concurrent_enrollment_does_not_exceed_capacity` 패턴의 테스트 추가/갱신.
- **외부 API 호출은 트랜잭션 외부**. `@Transactional` 메서드 내부에서 결제 Mock 호출 금지.

## 커밋·PR

- 한국어 메시지
- 작은 단위 커밋, 도메인별 분리 권장

## Phase 작업 워크플로우 (필수)

`docs/implement-plan.md` 기반 작업 시:

1. **한 phase = 한 커밋**. phase 종료 조건(4계층 테스트 그린) 충족.
2. **커밋 전 사용자 검토 필수**. 변경 요약·diff 요약 제시 후 명시적 승인 받고 커밋. 자동 커밋 금지.
3. **커밋 직후 작업 중단**. 다음 phase로 자동 진행 금지.
4. 사용자에게 검토 요청 후 승인 받고 다음 phase 진행.
5. `docs/`는 `.gitignore` 처리됨. 커밋 대상 아님.

## AI 활용 기록

README의 "AI 활용 범위" 섹션 갱신은 사용자 책임. Claude는 변경 사항 요약만 제공.
