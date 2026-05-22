# liveklass-assignment

Spring Boot 3.3 + Java 21 + JPA + MySQL.

## 실행

```bash
# 로컬 MySQL 기동
docker compose up -d

# 앱 부팅
./gradlew bootRun
```

## 테스트 (Testcontainers MySQL)

```bash
./gradlew test
```

## 빌드

```bash
./gradlew clean build
```

## 스택

- Java 21
- Spring Boot 3.3.5 (Web, Data JPA, Validation)
- MySQL 8.0 (로컬: docker compose / 테스트: Testcontainers)
- Lombok
- JUnit 5 + Testcontainers
