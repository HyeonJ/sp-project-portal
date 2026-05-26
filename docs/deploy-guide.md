# 배포 가이드 / 설치 매뉴얼

> 산출물 No.20. **현재 구현 기준** 빌드·환경변수·배포 절차. 목표 운영 인프라(AWS EC2/RDS/S3 등)의 청사진은 [`infrastructure.md`](infrastructure.md), 논리 아키텍처는 [`architecture.md`](architecture.md) 참조.

## 0. 구현 현황 vs 목표 (먼저 읽을 것)

`infrastructure.md`는 AWS 운영 목표를 기술하지만, **MVP 코드에 실제로 구현된 것**은 아래와 같다. 배포 시 이 차이를 전제로 한다.

| 항목 | 목표(infra 문서) | **현재 구현** |
|------|-----------------|--------------|
| 파일 스토리지 | AWS S3 | **로컬 디렉토리만** (`LocalFileStorage`). S3 어댑터 미구현 → 영속 볼륨 필수 |
| 세션 | Redis 대안 검토 | PostgreSQL (Spring Session JDBC) |
| 이메일 발송 | SES/SendGrid | **미연동** — 초대·알림은 인앱(상단바 벨) 중심. 외부 메일 발송은 추후 |
| 인증 | 세션 | 세션 (일치, JWT 아님) |
| DB | RDS PostgreSQL 15 | PostgreSQL 15 (일치) |

## 1. 프로필 구조

| 프로필 | 용도 | DataSource | 시드 | 비고 |
|--------|------|-----------|------|------|
| `local` | 로컬 개발 (기본값) | Testcontainers PostgreSQL (`bootTestRun`) | ✅ | `application.yml` |
| `docker` | 자체 완결 데모 | compose의 PostgreSQL | ✅ | `application-docker.yml` |
| `prod` | **운영** | 환경변수 필수 | ❌ | `application-prod.yml` |
| `test` | 통합 테스트 | Testcontainers | (테스트 내) | `./gradlew test` |

- 프로필은 `SPRING_PROFILES_ACTIVE`로 지정. 미지정 시 `local`.
- 시드(`DataSeeder`)는 `local`/`dev`/`docker`에서만 동작. **prod에는 시드 계정이 생성되지 않는다.**

## 2. 환경변수 (prod 필수)

`prod`는 모든 접속 정보를 환경변수로 요구하며, 미설정 시 **기동 실패(fail-fast)**한다.

| 변수 | 필수 | 기본값 | 설명 |
|------|:---:|--------|------|
| `SPRING_PROFILES_ACTIVE` | ✅ | `local` | 운영은 `prod` |
| `DB_URL` | ✅ | — | `jdbc:postgresql://<host>:5432/<db>` |
| `DB_USERNAME` | ✅ | — | DB 계정 |
| `DB_PASSWORD` | ✅ | — | DB 비밀번호 |
| `STORAGE_LOCAL_DIR` | — | `/data/storage` | 업로드 파일 영속 디렉토리 — **영속 볼륨에 마운트 필수** |

> 시크릿 하드코딩 금지(글로벌 규칙). prod 기본값을 두지 않는 것은 의도된 fail-fast다.

## 3. 빌드

```bash
./gradlew bootJar              # build/libs/sp-project-portal-0.1.0.jar 생성 (테스트 제외)
./gradlew build                # 컴파일 + 테스트(Testcontainers, Docker 필요) + bootJar
```

- `bootJar`는 테스트를 돌리지 않으므로 Docker 없이도 JAR 산출 가능(`Dockerfile`의 build 스테이지가 이 경로 사용).
- 배포 전 검증은 `build`(테스트 포함)로. Docker Engine 29.x는 `build.gradle`의 `api.version=1.44` 고정에 의존.

## 4. 배포 방법

### 4-1. Docker Compose (데모 / 단일 호스트)

앱 + PostgreSQL을 한 번에. 영속 볼륨(`db-data`, `storage-data`) 포함.

```bash
docker compose up --build      # → http://localhost:8080
DB_PASSWORD=<강한 비밀번호> docker compose up --build   # DB 비번 오버라이드
```

