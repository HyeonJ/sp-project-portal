# SoftPuzzle PM

> 제품명: **SoftPuzzle PM** — SoftPuzzle의 B2B 프로젝트 관리 SaaS. 레포 슬러그·폴더는 `sp-project-portal` 유지.

고객사와 대행사(프로젝트팀) 간 **승인/컨펌 워크플로우**를 중심으로 프로젝트 진행을 관리하는 웹 시스템. 요구사항·IA·디자인·프로토타입 산출물을 파일로 업로드하고, 고객사가 컨펌하면 게이트가 순차로 열리며 개발 단계까지 진행한다.

> 📌 **제품이 무엇을 하고 무엇이 특별한지**(메뉴·기능·차별점·데모 보는 법)는 [`OVERVIEW.md`](OVERVIEW.md)를 먼저 읽으면 좋다. 이 README는 코드를 받아 **띄우는 방법**에 집중한다.

---

## 1. 기술 스택

| 영역 | 기술 |
|------|------|
| 언어/런타임 | Java 21 (Gradle toolchain 고정, 아키텍처 floor=17) |
| 프레임워크 | Spring Boot 3.3.5 |
| 빌드 | Gradle 8.14.4 (Wrapper 포함 — 별도 설치 불필요) |
| 인증 | **Spring Security 세션 인증 (JWT 아님)** + Spring Session JDBC |
| 영속 | MyBatis 3.0.4 (XML 매퍼) + PostgreSQL 15 + Flyway |
| 뷰 | Thymeleaf SSR + jQuery (AJAX는 `{success, data}` JSON 봉투) |
| 파일 스토리지 | 로컬 디렉토리 (`FileStorage` 추상화 — **S3 어댑터는 미구현**) |
| 테스트 | Testcontainers PostgreSQL (H2 미사용 — 운영과 동일 DB로 테스트) |

> 인증·스토리지 두 항목은 흔한 오해 지점이라 강조한다. 일부 옛 설계 문서(api-spec 초안 등)에 JWT/refresh·S3 표현이 남아 있으나 **실제 구현은 세션 인증 + 로컬 스토리지**다. 배경은 [`docs/retrospective/`](docs/retrospective/) 참조.

## 2. 사전 요구사항

- **JDK 21** (Gradle toolchain이 자동 탐지·다운로드하지만, 설치돼 있으면 빠름)
- **Docker 데몬이 실행 중**이어야 한다 — 로컬 실행·테스트 모두 Testcontainers로 PostgreSQL 컨테이너를 띄운다. **Docker 없이는 로컬 기동·테스트 불가.**
  - 확인: `docker info`가 성공해야 한다. 실패하고 `failed to connect to the docker API` 에러가 나면 데몬이 꺼진 것.
  - Windows: **Docker Desktop**을 실행하고(`C:\Program Files\Docker\Docker\Docker Desktop.exe`) 데몬이 올라올 때까지(`docker info` 성공) 기다린 뒤 진행한다. 부팅에 30~90초 걸릴 수 있다.
  - Docker Engine 29.x 사용 시 `build.gradle`이 Docker API 버전을 `1.44`로 고정해 호환된다(별도 설정 불필요).

## 3. Clone & Run

### 3-1. 로컬 개발 실행 (권장)

```bash
./gradlew bootTestRun          # Windows: .\gradlew.bat bootTestRun
```

- `local` 프로필로 기동(기본값). `TestcontainersConfiguration`이 `postgres:15-alpine` 컨테이너를 자동 기동하고 Flyway가 스키마를 올린다.
- `DataSeeder`가 **계정·샘플 프로젝트 3개**를 멱등 시드한다(아래 4절).
- 접속: **http://localhost:8080** → `/login`

> ⚠️ 일반 `./gradlew bootRun`은 `local` 프로필에 datasource 설정이 없어 기동되지 않는다. 로컬은 **반드시 `bootTestRun`**(Testcontainers 경로)을 쓴다.

### 3-2. 자체 완결 데모 (앱 + DB 한 번에)

```bash
docker compose up --build      # → http://localhost:8080
```

- `docker` 프로필. 앱과 PostgreSQL을 함께 띄우며 시드 계정 포함. 영속 볼륨(`db-data`, `storage-data`) 사용.

## 4. 시드 계정 (local / dev / docker 프로필)

모든 시드 계정 비밀번호: **`Passw0rd!`**

| 이메일 | 역할(tier) | 용도 |
|--------|-----------|------|
| `admin@softpuzzle.com` | 관리자(admin) | 플랫폼 관리 콘솔 |
| `pm@softpuzzle.com` | 프로젝트팀(team/PM) | 프로젝트 생성·진행 주체 |
| `client@acme.com` | 고객사(client) | ACME 포털 — 요구사항~프로토타입 컨펌 완료, Figma 등록(단계 12) |
| `client@beta.com` | 고객사(client) | 베타 커머스 — 요구사항만 컨펌(단계 5) |
| `client@gamma.com` | 고객사(client) | 감마 앱 — 착수 직후, 슬롯 비어 있음(단계 1) |

