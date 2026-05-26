# 모듈/패키지 맵 (프로그램 목록·단위 모듈 설명서)

> 산출물 No.15. 새 개발자가 "코드가 어디 있는지"를 빠르게 파악하기 위한 지도.
> 논리 아키텍처(레이어·보안·배포)는 [`architecture.md`](architecture.md), 데이터 모델은 [`erd.md`](erd.md) 참조.

## 1. 전체 구조

단일 Spring Boot 앱. 루트 패키지 `com.softpuzzle.pm` 아래 **도메인별 패키지**로 분리한다.

```
src/main/java/com/softpuzzle/pm/
├── SpProjectPortalApplication.java   # 부트 엔트리포인트
├── common/        # 횡단 공통 (응답 봉투·예외·보안·시드·스토리지·인증주체)
├── web/           # SSR 페이지 라우트 (Thymeleaf 뷰 반환)
├── account/       # 계정·고객사 조직·Spring Security UserDetails
├── member/        # 초대(스마트 분기)·초대 수락·비밀번호 재설정
├── project/       # 프로젝트·멤버십 가드·게이트·20단계 로드맵
├── deliverable/   # 산출물 슬롯·버전·파일·코멘트·검토 상태머신·활동
├── dev/           # 개발 트리거 (사전조건 게이트 검증)
├── qa/            # 테스트 케이스·결함 (채번·CSV·연결·첨부)
├── notify/        # 인앱 알림 (상단바 벨)
├── search/        # 통합 검색 (접근 가능 프로젝트 범위)
├── audit/         # 감사 로그 (append-only)
├── admin/         # 관리자 콘솔 (계정·전체 프로젝트·감사 조회)
├── dashboard/     # 내 프로젝트 대시보드 (KPI 집계)
└── export/        # 프로젝트 산출물 JSON Export
```

## 2. 계층 규약

```
~Controller (얇음, @RestController/@Controller)
    → ~Service (@Transactional, 비즈니스 로직·인가)
        → ~Mapper (MyBatis 인터페이스, XML = resources/mapper/*.xml)
            → PostgreSQL
```

- enum/상태값은 **ASCII 코드로 저장**(`team`/`client`/`admin`, `draft`/`pending`/`confirmed` 등)하고 UI에서 한글 라벨로 매핑.
- 인가는 **이중 방어**: 라우트 가시성(사이드바·리다이렉트) + 서비스 레이어 멤버십/tier 검증(`MembershipGuard`).
- AJAX 응답은 `{success, data}` / `{success, message}` 봉투(`common.ApiResponse`).

## 3. 패키지별 책임

### `common` — 횡단 공통
| 클래스 | 책임 |
|--------|------|
| `SecurityConfig` | Spring Security 세션 인증·BCrypt(strength 12)·CSRF(세션 토큰)·폼 로그인(`/login`→`/`)·`frameOptions=SAMEORIGIN`(파일 미리보기 iframe) |
| `CurrentUser` | SecurityContext의 인증 email → 도메인 `Account` 해석. `require()` |
| `MembershipGuard` | **서비스 레이어 인가 단일 진입점**. `assertCanView`/`assertMember`/`assertCanRequestReview`(팀)/`assertCanReview`(고객사)/`assertTeamMember`/`assertCanCreateProject`. admin=전역 읽기·쓰기 불가 |
| `ApiResponse` / `ApiException` | 응답 봉투 / 커스텀 RuntimeException(`forbidden`/`notFound` 등 팩토리) |
| `GlobalExceptionHandler` | `@RestControllerAdvice` — 예외 → HTTP 상태·에러 봉투. SSR 오류 페이지(403/404/500)와 분리 |
| `DataSeeder` | dev 시드(`local`/`dev`/`docker` 프로필, 멱등). 계정·샘플 프로젝트 3개. prod 미동작 |
| `Tokens` | 토큰 생성·해시 유틸 (초대·비밀번호 재설정 토큰은 해시 저장) |
| `storage/FileStorage` · `LocalFileStorage` | 파일 스토리지 추상화 + 로컬 구현. **S3 어댑터는 미구현**(추상화만 존재) |

> `MembershipGuard`는 `project` 패키지에 위치하지만 전 도메인이 의존하는 횡단 인가 컴포넌트다.

