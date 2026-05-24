# LiveKlass BE 과제 A — 수강 신청 시스템

상태 전이 / 정원 관리 / 동시성 제어를 핵심으로 한 수강 신청 백엔드 시스템.

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택](#2-기술-스택)
3. [실행 방법](#3-실행-방법)
4. [요구사항 해석 및 가정](#4-요구사항-해석-및-가정)
5. [설계 결정과 이유](#5-설계-결정과-이유)
6. [부하·동시성 검증](#6-부하동시성-검증)
7. [API 목록 및 예시](#7-api-목록-및-예시)
8. [데이터 모델](#8-데이터-모델)
9. [미구현 / 제약사항](#9-미구현--제약사항)
10. [테스트 실행](#10-테스트-실행)
11. [AI 활용 범위](#11-ai-활용-범위)

---

## 1. 프로젝트 개요

LiveKlass BE 과제 A — 온라인 강의 수강 신청 서비스.

크리에이터가 강의를 만들어 공개하고, 수강생이 원하는 강의에 신청·결제하고, 필요하면 취소까지 할 수 있는 백엔드 시스템입니다.

### 주요 흐름

- **크리에이터**: 강의를 등록하고 공개/마감 상태를 직접 관리
- **수강생**: 공개된 강의에 신청 → 결제 → 필요 시 취소
- **정원이 꽉 찬 경우**: 대기열에 등록되고, 빈 자리가 생기면 순서대로 자동 승급
- **결제 완료 후 7일 이내**에는 수강생이 직접 취소 가능

## 2. 기술 스택

- Java 21, Spring Boot 3.3.5
- Spring Data JPA, Bean Validation
- MySQL 8 (로컬: docker compose / 테스트: Testcontainers)
- Lombok
- JUnit 5, RestAssured, Mockito

## 3. 실행 방법

```bash
docker compose up -d        # MySQL 기동
./gradlew bootRun           # 앱 부팅 (localhost:8080)
```

모든 요청에 `X-User-Id` 헤더 필수 (인증 단순화).

## 4. 요구사항 해석 및 가정

## 평가 핵심에 대한 해석

과제의 핵심 키워드인 **상태 전이 · 정원 관리 · 동시성 제어** 세 가지를 평가의 중심축으로 보고, 이를 깊이 있게 검증할 수 있는 구조에 자원을 집중했습니다. 프로덕션 운영에 필요하지만 평가 핵심과 직접 관련이 적은 항목은 의도적으로 범위에서 제외했습니다(아래 "의도적으로 제외한 항목" 참고).

| 평가 핵심 | 본 구현의 대응 방향 |
|---|---|
| **동시성 제어** | DB 락 직렬화를 피하고, 인메모리 `AtomicInteger` + DB 원자적 `UPDATE` 조합으로 마지막 한 자리 시나리오를 처리 |
| **상태 전이** | Course / Enrollment / Payment 세 도메인의 상태 머신을 Enum + 전이 매핑으로 명시적으로 모델링 |
| **트랜잭션 경계** | Facade-Service 2계층으로 인메모리 동시성 제어 / DB 트랜잭션 / 외부 API 호출 영역을 분리 |

## 인프라 가정

- **단일 애플리케이션 인스턴스**: 인메모리 동시성 제어가 유효함을 전제로 함
- **단일 데이터베이스 인스턴스**
- **외부 결제 시스템**: Mock API로 대체

## 명세 공백에 대한 결정

원본 요구사항이 명시하지 않았거나 해석이 갈릴 수 있는 사항에 대한 결정입니다.

### 1. 정원 카운팅 기준
- **결정**: `PENDING`과 `CONFIRMED` 모두 정원을 점유
- **근거**: 결제 전 신청도 자리 확보로 간주해야 정원 보장이 명확

### 2. 취소 가능 기간 기준일
- **결정**: `CONFIRMED` 전이 시점(결제 완료)부터 7일 이내
- **근거**: "결제 후 7일" 요구사항 충실 반영

### 3. 강의 상태 전이 트리거
- **결정**: 크리에이터의 명시적 요청 + 시스템 자동 전이(`DRAFT → OPEN`, `startDate` 도래 시 매일 00:00 KST). 그 외 자동 전이(`CLOSED → OPEN` 재개, `OPEN → CLOSED` 자동 마감 등)는 없음
- **근거**: 강의 시작일에 크리에이터가 수동으로 OPEN 전환하는 것은 비현실적이므로 해당 트리거만 자동화. 단일 인스턴스 가정 하 분산 락 미적용

### 4. 인증·인가
- **결정**: `X-User-Id` 헤더 기반 식별. 역할(Role) 필드는 두지 않고, 강의의 `creator_id`로 권한 검증
- **근거**: 요구사항에서 인증/인가 간략화를 명시적으로 허용

### 5. 페이지네이션
- **결정**: offset/limit (기본 size 20, 최대 100)
- **근거**: 가장 보편적인 방식. 커서 기반은 과제 범위 대비 과함

### 6. 결제 흐름 순서
- **결정**: 애플리케이션 내부 상태 전이 → 외부 결제 API 호출 순
- **근거**: 외부 결제 성공 후 내부 실패 시의 결제 취소 비용 회피

### 7. 결제 실패 시 처리
- **결정**: `Enrollment`를 `CONFIRMED → PENDING`으로 롤백
- **근거**: 정원은 유지하되 재결제 시도 여지를 남김

### 8. 대기열 저장 위치
- **결정**: 인메모리 자료구조 (DB 미사용)
- **근거**: 단일 인스턴스 가정 하에 충분. DB 락 경합 추가 회피

### 9. 대기열 승급 트리거
- **결정**: 수강 취소 / 결제 실패 보상 시점에 최선두 사용자 자동 승급
- **근거**: 정원 반환 사건에 자연스럽게 결합

### 10. 수강 신청 응답 코드
- **결정**: 자리 확보 시 `201 Created`, 대기열 등록 시 `202 Accepted`
- **근거**: 리소스 생성 여부에 따라 시맨틱하게 구분

## 의도적으로 제외한 항목

평가 핵심과의 관련성 대비 복잡도가 크다고 판단한 항목입니다. 시간 부족이 아닌 의도적 선택임을 밝힙니다.

| # | 항목 | 제외 이유 |
|---|---|---|
| 1 | `PENDING` 상태의 TTL (미결제 자동 취소) | 결제 흐름 단순화. 평가 핵심과 직접 관련 없음 |
| 2 | 동일 사용자의 동일 강의 중복 신청 방지 | 과제 범위 외로 판단 |
| 3 | `CLOSED → OPEN` 재전이 시 대기열 자동 승급 정책 | 엣지 케이스. 정책 결정이 평가 핵심과 무관 |
| 4 | 크리에이터의 강의 취소 (전체 수강생 일괄 환불 보상 트랜잭션) | 다단계 외부 결제 취소·보상 설계는 복잡도 대비 평가 핵심 기여도가 낮음. 크리에이터는 `CLOSED` 전이까지만 가능 |
| 5 | 실제 결제 게이트웨이 연동 | 요구사항에서 Mock 허용 |
| 6 | JWT/OAuth 등 본격적인 인증 시스템 | 요구사항에서 간략화 허용 |
| 7 | 강의 목록 조회의 상태/검색/정렬 필터 | 평가 핵심과 무관. 페이지네이션만 지원 |
| 8 | 대기 사용자에 대한 능동 통지 | 대기 사용자는 본인 신청 목록 조회로 상태 확인. 푸시/이메일 등 통지 인프라는 과제 범위 외 |

## 5. 설계 결정과 이유

### 5-1. 동시성 제어 — 4안 비교 후 Option 4 채택

#### 문제 정의

정확성 + 락 직렬화로 인한 스레드·커넥션 점유 최소화.

- Tomcat 워커 스레드는 응답까지 점유 → 락 대기 시간만큼 다른 요청 처리 불가.
- HikariCP 커넥션은 트랜잭션 동안 점유 → DB 락 대기 시간이 커넥션 풀 고갈로 전파.

#### 4안 비교 (스레드·커넥션 관점)

| 안 | 직렬화 위치 | 만석 응답 시간 (UX) | Tomcat 워커 스레드 점유 | HikariCP 커넥션 점유 |
|---|---|---|---|---|
| A.1 비관적 락 (`FOR UPDATE`) | DB row-lock | 락 대기 후 응답 | 길음 (락 대기 누적) | 락 대기 시간 전체 |
| A.2 atomic UPDATE 단독 | DB row-lock (UPDATE 시점) | DB 왕복 후 알림 | 중 (UPDATE 직렬화) | UPDATE 시점만 |
| A.3 앱락 + UPDATE | 앱 락 (강의별) | 앱 락 대기 누적 | 길음 (직렬 처리) | 짧음 (락 외부 INSERT) |
| **A.4 인메모리 카운터 + DB UPDATE** | **인메모리 (μs 단위)** | **즉시 거부** | **짧음 (만석 빠른 해제)** | **승자만 점유** |

> A.4의 인메모리 카운터는 `AtomicInteger`. 동시 차감 충돌은 **CAS(Compare-And-Swap)** CPU 명령으로 락 없이 원자적으로 해결. "정원 미달이면 1 증가"가 한 명령에 묶여 중간 끼어듦 없음.

#### 채택안 — Option 4 흐름

```
신청 요청
   │
   ▼
┌─ 인메모리 카운터 ──┐── 정원이 가득찼다면 ─▶ 대기열 등록 (202)
│  CAS 차감          │
└────────┬───────────┘
         ▼
┌─ DB atomic UPDATE ┐── fail ─▶ 보상 (인메모리 원복 / 대기자 승급)
│ WHERE count<max   │
└────────┬──────────┘
         ▼
  수강 신청 INSERT (PENDING) → 201
```

#### 정합성 문제와 해결 — 원자성 보장

정원 관리 지점이 인메모리·DB 두 곳으로 분리되어 발산 가능. **각 지점의 차감을 원자적으로 수행하고, 어긋나면 보상으로 되돌려** 두 지점을 하나의 논리적 트랜잭션처럼 묶음.

- **인메모리 차감**: 단일 CPU 명령으로 "정원 미달이면 1 증가"를 한 번에 수행. 중간에 끼어드는 요청 없음.
- **DB 차감**: "정원 미달일 때만 1 증가" 조건을 UPDATE 한 문장에 묶어 row-lock으로 직렬화. 동시 요청 중 하나만 성공.
- **실패 시 보상**: 인메모리는 통과했으나 DB가 0건 갱신이면 즉시 인메모리를 원복. 두 지점의 합을 다시 맞춤.

재기동 시 인메모리 값은 DB의 현재 정원으로 다시 채워 발산 방지.

### 5-2. 상태 머신

Enum의 전이 검증 메서드 + 도메인 객체의 전이 메서드 두 곳에서 전이 강제.

#### 강의 (Course)

```
            (크리에이터: 공개)            (크리에이터: 마감)
   DRAFT  ──────────────────▶   OPEN  ──────────────────▶   CLOSED
     │                            ▲                            │
     │                            │                            │
     │                            └────────────────────────────┘
     │                                 (크리에이터: 재공개)
     │
     │       (크리에이터: 즉시 마감)
     └────────────────────────────────────────────────────▶  CLOSED
```

| 전이 | 트리거 | 허용 | 비고 |
|---|---|---|---|
| DRAFT → OPEN | 크리에이터 공개 | ✅ | 신청 가능 진입 |
| DRAFT → CLOSED | 크리에이터 즉시 마감 | ✅ | 초안에서도 마감 가능 |
| OPEN → CLOSED | 크리에이터 마감 | ✅ | 신청 차단. 기존 신청 유지 |
| CLOSED → OPEN | 크리에이터 재공개 | ✅ | 신청 가능 복귀 |

**상태별 신청 가능 여부**: OPEN ✅ / DRAFT·CLOSED ❌.

#### 수강 신청 (Enrollment)

```
   PENDING  ──────────▶  CONFIRMED  ──────────▶  CANCELLED
      ▲                     │  ▲
      │                     │  │ (보상: 취소 후 결제 환불 실패 시 원복)
      │                     ▼  │
      └────────────────── PENDING
           (결제 실패 롤백)
```

| 전이 | 트리거 | 허용 | 비고 |
|---|---|---|---|
| (없음) → PENDING | 수강 신청 | ✅ | 신규 생성 |
| PENDING → CONFIRMED | 결제 시도 시 내부 전이 | ✅ | 외부 결제 API 호출 직전 |
| CONFIRMED → PENDING | 결제 실패 롤백 | ✅ | 외부 결제 응답 실패 |
| CONFIRMED → CANCELLED | 수강 취소 요청 | ✅ | 결제 완료 후 7일 이내 |
| CANCELLED → CONFIRMED | 취소 후 결제 환불 실패 시 보상 | ✅ | 내부 보상 전용. 외부 API 노출 없음 |
| PENDING → CANCELLED | — | ❌ | PENDING 직접 취소 경로 미정의 |

**CONFIRMED → PENDING 의도**: 결제 흐름은 "내부 로직 모두 성공 → 외부 결제 API 호출" 순서. 외부 결제 성공 후 내부 상태 전이 실패로 환불해야 하는 비용 회피. 결제 API 호출 시점엔 이미 CONFIRMED, 외부 결제 실패 시 PENDING 롤백.

#### 결제 (Payment)

```
   PENDING  ───▶  SUCCESS  ───▶  CANCELLED (수강 취소 시 환불)
      │
      └────────▶  FAILED
```

| 전이 | 트리거 | 허용 | 비고 |
|---|---|---|---|
| (없음) → PENDING | 결제 진행 트랜잭션: Payment 행 신규 생성 | ✅ | Payment row는 결제 시도 시점 생성 |
| PENDING → SUCCESS | 외부 결제 Mock 성공 | ✅ | |
| PENDING → FAILED | 외부 결제 Mock 실패 | ✅ | Enrollment CONFIRMED → PENDING 롤백 동반 |
| SUCCESS → CANCELLED | 수강 취소(7일 이내) 환불 | ✅ | |
| PENDING → CANCELLED | — | ❌ | 트리거 부재. PENDING TTL은 범위 외 |
| FAILED → CANCELLED | — | ❌ | 실패 결제 환불 의미 없음 |

### 5-3. 트랜잭션 경계 (Facade / Service 2-layer)

**계층 책임**

| 계층 | 책임 | 트랜잭션 |
|---|---|---|
| Facade | 인메모리 카운터·대기열 조작, 외부 API(결제) 호출, 보상 로직 | 외부 |
| Service | DB 원자적 UPDATE + INSERT만 | `@Transactional` 내부 |

**흐름별 경계**

| 흐름 | TX 외부 (Facade) | TX 내부 (Service) |
|---|---|---|
| 수강 신청 | 인메모리 정원 카운터 점유 / 대기열 등록 / 보상 시 카운터 반환 | Enrollment INSERT |
| 결제 | 외부 결제 호출 | Payment INSERT, Enrollment CONFIRMED |
| 취소 | 외부 결제 환불 호출 / 인메모리 정원 카운터 반환 / 대기열 1순위 승급 | Enrollment CANCELLED, Payment CANCELLED |

**이유**: 외부 I/O(결제 게이트웨이 응답 지연)와 인메모리 카운터·대기열 조작이 DB row-lock 보유 시간에 영향을 주지 않도록 TX 외부로 분리. row-lock 시간이 외부 응답시간에 묶이지 않음.

**결제 흐름 — 확정 → 외부 호출 → 실패 시 보상**

> 아래 도식의 `tx#`는 DB 트랜잭션 단위.

```
결제 요청
   │
   ▼
┌─ tx#1 (Service @Transactional) ────────────┐
│  Enrollment  PENDING → CONFIRMED            │
│  Payment     INSERT (PENDING)               │
└────────────────────┬────────────────────────┘
                     ▼
        외부 결제 호출  ← TX 외부
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
     실패                       성공
        │                         │
   ┌────┴─────────────────┐  ┌────┴──────────────┐
   │ 보상 tx#2 (단일 TX)  │  │ tx#3              │
   │ Payment → FAILED     │  │ Payment → SUCCESS │
   │ Enrollment           │  └────┬──────────────┘
   │   CONFIRMED          │       │
   │   → PENDING          │       ▼
   └────┬─────────────────┘  200 OK
        ▼
   402 PaymentFailed
```

**취소 흐름 — 내부 상태 전이 → 외부 환불 → 실패 시 보상 → 대기열 승급**

```
취소 요청
   │
   ▼
┌─ tx#1 (Service @Transactional) ────────────┐
│  Enrollment  CONFIRMED → CANCELLED          │
│  Course      current_count - 1              │
└────────────────────┬────────────────────────┘
                     ▼
        SUCCESS Payment 조회  ← TX 외부
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
       존재                      없음 (skip)
        │                         │
        ▼                         │
   외부 결제 환불 호출             │
        │                         │
        ▼                         │
   tx#2 Payment → CANCELLED       │
        │                         │
        ├────── 위 두 단계 실패 ──┐│
        │                       ││
        ▼                       ▼▼
  ┌─────────────────────┐  인메모리 정원 카운터 반환   ← TX 외부
  │ 보상 tx#3           │  대기열 1순위 승급 (별도 트랜잭션, 옵셔널)
  │ Enrollment          │       │
  │   CANCELLED         │       ▼
  │   → CONFIRMED       │  200 OK
  │ Course count + 1    │
  └────┬────────────────┘
       ▼
  5xx (취소 실패, 보상도 실패 시 CRITICAL 로그)
```

- 외부 결제 호출은 항상 트랜잭션 바깥에서 실행. DB 락이 외부 결제 시스템 응답을 기다리며 묶여 있지 않음.
- 결제 실패 시 보상은 단일 트랜잭션으로 묶어 처리. 결제 상태를 실패로 바꾸는 것과 수강 신청을 결제 전 상태로 되돌리는 것이 함께 성공하거나 함께 롤백되어 두 도메인이 어긋나지 않음. 보상 트랜잭션 자체가 실패하면 CRITICAL 로그로 표면화 후 운영자 수동 보정.
- 결제는 성공했는데 직후 DB 갱신이 깨지는 드문 경우엔 외부 결제 시스템에 취소 요청을 가능한 한 시도(best-effort)하고 내부 상태를 보상 트랜잭션으로 결제 전 상태로 되돌림. 외부 결제 시스템 취소 응답 자체가 실패하면 CRITICAL 로그로 표면화 — 결제 게이트웨이 응답 검증·재시도·DLQ는 본 과제 범위 외이며 운영 모니터링/대사(정합성 점검)로 보강하는 영역.
- 취소 흐름도 동일 원칙. 수강 신청 상태와 강의 정원 차감을 짧은 트랜잭션으로 먼저 확정한 뒤, 외부 환불 호출은 트랜잭션 바깥에서 실행. 외부 환불 또는 결제 취소 상태 전이가 실패하면 수강 신청을 다시 확정 상태로 되돌리고 정원도 원복하는 보상 트랜잭션이 실행됨. 보상마저 실패하면 CRITICAL 로그.
- 내부 상태 확정이 끝난 뒤에야 인메모리 정원 카운터를 해제하고 대기열 최선두를 승급. 대기열 승급은 인메모리 처리와 새 신청 생성 트랜잭션으로 끊어 처리해 본 취소 응답 경로를 지연시키지 않음.

### 5-4. 페이지네이션 — Offset/Limit 채택

| 항목 | Offset | Cursor |
|---|:---:|:---:|
| 임의 페이지 점프 | ✅ | ❌ |
| 깊은 페이지 성능 | ❌ | ✅ |
| `totalElements` 메타 | ✅ | △ |
| 구현 복잡도 | 낮음 | 중 |

**Offset 선택 사유 (비즈니스)**:
- 강의별 수강 신청 목록: 행 수 상한 = `max_capacity ≤ 100`. 깊은 페이지 자체가 발생 안 함.
- 본인 수강 신청 목록: 사용자당 수십 건 수준.
- **데이터 상한이 페이지네이션 비용 상한을 강제** → Offset 단점(딥 페이지 cost) 미발생 도메인.

## 6. 부하·동시성 검증

| 테스트 | 정원 | 동시 스레드 | 기대 |
|---|---|---|---|
| `EnrollmentE2ETest#concurrent_enrollment_does_not_exceed_capacity` | 10 | 100 | PENDING 10 / WAITLISTED 90 |
| `EnrollmentConcurrencyMatrixE2ETest` (matrix) | 1 | 50 | PENDING 1 / WAITLISTED 49 |
| 〃 | 10 | 100 | PENDING 10 / WAITLISTED 90 |
| 〃 | 50 | 100 | PENDING 50 / WAITLISTED 50 |
| 〃 (cancel-promote) | 1 | 5 신규 + 1 취소 | 최종 PENDING 1 / 정원 초과 0 |

검증되는 핵심 불변식(invariant) — 어떤 동시 시나리오에서도 깨지지 않아야 하는 조건:
- `confirmed_count ≤ max_capacity`
- 인메모리 카운터 잔여값 == `max_capacity - course.current_count`
- 대기열 승급은 1건 이하

## 7. API 목록 및 예시

> 상세 스펙(요청/응답 스키마·에러 코드·예시): **[API.md](./API.md)**

평가자 시점 흐름 순서: 강의 등록 → 목록 → 신청 → 결제 → 본인 조회 → 취소.

| # | 단계 | Method | Path | 인증 |
|---|---|---|---|---|
| 1 | 강의 등록 | POST | `/api/courses` | creator |
| 2 | 강의 목록 | GET | `/api/courses` | - |
| 3 | 수강 신청 | POST | `/api/courses/{id}/enrollments` | classmate |
| 4 | 결제 확정 | POST | `/api/enrollments/{id}/payment` | classmate |
| 5 | 본인 신청 목록 | GET | `/api/me/enrollments` | classmate |
| 6 | 강의별 신청 목록 | GET | `/api/courses/{id}/enrollments` | creator |
| 7 | 수강 신청 취소 | POST | `/api/enrollments/{id}/cancel` | classmate |
| 8 | 강의 상태 변경 | PATCH | `/api/courses/{id}/status` | creator |
| 9 | 강의 상세 | GET | `/api/courses/{id}` | - |

### 7-1. 강의 등록

```bash
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"title":"Spring 마스터","description":"심화","price":50000,
       "maxCapacity":30,"startDate":"2026-06-01","endDate":"2026-07-01"}'
# 201 Created  Location: /api/courses/1
```

### 7-2. 강의 상태 변경

```bash
curl -X PATCH http://localhost:8080/api/courses/1/status \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"status":"OPEN"}'
```

### 7-3. 수강 신청

```bash
curl -X POST http://localhost:8080/api/courses/1/enrollments -H "X-User-Id: 42"
# 정원 여유 → 201 + {"data":{"enrollmentId":1,"status":"PENDING"}}
# 만석     → 202 + {"data":{"status":"WAITLISTED","position":1,"courseId":1}}
```

### 7-4. 결제 확정 (멱등키 필수)

```bash
curl -X POST http://localhost:8080/api/enrollments/1/payment \
  -H "X-User-Id: 42" -H "Idempotency-Key: req-uuid-1"
# 200 + {"data":{"status":"SUCCESS","paymentId":1,"enrollmentStatus":"CONFIRMED"}}
```

### 7-5. 본인 신청 목록

```bash
curl "http://localhost:8080/api/me/enrollments?page=0&size=20" -H "X-User-Id: 42"
```

### 7-6. 강의별 신청 목록 (크리에이터)

```bash
curl "http://localhost:8080/api/courses/1/enrollments?page=0&size=20" -H "X-User-Id: 1"
```

### 7-7. 수강 신청 취소

```bash
curl -X POST http://localhost:8080/api/enrollments/1/cancel -H "X-User-Id: 42"
# 200 + {"data":{"enrollmentId":1,"status":"CANCELLED"}}
```

### 7-8. 대표 에러 응답

| HTTP | code | 사례 |
|---|---|---|
| 400 | `MISSING_HEADER`, `VALIDATION_FAILED` | `X-User-Id` 누락 / DTO 검증 실패 |
| 403 | `FORBIDDEN` | 타인의 enrollment 취소 시도 |
| 404 | `NOT_FOUND` | 미존재 enrollmentId |
| 409 | `CONFLICT`, `ILLEGAL_STATE_TRANSITION`, `CANCELLATION_PERIOD_EXPIRED` | DRAFT 신청 / PENDING 취소 / 7일 경과 |

## 8. 데이터 모델

### 8-1. 엔티티 관계

```text
course (1) ──< enrollment (1) ──< payment
```

- 1 강의 : N 수강 신청 : 1 결제(시도당 1 row, 멱등키로 중복 차단).
- 연관관계는 ID 참조 컬럼(`course_id`, `enrollment_id`)만 둠. `@ManyToOne`·DB FK 제약 미사용.
- 이유: ① 도메인이 단일 인스턴스·소규모로 cascade 자동화 이득 적음 ② JPA 연관관계 객체 그래프 로딩이 트랜잭션 경계·N+1 비용을 늘림 ③ Facade-Service 분리에서 Service는 ID 단위(외래키 컬럼만 두고 객체 매핑은 하지 않음)로만 다루는 게 단순.

### 8-2. `course` 테이블

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | BIGINT PK | NOT NULL | `GenerationType.IDENTITY` |
| `creator_id` | BIGINT | NOT NULL | 강의 등록자. 상태 변경·수강생 목록 조회 권한 검증 키 |
| `title` | VARCHAR(200) | NOT NULL | |
| `description` | TEXT | NULL | |
| `price` | INT | NOT NULL | 정적 팩토리에서 `>= 0` 검증 |
| `max_capacity` | INT | NOT NULL | 정적 팩토리에서 `> 0` 검증 |
| `current_count` | INT | NOT NULL | PENDING+CONFIRMED 합계. 원자적 UPDATE로만 변경 ([§5-1](#5-1-동시성-제어--4안-비교-후-option-4-채택)) |
| `start_date` | DATE | NOT NULL | |
| `end_date` | DATE | NOT NULL | `>= start_date` 검증 |
| `status` | VARCHAR(20) | NOT NULL | Enum String: `DRAFT` / `OPEN` / `CLOSED` |
| `created_at` | DATETIME | NOT NULL | `@CreatedDate` |
| `updated_at` | DATETIME | NOT NULL | `@LastModifiedDate` |

### 8-3. `enrollment` 테이블

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | BIGINT PK | NOT NULL | |
| `course_id` | BIGINT | NOT NULL | 신청 대상 강의 (애플리케이션 FK) |
| `classmate_id` | BIGINT | NOT NULL | 신청자. 본인 검증 키 |
| `status` | VARCHAR(20) | NOT NULL | `PENDING` / `CONFIRMED` / `CANCELLED` |
| `created_at` | DATETIME | NOT NULL | 신청 시각 |
| `confirmed_at` | DATETIME | NULL | CONFIRMED 전이 시 set, 결제 실패 보상 롤백 시 null |
| `cancelled_at` | DATETIME | NULL | CANCELLED 전이 시 set, 환불 실패 보상 복귀 시 null |

### 8-4. `payment` 테이블

| 컬럼 | 타입 | NULL | 설명 |
|---|---|---|---|
| `id` | BIGINT PK | NOT NULL | |
| `enrollment_id` | BIGINT | NOT NULL | 결제 대상 수강 신청 |
| `idempotency_key` | VARCHAR(64) | NOT NULL | UNIQUE. 클라이언트 발급 `Idempotency-Key` 헤더 |
| `external_payment_key` | VARCHAR(64) | NULL | 외부 게이트웨이 거래 ID. SUCCESS 전이 시 set |
| `amount` | INT | NOT NULL | 결제 금액. `>= 0` 검증 |
| `status` | VARCHAR(20) | NOT NULL | `PENDING` / `SUCCESS` / `FAILED` / `CANCELLED` |
| `created_at` | DATETIME | NOT NULL | 결제 시도 시각 |
| `updated_at` | DATETIME | NOT NULL | |

### 8-5. 명시적으로 두지 않은 것

- **DB FK 제약**: 위 §8-1 사유. cascade·연관관계 매핑 없음.
- **DB CHECK 제약**: 도메인·인메모리·SQL WHERE의 3계층 방어로 대체.
- **soft delete 컬럼**: 강의·신청·결제 모두 CANCELLED/FAILED 등 상태값으로 종단 표현. 별도 `deleted_at` 미사용.
- **낙관적 락용 버전 컬럼(`@Version`)**: 낙관적 락 미사용. 동시성 제어는 인메모리 CAS + DB 조건부 UPDATE로 일원화.

## 9. 미구현 / 제약사항

정책 범위 외 항목은 [§4 "의도적으로 제외한 항목"](#의도적으로-제외한-항목) 참고. 아래는 구현 측 제약.

- **멱등키 메모리 캐시 없음** — DB UNIQUE 인덱스 단독.
- **인메모리 대기열 재기동 시 손실** — `SeatCounterRestorer`(앱 재기동 시 DB 정원으로 인메모리 카운터를 복구하는 컴포넌트)는 카운터만 복원.
- **Flyway 미적용** — Hibernate `ddl-auto` 사용 (로컬 `update`, 테스트 `create-drop`).
- **단일 인스턴스 가정** — 멀티 인스턴스 시 인메모리 카운터/대기열 분산 동기화 필요 (Redis 등).
- **자동 OPEN 스케줄러 분산 락 미적용** — 단일 인스턴스 전제. 다중 인스턴스 배포 시 ShedLock(분산 스케줄러 락 라이브러리) 등으로 중복 실행 방지 필요. `CLOSED → OPEN` 자동 전이는 여전히 범위 외(§4 표 #3 참고).
- **결제 게이트웨이 Mock 성공률 시뮬레이션** — `MockPaymentGateway` (~97% 성공률).
- **결제 보상 트랜잭션 자체 실패 / PG 취소 요청 실패에 대한 자동 재시도·DLQ(Dead Letter Queue, 실패 메시지 격리 큐) 미구현** — CRITICAL 로그 + 수동 보정 의존.

## 10. 테스트 실행

```bash
./gradlew test                          # 전체
./gradlew test --tests "*E2ETest"       # E2E만
./gradlew test --tests "*Concurrency*"  # 동시성만
```

테스트 계층 (4-layer, 상세는 `.claude/rules/test-strategy.md`):

| 계층 | 도구 | 검증 대상 |
|---|---|---|
| 도메인 단위 | JUnit 5 | 상태 전이, Value Object, 비즈니스 규칙 |
| Repository | `@DataJpaTest` + Testcontainers | `@Query`·`@Modifying`·UNIQUE 제약 |
| Service 통합 | Spring Boot Test + Testcontainers | 트랜잭션 경계, 보상 로직 |
| E2E | RestAssured + Testcontainers | 실제 HTTP, 동시성 시나리오 |

## 11. AI 활용 범위

본 과제는 요구사항 정의부터 최종 문서화에 이르는 전 과정에 걸쳐 Claude(Anthropic)를 적극적으로 활용했습니다.

단순한 코드 생성 도구가 아니라 설계 파트너이자 검증 보조자로 두는 것을 목표로 삼았습니다.

다만 모든 산출물에 대한 의사결정과 최종 책임은 작성자 본인에게 있으며, AI는 후보안을 빠르게 만들어 비교하도록 돕는 위치에 두었습니다.

### 단계별 활용

**요구사항 정의 단계**에서는 원본 과제 명세를 함께 읽으며 명세가 비어 있는 지점을 추려내는 데 활용했습니다.

정원을 어느 상태부터 점유하는 것으로 볼지, 취소 가능 기간의 기준일을 언제로 둘지, 결제 흐름에서 내부 상태 전이와 외부 결제 호출의 순서를 어떻게 정할지처럼 해석이 갈리는 지점을 정리하고, 각 선택지의 장단점을 빠르게 비교한 뒤 최종 결정은 작성자가 직접 내렸습니다.

**구현 범위 결정 단계**에서는 평가 핵심인 상태 전이·정원 관리·동시성 제어와 거리가 먼 항목을 식별하는 데 활용했습니다.

자동 만료, 중복 신청 방지, 강의 취소에 따른 일괄 환불처럼 복잡도 대비 평가 기여도가 낮은 항목을 의도적으로 제외 목록에 올렸으며, 작업 도중 새로운 기능 욕심이 생길 때마다 이 목록을 게이트로 다시 점검했습니다.

**기술 설계 단계**에서는 동시성 제어 후보안을 폭넓게 비교하는 데 활용했습니다.

비관적 락, 조건부 갱신 단독, 애플리케이션 락, 인메모리 카운터와 조건부 갱신의 조합을 스레드와 커넥션 점유 관점에서 따져 보았고, 트랜잭션 경계를 어디까지 좁힐지, 결제 보상 흐름을 어떻게 짧게 끊어 처리할지, 상태 머신을 어떻게 모델링할지에 대한 스케치를 함께 다듬었습니다.

채택한 설계의 근거와 트레이드오프는 본인이 직접 설명할 수 있는 수준까지 이해한 뒤에 확정했습니다.

**코드·테스트 작성 단계**에서는 도메인 계층, 데이터 접근 계층, 비즈니스 흐름을 조립하는 상위 계층, 그리고 네 단계로 나뉜 테스트를 단계별 커밋으로 진행했습니다.

한 단계가 끝날 때마다 작성자가 변경 내용을 직접 검토한 뒤에만 커밋이 진행되도록 워크플로우를 강제했고, 이해되지 않는 부분은 그대로 두지 않고 다시 묻거나 직접 수정해 해소했습니다.

**검증 단계**에서는 동시성 시나리오를 다중 스레드로 동시에 호출하는 테스트를 마련했습니다.

정원이 절대 초과되어서는 안 된다는 점, 인메모리 카운터와 데이터베이스의 정원 값이 항상 일치해야 한다는 점, 그리고 대기열 승급이 한 자리당 한 번 이상 발생해서는 안 된다는 점을 회귀 방지 안전망으로 두었으며, 의심스러운 경계 조건은 별도 테스트로 분리해 재현하고 원인을 짚은 뒤 수정했습니다.

**문서 작성 단계**에서는 본 README와 API 명세 문서의 구조, 표, 다이어그램 초안을 함께 다듬었습니다.

다만 사실관계는 실제 동작과 코드를 대조해 직접 확인하고 수정한 뒤 반영했습니다.

### 활용 원칙

첫째, AI는 후보안을 빠르게 생성하고 비교하는 역할에 한정했으며, 채택 여부는 항상 작성자가 결정했습니다.

둘째, 생성된 모든 코드와 문서는 본인이 읽고 이해한 뒤에만 채택했으며, 이해되지 않는 부분은 그대로 두지 않고 다시 묻거나 직접 고쳐 해소했습니다.

셋째, 테스트가 통과한다는 사실보다 "그 테스트가 무엇을 보장하는가"를 우선 기준으로 삼아, 의도된 불변식을 지키는지를 우선 검토했습니다.

이러한 절차를 통해 AI 활용으로 얻은 속도 이득을 다시 평가 핵심에 깊이 투입하는 데 사용했습니다.