세 프로젝트는 **진행 단계가 서로 달라** 게이트·로드맵·컨펌 흐름을 한 번에 확인할 수 있다. 시드는 멱등이라 재기동해도 중복 생성되지 않는다(`DataSeeder`).

## 5. 테스트

```bash
./gradlew test                 # Testcontainers PostgreSQL 통합 테스트 (Docker 필요)
./gradlew build                # 컴파일 + 테스트 + bootJar
```

- 서비스/매퍼 단위의 Testcontainers 통합 테스트(`*IT`)가 `src/test/java` 아래에 있다. 인가 매트릭스(team/client/admin/member/non-member)·게이트 진행·검토 상태머신·채번·CSV 등 핵심 불변식을 검증한다.
- Docker Engine 29.x 호환을 위해 `build.gradle`에서 Docker API 버전을 `1.44`로 고정한다(수정 시 주의).

## 6. 환경변수 (prod 프로필)

`prod` 프로필은 모든 접속 정보를 환경변수로 요구하며, 미설정 시 **기동 실패(fail-fast)**한다. 상세는 [`docs/deploy-guide.md`](docs/deploy-guide.md).

| 변수 | 필수 | 설명 |
|------|:---:|------|
| `SPRING_PROFILES_ACTIVE` | ✅ | `prod` |
| `DB_URL` | ✅ | `jdbc:postgresql://<host>:5432/<db>` |
| `DB_USERNAME` | ✅ | DB 계정 |
| `DB_PASSWORD` | ✅ | DB 비밀번호 |
| `STORAGE_LOCAL_DIR` | (기본 `/data/storage`) | 업로드 파일 영속 디렉토리 — **영속 볼륨 필수** |

## 7. 브랜치 구조

| 브랜치 | 용도 |
|--------|------|
| **`mvp`** | **최신 작업 브랜치** — 요구사항 파일 업로드 방식 MVP (SRS v2.x). 인수인계 기준 |
| `main` | v1.4 시점 안정판 (참고용 폴백) |
| `archive/v1.4-individual-req-entry` | 요구사항 항목별 등록 방식(구 MVP) 동결 |

> 새 세션·작업 시작 시 `git status`로 현재 브랜치 확인 후 `PROGRESS.md`로 진행 단계 파악.

## 8. 코드 구조 한눈에

단일 Spring Boot 앱. `com.softpuzzle.pm` 아래 도메인 패키지로 분리(account·project·deliverable·qa·dev·notify·search·admin·audit·member·dashboard·export·common·web). **패키지별 책임·핵심 클래스·마이그레이션 매핑은 [`docs/module-map.md`](docs/module-map.md)** 참조.

계층 규약: 얇은 `~Controller` → `@Transactional ~Service` → MyBatis `~Mapper`(XML은 `resources/mapper/`). enum은 ASCII 코드로 저장하고 UI에서 한글 라벨로 매핑한다. 인가는 라우트 가시성 + 서비스 레이어 멤버십/tier 검증(이중 방어).

## 9. 문서 인덱스

| 문서 | 내용 |
|------|------|
| `OVERVIEW.md` | **제품 소개·기능 개요·차별점·데모 보는 법** (비개발자도 이해 가능) |
| `PROGRESS.md` | **현재 진행 단계·전체 플로우 진행표·협의 루프 이력** (가장 먼저 볼 것) |
| `CLAUDE.md` | 프로젝트 규칙·브랜치·사용자 구조 |
| `docs/module-map.md` | 패키지·모듈 맵 (개발자 온보딩) |
| `docs/deploy-guide.md` | 배포·환경변수·운영 주의점 |
| `docs/requirements.md` | 요구사항 정의서 (SRS) |
| `docs/architecture.md` | 논리 아키텍처·보안·배포 흐름 |
| `docs/infrastructure.md` | 운영 인프라 구성 |
| `docs/erd.md` | ERD·테이블 정의 (Mermaid) |
| `docs/api-spec.md` | API 명세 (설계 계약 — 일부 구현과 차이, retrospective 참조) |
| `docs/class-sequence.md` | 클래스·시퀀스 다이어그램 |
| `docs/screen-design/index.html` | 화면 설계서 |
| `docs/prototype/index.html` | 인터랙티브 프로토타입 (디자인 기준) |
| `docs/deliverables.md` | 단계별 산출물 목록·파일 매핑 |
| `docs/retrospective/` | **baseline 드리프트 회고 — api-spec과 실제 구현 차이를 이해하려면 필독** |

## 10. 코딩 컨벤션

Java/Spring·프론트엔드 컨벤션, 커밋 메시지 규칙, 로깅 규칙은 글로벌 규칙(`~/.claude/CLAUDE.md`)을 따른다. 요약: 네이밍 카멜케이스·상수 UPPER_SNAKE, 스페이스 4칸·120자, K&R 중괄호, `@Transactional` 명시(읽기 `readOnly=true`), 와일드카드 import 금지, 커스텀 RuntimeException, Optional 사용, 커밋 `feat:`/`fix:`/`refactor:`/`docs:`/`chore:`/`test:`.