### `web` — SSR 페이지 라우트
- `PageController` — `/login`·`/`(대시보드)·`/projects/new`·`/account`·`/search`·`/admin/*`. 레이아웃 셸용 nav 속성(`navTier`·`navCanCreate`·`navScopeLabel`) 주입, tier 미달 시 리다이렉트.
- `ProjectPageController` — 프로젝트 컨텍스트 페이지(대시보드·산출물 슬롯·결함·TC·개발·설정·로드맵).
- `MeController` — 현재 사용자 정보 조회 API.
- 뷰 템플릿은 `resources/templates/`, 정적 자산은 `resources/static/{css,js}`.

### `account` — 계정·조직·인증
- `Account`(tier=team/client/admin, job, status, clientOrgId) · `ClientOrg`(고객사 조직).
- `PmUserDetailsService` — Spring Security `UserDetailsService` 구현(email 기반 로그인).
- 매퍼: `AccountMapper`, `ClientOrgMapper`.

### `member` — 초대·인증 플로우
- `MemberService` / `MemberController` — 멤버 초대 **스마트 분기**(신규=온보딩 초대 / 기존 계정=참여만 추가+알림 / 이미 참여=무동작), 고객사 N:M 참여.
- `AuthFlowController` — 초대 수락·비밀번호 재설정 화면 흐름.
- `PasswordResetService` + `PasswordResetToken(Mapper)` — 토큰 해시 저장·만료.
- `Invitation(Mapper)` — 초대 토큰(해시 저장).

### `project` — 프로젝트·게이트·로드맵
| 클래스 | 책임 |
|--------|------|
| `ProjectService` / `ProjectController` | 프로젝트 CRUD·멤버 추가·보관/삭제(Danger Zone). 생성 시 게이트 시드, 생성자 멤버 등록 |
| `ProjectMember(Mapper)` | 멤버십(소프트 제외). `existsActive`로 인가 판정 |
| `ProjectGate(Mapper)` | 게이트 상태(lock/wait/passed) — 컨펌 시 순차 해제 |
| `RoadmapService` | 20단계 진행 현황 로드맵(읽기 전용, SCR-RDM-001) |
| `Stages` | 단계 번호·게이트 메타 정의 |
| `MembershipGuard` | (위 `common` 항목 참조) |

### `deliverable` — 산출물 핵심 도메인 (가장 복잡)
승인/컨펌 워크플로우의 심장. **슬롯 → 버전(스냅샷) → 파일 자산** 구조.

| 클래스 | 책임 |
|--------|------|
| `SlotTypes` | 슬롯 종류·순서·선행 관계·게이트 매핑 **단일 정의처**. 순서: `requirements→ia→design→prototype→figma`, 게이트 `5/7/9/11/13` |
| `SlotService` / `SlotController` | 슬롯 조회·파일 업로드(스토리지 먼저→짧은 tx→커밋, 실패 시 고아 삭제)·외부 URL 자산·삭제 |
| `VersionService` | 새 버전 생성(v+1 draft, 이전 파일 사본, 과거 버전 immutable, 컨펌 시 자동 무효화) |
| `ReviewService` | **검토 상태머신**: draft→pending-review→confirmed/rejected + 회수. 컨펌 시 게이트 순차 진행, 행 `FOR UPDATE` 직렬화, 순서 위반 409 |
| `CommentService` / `CommentController` | 코멘트(버전 스냅샷별, 본인 수정/삭제·관리자 강제삭제·소프트삭제·IDOR 방지) |
| `SlotVersion` / `FileAsset` / `DeliverableSlot` / `ActivityEvent` (+ 각 Mapper) | 도메인 엔티티 |
| `dto/` | `SlotDetail`·`NewVersionRequest`·`CommentRequest`·`RejectRequest`·`AddUrlRequest` |

> REQ-WF-005 **선행 산출물 변경 배지**: 선행 슬롯 새 버전 시 하류 컨펌 슬롯에 배지 전파, `ackUpstream` 재스탬프로 해제.

### `dev` — 개발 트리거
- `DevRunService` / `DevRunController` — 개발 시작 트리거. **하드 사전조건**: 게이트 5·7·9·11·13 전부 통과해야 실행(미충족 시 409 `DEV_PRECONDITION` + 미통과 게이트 명시). team만 트리거. 실행 이력(`DevRun`).

