# 테스트 작성 전략

본 프로젝트의 테스트는 다음 **4계층**으로만 구성한다. 각 계층의 책임·범위·도구를 엄수한다.

---

## 1. E2E 통합 테스트

**목적**: 실제 HTTP 요청·응답 단위로 시스템 전체 경로 검증.

**도구**: `RestAssured` + Spring Boot Test + Testcontainers MySQL.

**원칙**:
- 컨트롤러 단위 `@WebMvcTest`나 `MockMvc` 사용 **금지**. 실제 내장 톰캣 기동 후 RestAssured로 호출.
- DB는 Testcontainers MySQL 사용. H2 또는 in-memory fake **금지**.
- 내부 컴포넌트(Service, Repository, Facade) **Mock 금지**.
- **외부 시스템만 Mock 허용**: 결제 Mock API, 외부 HTTP 콜 등. WireMock 또는 인터페이스 대체 구현으로 격리.
- 동시성 시나리오(마지막 자리 경쟁 등)는 E2E에서 검증한다. `CountDownLatch` 기반 N-스레드 호출.
- 테스트 데이터는 각 테스트 내부에서 생성·정리. `@DirtiesContext` 또는 트랜잭션 롤백·DB truncate 전략 중 하나 선택.

**위치 예시**: `src/test/java/.../e2e/`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentE2ETest extends AbstractIntegrationTest {
    @LocalServerPort int port;

    @Test
    void enroll_returns_pending() {
        given().port(port).header("X-User-Id", 1)
        .when().post("/api/courses/{id}/enrollments", courseId)
        .then().statusCode(201).body("status", equalTo("PENDING"));
    }
}
```

---

## 2. Service 통합 테스트

**목적**: `@Transactional` 경계, DB 상호작용, 상태 전이의 통합 동작 검증.

**도구**: Spring Boot Test + Testcontainers MySQL.

**원칙**:
- Service·Facade 메서드를 **직접 호출**하여 검증. HTTP 레이어 우회.
- Repository **Mock 금지** — 실제 DB 사용.
- 외부 시스템(결제 Mock API)만 Mock 또는 stub 가능.
- 트랜잭션 경계 검증(롤백, 보상 로직 호출 등)을 명시적으로 다룸.

**위치 예시**: `src/test/java/.../service/`

**E2E와의 구분**: 같은 시나리오라면 E2E 1개 + 엣지 케이스는 Service 통합으로 분리. 둘 다 작성하면 중복 — 한 시나리오 한 계층 원칙.

---

## 3. Repository 테스트

**목적**: **직접 작성한 쿼리만** 검증. `@Query` JPQL/Native, Querydsl, 동적 쿼리, 락 힌트 등.

**도구**: `@DataJpaTest` + Testcontainers MySQL (`@AutoConfigureTestDatabase(replace = NONE)`).

**원칙**:
- Spring Data JPA 메서드 명명 규칙 자동 생성 쿼리(`findById`, `findByXxx` 등) 테스트 **금지** — Spring이 보장.
- 검증 대상은 다음에 한정:
  - `@Query` 어노테이션 쿼리
  - 네이티브 SQL
  - 원자적 UPDATE (예: `tryIncreaseCurrentCount`)
  - 락 힌트(`@Lock`)
  - Querydsl 동적 쿼리
- DB 제약(UNIQUE 등) 동작 검증도 여기에 포함. CHECK 제약은 도메인 로직(정적 팩토리 검증 + 원자적 UPDATE WHERE 절)으로 막으므로 별도 검증 불필요.
- **테스트는 `given/when/then` 패턴으로 단순 호출**. `TransactionTemplate`·`@Transactional` 테스트 wrap 금지.
- **DB 초기화**: `DatabaseCleaner`가 매 테스트 전 `TRUNCATE`. `@Transactional` 자동 롤백 방식 **금지** (롤백된 케이스가 실제 커밋 시점 동작과 다를 수 있음).

#### `@Modifying @Query`와 `@Transactional` 패턴 (Spring Data JPA)

`JpaRepository`의 자동 `@Transactional`은 `SimpleJpaRepository`에서 상속된 메서드(`save`, `findById` 등)에만 적용된다. **사용자 정의 `@Query @Modifying` 메서드는 별도 프록시 경로**(QueryExecutor)로 실행되며 클래스 레벨 `@Transactional`이 적용되지 않는다.

따라서 `@Modifying @Query` 메서드는 다음 중 하나가 필요:
- (권장) repo 메서드에 `@Transactional` 명시 — Spring Data 공식 권장 패턴
- 호출자(Service)가 `@Transactional` 보유

repo 메서드의 `@Transactional`은 Service가 호출 시 기본 propagation `REQUIRED`로 기존 tx에 join. 이중 tx 아님.

```java
public interface CourseRepository extends JpaRepository<Course, Long> {
    @Transactional
    @Modifying
    @Query("UPDATE Course c SET c.currentCount = c.currentCount + 1 WHERE ...")
    int tryIncreaseCurrentCount(@Param("courseId") Long courseId);
}
```

→ 테스트도 단순 `courseRepository.tryIncreaseCurrentCount(id)`로 호출. tx 신경 X.

**위치 예시**: `src/test/java/.../repository/`

---

## 4. 도메인 단위 테스트

**목적**: 도메인 객체·Enum·Value Object의 순수 로직 검증.

**도구**: JUnit 5만. Spring 컨텍스트 **로딩 금지**.

**원칙**:
- 외부 의존성 없는 POJO 테스트. DB·Mock 불필요.
- 검증 대상:
  - 상태 전이 메서드(`Enrollment.confirm()`, `Course.transitionTo()` 등)
  - Enum의 `verifyTransitionTo` 매핑
  - 7일 취소 기간 같은 비즈니스 규칙
  - 도메인 예외 발생 조건
- 빠른 실행 — 수백~수천 개 가능.

**위치 예시**: `src/test/java/.../domain/`

```java
class EnrollmentTest {
    @Test
    void cancel_after_7days_throws() {
        Enrollment e = Enrollment.confirmed(at("2026-05-01T00:00:00"));
        assertThatThrownBy(() -> e.cancelByClassmate(1L, at("2026-05-09T00:00:01")))
            .isInstanceOf(CancellationPeriodExpiredException.class);
    }
}
```

---

## 작성 가이드

1. **계층 선택 우선순위**: 도메인 단위 → Repository → Service 통합 → E2E. 가장 낮은 계층에서 검증 가능한 것은 그곳에서 검증.
2. **중복 금지**: 같은 시나리오를 여러 계층에서 검증하지 않는다. 엣지 케이스는 분리된 계층에 배치.
3. **Mock 사용 원칙**: 내부 컴포넌트 Mock 전면 금지. 외부 시스템(결제 API 등)만 Mock.
4. **DB는 항상 Testcontainers MySQL**. H2·in-memory fake 사용 금지.
5. **공통 베이스**: `AbstractIntegrationTest` 활용. Testcontainers 컨테이너 재사용 설정 권장.
6. **동시성 테스트는 E2E 계층에서 필수** (Tech-spec §10.2).

## `@DisplayName` 필수

모든 테스트 메서드에 `@DisplayName("한글로 시나리오 서술")` 부착. 메서드명만으로는 "무엇을 검증하는지" 불명확하므로 한글 서술을 강제.

- 형식: `<주어/대상> <조건> <기대 결과>` (예: `"OPEN 강의에 정원 미달 시 current_count가 1 증가한다"`)
- 메서드명은 영문 snake_case 유지 (IDE 검색·grep 용이)
- `@ParameterizedTest`도 동일 — `@DisplayName`로 케이스 이름 부여, 파라미터 변동 부분은 `name`으로 표현 가능

```java
@Test
@DisplayName("OPEN 강의에 정원 미달 시 current_count가 1 증가한다")
void tryIncreaseCurrentCount_increments_when_open_and_under_capacity() { ... }
```

테스트 리포트(`./gradlew test`)와 IDE 트리에 한글 표시 → 실패 시 의도 파악 즉시.

---

## 금지 사항

- `@MockBean`으로 Repository·Service·Facade 대체
- H2·HSQLDB·in-memory DB 도입
- `MockMvc` 기반 컨트롤러 테스트 (E2E는 RestAssured)
- Spring Data JPA 기본 메서드 테스트
- 트랜잭션 경계를 추정만으로 작성 — 반드시 통합 테스트로 검증
- 테스트 메서드를 `@Transactional`·`TransactionTemplate`로 감싸기 — DB 초기화는 `TRUNCATE` 기반 `DatabaseCleaner`로 통일
