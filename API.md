# API Spec

LiveKlass BE 과제 A — 수강 신청 시스템 REST API.

- Base URL: `http://localhost:8080`
- 인증: `X-User-Id: <number>` 헤더 필수 (단, `GET /api/courses`·`GET /api/courses/{id}` 제외)
- 응답 본문: 모든 성공 응답은 `{"data": <payload>}`, 에러는 `{"code": <string>, "message": <string>, "fieldErrors": [...]}`
- 컨텐츠 타입: `application/json`

## 목차

| # | 단계 | Method | Path | 인증 | 링크 |
|---|---|---|---|---|---|
| 1 | 강의 등록 | POST | `/api/courses` | creator | [§1](#1-강의-등록) |
| 2 | 강의 목록 | GET | `/api/courses` | - | [§2](#2-강의-목록) |
| 3 | 강의 상세 | GET | `/api/courses/{id}` | - | [§3](#3-강의-상세) |
| 4 | 강의 상태 변경 | PATCH | `/api/courses/{id}/status` | creator | [§4](#4-강의-상태-변경) |
| 5 | 수강 신청 | POST | `/api/courses/{id}/enrollments` | classmate | [§5](#5-수강-신청) |
| 6 | 결제 확정 | POST | `/api/enrollments/{id}/payment` | classmate | [§6](#6-결제-확정) |
| 7 | 본인 신청 목록 | GET | `/api/me/enrollments` | classmate | [§7](#7-본인-신청-목록) |
| 8 | 강의별 신청 목록 | GET | `/api/courses/{id}/enrollments` | creator | [§8](#8-강의별-신청-목록) |
| 9 | 수강 신청 취소 | POST | `/api/enrollments/{id}/cancel` | classmate | [§9](#9-수강-신청-취소) |

공통 에러 코드: [§10](#10-공통-에러-코드)

---

## 1. 강의 등록

크리에이터가 새 강의를 `DRAFT` 상태로 등록.

- **Method / Path**: `POST /api/courses`
- **Headers**: `X-User-Id`, `Content-Type: application/json`

### Request Body

| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| `title` | string | NotBlank, ≤200 | |
| `description` | string | - | optional |
| `price` | int | ≥0 | KRW |
| `maxCapacity` | int | >0 | |
| `startDate` | date (`YYYY-MM-DD`) | NotNull | |
| `endDate` | date (`YYYY-MM-DD`) | NotNull | |

### Request 예시

```bash
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "title":"Spring 마스터",
    "description":"심화 강의",
    "price":50000,
    "maxCapacity":30,
    "startDate":"2026-06-01",
    "endDate":"2026-07-01"
  }'
```

### Response (201 Created)

`Location: /api/courses/{id}`

```json
{
  "data": {
    "id": 1,
    "creatorId": 1,
    "title": "Spring 마스터",
    "description": "심화 강의",
    "price": 50000,
    "maxCapacity": 30,
    "currentCount": 0,
    "startDate": "2026-06-01",
    "endDate": "2026-07-01",
    "status": "DRAFT"
  }
}
```

### Error

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_REQUEST` | validation 실패 (`fieldErrors` 포함) |
| 400 | `MISSING_HEADER` | `X-User-Id` 누락 |

---

## 2. 강의 목록

전체 강의(모든 상태) 페이지네이션 조회.

- **Method / Path**: `GET /api/courses`
- **Headers**: 없음

### Query Parameters

| 파라미터 | 타입 | 기본 | 비고 |
|---|---|---|---|
| `page` | int | 0 | 0-base |
| `size` | int | 20 | 권장 ≤100 |

### Request 예시

```bash
curl "http://localhost:8080/api/courses?page=0&size=20"
```

### Response (200 OK)

```json
{
  "data": {
    "items": [
      {
        "id": 1, "creatorId": 1, "title": "Spring 마스터",
        "description": "심화 강의", "price": 50000,
        "maxCapacity": 30, "currentCount": 5,
        "startDate": "2026-06-01", "endDate": "2026-07-01",
        "status": "OPEN"
      }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  }
}
```

정렬: `id DESC` (최근 등록 순).

---

## 3. 강의 상세

- **Method / Path**: `GET /api/courses/{courseId}`
- **Headers**: 없음

### Request 예시

```bash
curl http://localhost:8080/api/courses/1
```

### Response (200 OK)

§1 Response payload와 동일 스키마.

### Error

| HTTP | code | 조건 |
|---|---|---|
| 404 | `NOT_FOUND` | 미존재 courseId |

---

## 4. 강의 상태 변경

크리에이터가 강의 상태를 전이. 전이 가능 경로:

```
DRAFT  → OPEN | CLOSED
OPEN   ↔ CLOSED
```

- **Method / Path**: `PATCH /api/courses/{courseId}/status`
- **Headers**: `X-User-Id`, `Content-Type: application/json`

### Request Body

| 필드 | 타입 | 값 |
|---|---|---|
| `status` | string | `OPEN` / `CLOSED` / `DRAFT` |

### Request 예시

```bash
curl -X PATCH http://localhost:8080/api/courses/1/status \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"status":"OPEN"}'
```

### Response (200 OK)

```json
{
  "data": {
    "courseId": 1,
    "status": "OPEN",
    "remaining": 30
  }
}
```

- `remaining`: OPEN으로 전이 시 남은 자리. 그 외 0.

### Error

| HTTP | code | 조건 |
|---|---|---|
| 403 | `FORBIDDEN` | 본인 강의가 아님 |
| 404 | `NOT_FOUND` | 미존재 courseId |
| 409 | `ILLEGAL_STATE_TRANSITION` | 허용되지 않은 전이 (예: `CLOSED → DRAFT`) |

---

## 5. 수강 신청

- **Method / Path**: `POST /api/courses/{courseId}/enrollments`
- **Headers**: `X-User-Id`

### Request 예시

```bash
curl -X POST http://localhost:8080/api/courses/1/enrollments \
  -H "X-User-Id: 42"
```

### Response — 정원 여유 (201 Created)

`Location: /api/enrollments/{id}`

```json
{
  "data": {
    "status": "PENDING",
    "enrollmentId": 7
  }
}
```

### Response — 만석 (202 Accepted, 대기열 등록)

```json
{
  "data": {
    "status": "WAITLISTED",
    "courseId": 1,
    "position": 3
  }
}
```

### Error

| HTTP | code | 조건 |
|---|---|---|
| 400 | `MISSING_HEADER` | `X-User-Id` 누락 |
| 409 | `CONFLICT` | DRAFT / CLOSED / 미존재 강의 (인메모리 카운터 미등록) |

---

## 6. 결제 확정

PENDING → CONFIRMED 전이. 멱등키 필수.

- **Method / Path**: `POST /api/enrollments/{enrollmentId}/payment`
- **Headers**: `X-User-Id`, `Idempotency-Key`

### Headers

| 헤더 | 비고 |
|---|---|
| `X-User-Id` | 본인 enrollment만 결제 가능 |
| `Idempotency-Key` | DB UNIQUE. 동일 키 재시도 시 동일 결과 |

### Request 예시

```bash
curl -X POST http://localhost:8080/api/enrollments/7/payment \
  -H "X-User-Id: 42" \
  -H "Idempotency-Key: req-uuid-1"
```

### Response (200 OK)

```json
{
  "data": {
    "paymentId": 11,
    "status": "SUCCESS",
    "enrollmentStatus": "CONFIRMED"
  }
}
```

### Error

| HTTP | code | 조건 |
|---|---|---|
| 400 | `MISSING_HEADER` | `Idempotency-Key` 누락 |
| 403 | `FORBIDDEN` | 본인 enrollment가 아님 |
| 404 | `NOT_FOUND` | 미존재 enrollmentId |
| 409 | `DUPLICATE_PAYMENT` | 멱등키 UNIQUE 위반 |
| 409 | `ILLEGAL_STATE_TRANSITION` | PENDING이 아닌 상태 |
| 502 | `PAYMENT_GATEWAY_FAILURE` | 외부 게이트웨이 charge 실패 (보상 후) |

---

## 7. 본인 신청 목록

호출자의 enrollment 페이지네이션.

- **Method / Path**: `GET /api/me/enrollments`
- **Headers**: `X-User-Id`

### Query Parameters

| 파라미터 | 타입 | 기본 |
|---|---|---|
| `page` | int | 0 |
| `size` | int | 20 |

### Request 예시

```bash
curl "http://localhost:8080/api/me/enrollments?page=0&size=20" \
  -H "X-User-Id: 42"
```

### Response (200 OK)

```json
{
  "data": {
    "items": [
      {
        "enrollmentId": 7,
        "courseId": 1,
        "status": "CONFIRMED",
        "createdAt": "2026-05-01T12:00:00",
        "confirmedAt": "2026-05-01T12:01:30",
        "cancelledAt": null
      }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  }
}
```

`null` 필드는 `@JsonInclude(NON_NULL)`로 응답에서 생략됨.

---

## 8. 강의별 신청 목록

크리에이터가 본인 강의의 enrollment 조회.

- **Method / Path**: `GET /api/courses/{courseId}/enrollments`
- **Headers**: `X-User-Id`

### Request 예시

```bash
curl "http://localhost:8080/api/courses/1/enrollments?page=0&size=20" \
  -H "X-User-Id: 1"
```

### Response (200 OK)

```json
{
  "data": {
    "items": [
      {
        "enrollmentId": 7,
        "classmateId": 42,
        "status": "CONFIRMED",
        "createdAt": "2026-05-01T12:00:00",
        "confirmedAt": "2026-05-01T12:01:30"
      }
    ],
    "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
  }
}
```

### Error

| HTTP | code | 조건 |
|---|---|---|
| 403 | `FORBIDDEN` | 본인 강의가 아님 |
| 404 | `NOT_FOUND` | 미존재 courseId |

---

## 9. 수강 신청 취소

CONFIRMED 상태 + confirmedAt 시점부터 7일 이내만 수강생 취소 가능. 취소 시 SUCCESS payment 자동 환불 + 대기자 1명 자동 승급.

- **Method / Path**: `POST /api/enrollments/{enrollmentId}/cancel`
- **Headers**: `X-User-Id`

### Request 예시

```bash
curl -X POST http://localhost:8080/api/enrollments/7/cancel \
  -H "X-User-Id: 42"
```

### Response (200 OK)

```json
{
  "data": {
    "enrollmentId": 7,
    "status": "CANCELLED"
  }
}
```

### Error

| HTTP | code | 조건 |
|---|---|---|
| 403 | `FORBIDDEN` | 본인 enrollment가 아님 |
| 404 | `NOT_FOUND` | 미존재 enrollmentId |
| 409 | `ILLEGAL_STATE_TRANSITION` | PENDING / CANCELLED 상태 (직접 전이 금지) |
| 409 | `CANCELLATION_PERIOD_EXPIRED` | confirmedAt + 7일 초과 |
| 502 | `PAYMENT_GATEWAY_FAILURE` | 외부 게이트웨이 cancel 실패 (보상 후) |

---

## 10. 공통 에러 코드

`GlobalExceptionHandler` 매핑.

| code | HTTP | 의미 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 검증 실패 / 타입 불일치 / IllegalArgument |
| `MISSING_HEADER` | 400 | 필수 헤더 누락 |
| `FORBIDDEN` | 403 | 인가 실패 (creator/classmate 불일치) |
| `NOT_FOUND` | 404 | 리소스 미존재 |
| `CONFLICT` | 409 | 일반 충돌 (만석·DRAFT 신청 등) |
| `ILLEGAL_STATE_TRANSITION` | 409 | 상태 머신 위반 |
| `CAPACITY_EXCEEDED` | 409 | 정원 초과 (DB 원자적 UPDATE 실패) |
| `CANCELLATION_PERIOD_EXPIRED` | 409 | 취소 가능 기간 초과 |
| `DUPLICATE_PAYMENT` | 409 | 멱등키 UNIQUE 위반 |
| `PAYMENT_GATEWAY_FAILURE` | 502 | 외부 결제 게이트웨이 실패 |
| `INTERNAL_ERROR` | 500 | 그 외 예상 외 |

### 에러 응답 스키마

```json
{
  "code": "INVALID_REQUEST",
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "maxCapacity", "message": "must be greater than 0" }
  ]
}
```

`fieldErrors`는 검증 실패 시에만 비어있지 않음.