### `qa` — 테스트 케이스·결함
| 클래스 | 책임 |
|--------|------|
| `TestCaseService` / `TestCaseController` | TC 단건·CSV 일괄 등록·상태(통과/실패/대기). team |
| `DefectService` / `DefectController` | 결함 등록(team+client)·심각도/상태·TC 양방향 연결·증거 첨부(스토리지-먼저) |
| `CodeSequenceMapper` | 프로젝트별 원자적 채번(`INSERT ON CONFLICT RETURNING`) — TC/결함 코드 |
| `DefectAttachment(Mapper)` | 결함 첨부 파일 |
| `dto/` | `CreateTestCaseRequest`·`CreateDefectRequest`·`TestCaseDetail`·`DefectDetail` 등 |

### `notify` · `search` · `audit` · `admin` · `dashboard` · `export`
| 패키지 | 책임 |
|--------|------|
| `notify` | 인앱 알림(검토 요청→고객사, 컨펌/반려→팀). 이벤트 tx 내 생성, 상단바 벨+미읽음 배지+읽음 |
| `search` | 통합 검색 — 접근 가능 프로젝트 범위 내 프로젝트·TC·결함(비멤버 제외) |
| `audit` | 감사 로그(append-only). `CREATE_PROJECT`·`CONFIRM`·`REJECT`·`TRIGGER_DEV` 등 + tier 스냅샷 |
| `admin` | 관리자 콘솔 — 계정 관리(생성 임시비번·활성/비활성, 해시 미노출)·전체 프로젝트·감사 조회. admin 전용 |
| `dashboard` | 내 프로젝트 대시보드(집계 KPI·산출물·활동) |
| `export` | 프로젝트 산출물 JSON Export(프로젝트·게이트·산출물·TC·결함 요약, 멤버/IDOR 검증) |

## 4. DB 마이그레이션 (Flyway)

`resources/db/migration/` — Flyway가 스키마 소유(세션 스키마 포함, 자동 init off).

| 버전 | 파일 | 도입 도메인 |
|------|------|------------|
| V1 | `V1__spring_session.sql` | Spring Session JDBC 스키마(인덱스 포함) |
| V2 | `V2__account.sql` | 계정·고객사 조직 (tier CHECK) |
| V3 | `V3__project_core.sql` | 프로젝트·멤버십(소프트 제외)·초대·게이트 |
| V4 | `V4__deliverables.sql` | 슬롯(복합 FK)·버전(검토 일관성 CHECK·pending 부분 UQ)·파일 자산 |
| V5 | `V5__activity_event.sql` | 활동 이벤트 |
| V6 | `V6__comment.sql` | 코멘트 (slot_version/defect XOR CHECK) |
| V7 | `V7__dev_run.sql` | 개발 트리거 실행 이력 |
| V8 | `V8__test_defect.sql` | 채번·TC·결함·연결·첨부 |
| V9 | `V9__notification_audit.sql` | 알림·감사 로그 |
| V10 | `V10__password_reset.sql` | 비밀번호 재설정 토큰 |

> 스키마 변경은 새 `V11__*.sql` 추가로만(기존 마이그레이션 수정 금지 — 적용된 체크섬 깨짐).

## 5. 테스트 맵

`src/test/java/com/softpuzzle/pm/` — Testcontainers PostgreSQL 통합 테스트(`*IT`).

| 테스트 | 검증 대상 |
|--------|----------|
| `WalkingSkeletonTest` | 로그인 + 보호 페이지 + AJAX POST(CSRF) end-to-end |
| `ProjectServiceIT` · `ProjectWorkflowIT` · `ProjectSettingsIT` | 인가 매트릭스·게이트 시드·워크플로우·설정 |
| `SlotServiceIT` · `VersionServiceIT` · `ReviewServiceIT` · `CommentServiceIT` | 산출물 업로드·버전·검토 상태머신·코멘트 |
| `DevRunServiceIT` | 개발 트리거 사전조건 차단/허용 |
| `QaServiceIT` | 채번·CSV·결함 TC 링크·첨부 |
| `MemberAuthIT` | 초대 스마트 분기·인증 |
| `NotifySearchIT` | 검토 알림 대상·검색 키워드·비멤버 제외 |
| `DashboardServiceIT` · `AdminExportIT` | 대시보드 집계·관리자/Export 인가 |
| `AccountMapperIT` | 매퍼 단위 |