- `docker` 프로필이라 **시드 계정이 포함**된다. 실 운영 노출에는 부적합 — 데모/검증용.
- compose는 `db` healthcheck(`pg_isready`)가 통과해야 앱을 기동한다.

### 4-2. 단일 컨테이너 (외부 DB 연결)

`Dockerfile`은 멀티스테이지(Temurin 21 JDK 빌드 → JRE 런타임). 기본 `SPRING_PROFILES_ACTIVE=docker`이므로 **운영은 `prod`로 덮어쓴다.**

```bash
docker build -t sppm:0.1.0 .

docker run -d --name sppm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://<db-host>:5432/<db> \
  -e DB_USERNAME=<user> \
  -e DB_PASSWORD=<pass> \
  -e STORAGE_LOCAL_DIR=/data/storage \
  -v sppm-storage:/data/storage \
  sppm:0.1.0
```

> ⚠️ `-v sppm-storage:/data/storage` **영속 볼륨을 반드시 마운트**한다. S3 미구현이라 컨테이너 재생성 시 볼륨이 없으면 업로드 파일이 사라진다.

### 4-3. JAR 직접 실행

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/sppm \
DB_USERNAME=sppm DB_PASSWORD=<pass> \
STORAGE_LOCAL_DIR=/var/sppm/storage \
java -jar build/libs/sp-project-portal-0.1.0.jar
```

## 5. 데이터베이스 / 마이그레이션

- **Flyway가 스키마를 전적으로 소유**한다(`spring.flyway.locations=classpath:db/migration`). 앱 기동 시 V1~V10을 순서대로 적용.
- Spring Session 스키마도 Flyway(V1)가 관리 — `spring.session.jdbc.initialize-schema=never`로 자동 init 끔.
- 운영 DB는 **빈 데이터베이스**만 준비하면 된다(스키마는 Flyway가 생성). DB 계정에 DDL 권한 필요.
- 스키마 변경은 **새 `V11__*.sql` 추가로만**. 이미 적용된 마이그레이션 파일 수정 금지(체크섬 불일치로 기동 실패).

## 6. 운영 체크리스트

- [ ] 빈 PostgreSQL 15 데이터베이스 + DDL 권한 계정 준비
- [ ] `prod` 프로필 + 4개 필수 환경변수 설정 (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`SPRING_PROFILES_ACTIVE=prod`)
- [ ] `STORAGE_LOCAL_DIR`을 **영속 볼륨**에 마운트 (백업 대상 포함)
- [ ] 리버스 프록시에서 HTTPS 종단 (세션 쿠키 보안 전제)
- [ ] 최초 관리자 계정 생성 — prod는 시드가 없으므로 **운영 진입 경로 별도 확보 필요**(시드 비활성, 초기 admin 부트스트랩 미구현 → 운영 이관 시 결정 사항)
- [ ] 파일 업로드 한도 확인: 50MB/파일, 60MB/요청 (`application.yml` multipart)
- [ ] 세션 만료 30분(무활동) — 필요 시 `server.servlet.session.timeout` 조정

## 7. 헬스 체크 / 모니터링

- `GET /actuator/health` (인증 불필요), `GET /actuator/info`. 그 외 actuator 엔드포인트는 미노출.
- 로그 레벨: `com.softpuzzle.pm` = prod `INFO` / local `DEBUG`. Slf4j + Logback(Spring Boot 기본).

## 8. 알려진 운영 제약 (인수인계 주의)

1. **S3 미구현** — 다중 인스턴스 수평 확장 시 로컬 스토리지가 공유되지 않는다. 스케일아웃 전 S3 어댑터(`FileStorage` 구현 추가) 필요.
2. **이메일 미연동** — 초대/알림은 인앱 중심. 외부 메일 발송이 필요하면 별도 구현.
3. **prod 초기 관리자 부트스트랩 부재** — 시드가 prod에 없어 첫 admin 계정 생성 경로를 운영 이관 시 정해야 한다.
4. api-spec 등 일부 설계 문서와 실제 구현의 차이는 [`retrospective/`](retrospective/) 참조.
