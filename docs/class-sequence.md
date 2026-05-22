# 클래스 · 시퀀스 다이어그램

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SoftPuzzle PM |
| 버전 | v0.4 (문서 정합 — 로그인 시퀀스를 Spring Security 세션으로 정렬, JWT/refresh 제거) · v0.3 (dev_run 트리거 시퀀스) · v0.2 (코덱스 리뷰) |
| 작성일 | 2026-05-21 |
| 기준 | ERD(`erd.md` v1.2) · API 명세서(`api-spec.md` v0.3) · 화면 설계서 |
| 스택 | Spring Boot · MyBatis · PostgreSQL · Thymeleaf SSR · 세션(Spring Security + Spring Session JDBC) · S3 |
| 관련 단계 | 17~18 (선택 산출물 — 객체 구조·처리 흐름 명세) |

> 산출물 No.13(선택). ERD/API와 같은 Mermaid 마크다운. **전 도메인을 다 그리지 않고**, 가장 복잡한 **산출물 슬롯 워크플로우**를 대표로 계층 구조를 보이고, 핵심 흐름만 시퀀스로 명세한다. 다른 도메인(TC·결함·계정 등)도 동일한 `Controller → Service → Mapper` 계층을 따른다.

---

## 1. 계층 아키텍처 — 클래스 다이어그램 (대표: 산출물 도메인)

> 얇은 Controller(검증·DTO 변환) → 트랜잭션 경계 Service(비즈니스 규칙) → MyBatis Mapper(SQL). 횡단 관심사(인증·알림·활동기록·파일저장)는 별도 컴포넌트로 분리.

```mermaid
classDiagram
    class DeliverableController {
        +getSlot(projectId, slot) SlotResponse
        +createVersion(projectId, slot, req) VersionResponse
        +addFile(projectId, slot, vno, file) AssetResponse
        +addLink(projectId, slot, vno, req) AssetResponse
        +requestReview(projectId, slot, vno)
        +recallReview(projectId, slot, vno)
        +confirm(projectId, slot, vno)
        +reject(projectId, slot, vno, req)
        +ackUpstream(projectId, slot)
    }
    class DeliverableService {
        <<@Transactional>>
        +getSlotDetail(...) SlotDetail
        +createVersion(...) SlotVersion
        +requestReview(...)
        +confirm(...)
        +reject(...)
        +ackUpstream(...)
        -invalidateIfConfirmed(slot)
        -propagateUpstreamBadge(slot)
    }
    class GateService {
        +assertConfirmableInOrder(projectId, stage)
        +openGate(projectId, stage, by)
    }
    class FileStorageService {
        +upload(key, stream) StoredObject
        +presignedDownloadUrl(key, ttl) String
        +deleteQuietly(key)
    }
    class NotificationService {
        +notifyReviewRequested(version)
        +notifyConfirmed(version)
        +notifyRejected(version, reason)
    }
    class ActivityRecorder {
        +record(slotId, type, actor, body)
    }
    class SlotVersionMapper {
        <<MyBatis>>
        +findLatest(slotId) SlotVersion
        +insert(version)
        +updateStatus(id, status)
        +lockForUpdate(id) SlotVersion
    }
    class FileAssetMapper {
        <<MyBatis>>
        +findByVersion(versionId) List~FileAsset~
        +insert(asset)
        +delete(id)
    }
    class DeliverableSlot {
        +Long id
        +SlotType slotType
        +SlotStatus status
        +Long currentVersionId
        +isUpstreamChanged() boolean
    }
    class SlotMapper {
        <<MyBatis>>
        +lockForUpdate(slotId) DeliverableSlot
        +updateCurrentVersionAndStatus(slotId, versionId, status)
        +findDownstream(slotType) DeliverableSlot
    }
    class SlotVersion {
        +Long id
        +int versionNo
        +SlotStatus status
        +String changeSummary
        +Long confirmedUpstreamVersionId
        +canRequestReview() boolean
        +canConfirm() boolean
    }
    class FileAsset {
        +Long id
        +AssetKind kind
        +String logicalKey
        +int position
        +String storageKey
        +String externalUrl
    }

    DeliverableController ..> DeliverableService
    DeliverableService ..> SlotMapper
    DeliverableService ..> SlotVersionMapper
    DeliverableService ..> FileAssetMapper
    DeliverableService ..> GateService
    DeliverableService ..> NotificationService
    DeliverableService ..> ActivityRecorder
    DeliverableService ..> FileStorageService
    DeliverableSlot "1" --> "*" SlotVersion
    SlotVersion "1" --> "*" FileAsset
```

> 공통: 모든 Controller는 `@RestController` + `ApiResponse<T>` 봉투, 예외는 `@RestControllerAdvice`가 `{success:false, code, message}`로 변환. Service는 `@Transactional`(읽기는 `readOnly=true`). 인증은 `JwtAuthFilter` → `SecurityContext`. 동시성 민감 동작은 `lockForUpdate`(SELECT … FOR UPDATE, api-spec §14).
>
> **트랜잭션 자세** (코덱스 리뷰): `GateService`·`ActivityRecorder`는 **호출자 트랜잭션에 참여**(독립 tx 아님). `NotificationService`는 **DB 알림 row 기록(tx 내) + 외부 발송(메일·푸시)은 커밋 후**(`@TransactionalEventListener(AFTER_COMMIT)` 또는 outbox)로 분리 — tx 안에서 외부 발송 금지. `FileStorageService`는 인프라 경계로 워크플로우 규칙을 갖지 않음.
>
> **파생 필드**: `DeliverableSlot.isUpstreamChanged()`는 **저장 컬럼이 아니라 파생** — 하류 슬롯의 현재 컨펌 버전 `confirmed_upstream_version_id` ≠ 직속 선행 슬롯의 최신 컨펌 버전이면 true(읽기 시 계산). 중복 저장 금지(ERD에 컬럼 없음). *프로토타입은 데모 편의상 flag로 표현*.
>
> **인가**: Controller/Security 레이어가 Service 호출 **전에** 프로젝트 멤버십(`project_member`, `left_at IS NULL`)·tier(admin=읽기 전용)를 검증(api-spec §1-5). Service는 인가 통과를 전제.

---

## 2. 시퀀스 다이어그램

### 2-1. 로그인 · 로그아웃 (Spring Security 세션)

```mermaid
sequenceDiagram
    autonumber
    actor U as 사용자
    participant C as AuthController
    participant SS as Spring Security
    participant S as AuthService
    participant M as AccountMapper
    participant SJ as Spring Session JDBC
    U->>C: POST /auth/login {email, password}
    C->>S: authenticate(email, password)
    S->>M: findByEmail(lower(email))
    M-->>S: Account
    S->>S: BCrypt 검증 (실패 시 카운트++, 5회=10분 잠금)
    alt 인증 실패
        S-->>U: 400 (ACCOUNT_LOCKED / 인증 실패)
    else 성공
        S->>SS: SecurityContext 인증 설정
        SS->>SJ: 세션 생성·영속 (SPRING_SESSION)
        SS-->>U: 200 {account} + SESSION 쿠키(HttpOnly·Secure·SameSite=Lax)
    end
    Note over U,SJ: 이후 요청은 SESSION 쿠키로 인증. 30분 무활동 자동 만료(REQ-AUT-009). 상태 변경 요청은 CSRF 토큰 필요
    U->>C: POST /auth/logout
    C->>SS: 세션 무효화 (REQ-AUT-008)
    SS->>SJ: delete(session)
    SS-->>U: 200 → 로그인 페이지
```

### 2-2. 검토 요청 → 컨펌 / 반려 (게이트 해제)

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀(PM)
    actor CL as 고객사
    participant C as DeliverableController
    participant S as DeliverableService
    participant SM as SlotMapper
    participant VM as SlotVersionMapper
    participant G as GateService
    participant A as ActivityRecorder
    participant N as NotificationService
    Note over T,N: 검토 요청 (draft → 검토중)
    T->>C: POST .../versions/{vno}/review-request
    C->>S: requestReview(...)
    S->>VM: lockForUpdate(versionId)
    S->>S: assert version.status == draft
    S->>VM: updateStatus(pending-review) + review_requested_by/at
    S->>SM: updateCurrentVersionAndStatus(slot, pending-review)
    S->>A: record(review_requested)
    Note over S,N: ── COMMIT ──
    S--)N: notifyReviewRequested → 고객사 (after-commit)
    S-->>T: 200
    Note over T,N: 컨펌 (검토중 → 컨펌, 게이트 해제)
    CL->>C: POST .../versions/{vno}/confirm
    C->>S: confirm(...)
    S->>G: lock project_gate rows (결정적 순서)
    S->>VM: lockForUpdate(versionId)
    S->>S: assert status == pending-review
    S->>G: assertConfirmableInOrder(stage)
    S->>S: 직속 선행 슬롯 최신 컨펌 버전 → confirmedUpstreamVersionId (요구사항=NULL)
    S->>VM: updateStatus(confirmed) + reviewed_by/at + confirmedUpstreamVersionId
    S->>SM: updateCurrentVersionAndStatus(slot, confirmed)
    S->>G: openGate(stage)
    S->>A: record(confirmed)
    Note over S,N: ── COMMIT ──
    S--)N: notifyConfirmed → 팀 (after-commit)
    S-->>CL: 200
    Note over T,N: 반려 — status=rejected + SM 동기화 + activity(rejected).body=사유(영구) + 알림(after-commit)
    Note over T,N: 검토 회수(review-recall) — assert 검토중 → version·slot draft 복귀 + activity(review_recalled)
```

### 2-3. 새 버전 만들기 (컨펌 무효화 + 선행 배지 전파, REQ-WF-005)

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀
    participant C as DeliverableController
    participant S as DeliverableService
    participant SM as SlotMapper
    participant VM as SlotVersionMapper
    participant A as ActivityRecorder
    T->>C: POST .../slots/{slot}/versions {changeSummary}
    C->>S: createVersion(slot, changeSummary)
    S->>SM: lockForUpdate(slotId)
    S->>VM: findLatest(slotId)
    S->>S: 현재 파일 묶음 사본 → v+1 draft (logicalKey 유지, 배지 초기화)
    S->>VM: insert(newVersion, draft)
    S->>SM: updateCurrentVersionAndStatus(slot, newVersionId, draft)
    alt 직전 current 버전이 confirmed
        S->>A: record(invalidate) — 과거 버전 status는 confirmed 유지, 슬롯 포인터·status만 새 draft로
    end
    S->>A: record(version_created, changeSummary)
    S-->>T: 201 {versionNo, status:draft}
    Note over T,A: 선행 배지는 저장 안 함(파생). 하류 컨펌 슬롯이 자기 confirmed_upstream_version_id ≠ 선행 최신 컨펌 버전이면 읽기 시 표시. **새 draft 생성만으론 트리거 안 됨** — 이 슬롯이 재컨펌될 때 비로소 하류에서 보임(1-hop, 재컨펌마다 체인 전파). 해소 = ackUpstream(재-스탬프) 또는 하류 재컨펌.
```

### 2-4. 멤버 초대 (스마트 분기 — 신규 / 기존)

```mermaid
sequenceDiagram
    autonumber
    actor T as 팀/관리자
    participant C as MemberController
    participant S as InvitationService
    participant AM as AccountMapper
    participant PM as ProjectMemberMapper
    participant IM as InvitationMapper
    participant N as NotificationService
    T->>C: POST .../members/invite {email, type}
    C->>S: invite(projectId, email, type)
    S->>AM: findByEmail(lower(email))
    alt 신규 이메일
        S->>IM: insert(invitation, token_hash, expiresAt)
        S->>N: 온보딩 초대 메일(원문 토큰 링크)
        S-->>T: 201 {result: invited}
    else 기존 계정 · 미참여
        S->>PM: insert(projectMember)
        S->>N: 참여 알림 메일(비번 설정 없음)
        S-->>T: 200 {result: joined_existing}
    else 이미 참여
        S-->>T: 200 {result: already_member}
    end
```

### 2-5. 파일 업로드(S3) · presigned 다운로드

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀
    participant C as DeliverableController
    participant S as DeliverableService
    participant F as FileStorageService
    participant VM as SlotVersionMapper
    participant FM as FileAssetMapper
    participant S3 as S3
    T->>C: POST .../assets/files (multipart)
    C->>S: addFile(versionId, file)
    S->>F: upload(randomKey, stream) — 락 없이 먼저(I/O)
    F->>S3: putObject
    Note over S,FM: ── 짧은 트랜잭션 (S3 putObject 중 row lock 점유 금지) ──
    S->>VM: lockForUpdate(versionId) + assert draft
    S->>FM: insert(fileAsset, position)
    alt 트랜잭션 실패(락·검증·insert)
        S->>F: deleteQuietly(key) — S3 orphan 보상 삭제
    end
    S-->>T: 201 {assetId}
    Note over T,S3: 다운로드 — 권한 확인 후 presigned URL 발급
    T->>C: GET .../assets/{assetId}/download
    C->>S: download(assetId)
    S->>F: presignedDownloadUrl(key, 5m)
    S-->>T: 200 {url, expiresAt}
    T->>S3: GET presigned url (직접)
```

### 2-6. 개발 시작 트리거 (사전조건 검증 → 코드 자동 생성, REQ-DEV-001)

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀(PM)
    participant C as DevRunController
    participant S as DevRunService
    participant P as PreconditionChecker
    participant GM as ProjectGateMapper
    participant DM as DevRunMapper
    participant A as ActivityRecorder
    participant Q as CodeGenJob (async)
    T->>C: POST /projects/{id}/dev-runs
    C->>S: trigger(projectId)
    S->>P: assertDevReady(projectId)
    P->>GM: 게이트 5·7·9·11·13 상태 조회
    P->>P: 내부 산출물 등록 확인 (ERD·API·아키텍처·시스템구성도·화면설계서·Figma)
    alt 사전조건 미충족
        P-->>S: 실패(미충족 항목)
        S-->>T: 409 {code: DEV_PRECONDITION, fieldErrors}
    else 충족
        S->>DM: insert(devRun, result=pending)
        S->>A: record(dev_triggered)
        Note over S,Q: ── COMMIT 후 ──
        S--)Q: enqueue 코드 자동 생성 (입력: 확정 SRS·IA·시안·프로토타입·ERD·API·Figma)
        S-->>T: 202 {devRunId, status: pending}
        Q-->>DM: 완료 시 result=success / 실패 시 fail+reason
    end
    Note over T,Q: 코드 생성은 비동기(202) — dev_run 이력으로 추적(REQ-DEV-001 핵심: 수동 코딩 아닌 자동 생성)
```

---

## 3. 노트

- **계층 일관성**: TC·결함·프로젝트·계정 등 다른 도메인도 `Controller → Service(@Transactional) → Mapper` 동일 패턴. 본 문서는 가장 복잡한 산출물 워크플로우만 대표로 명세.
- **트랜잭션·동시성**: 상태 전이(검토 요청/회수/컨펌/반려/새 버전)는 `SELECT … FOR UPDATE`로 직렬화 (api-spec §14, ERD 부분 유니크 보완). **컨펌은 버전 락만으론 부족** — `project_gate`/`project` 행을 결정적 순서로 락해 게이트 순서 동시성 보장. **외부 알림은 커밋 후** 발송.
- **용어 매핑**: API `POST /upstream-review` ↔ 클래스 `DeliverableService.ackUpstream(slot)` — 하류 슬롯의 `confirmed_upstream_version_id`를 현재 선행 최신 컨펌 버전으로 **재-스탬프**(배지 파생 해소) + `activity_event.upstream_reviewed`.
- **자산 삭제 보상**: `FileAssetMapper.delete`는 **draft 한정**, `kind=file`이면 DB row 삭제 + S3 object 삭제(또는 GC 큐), `kind=url`이면 row만.
- **반려 사유**·**활동 이력**: `activity_event`(append-only)에 기록, 코멘트와 분리(REQ-WF-004/CMT-002).
- **보안**: **Spring Security 세션 인증**(JWT 아님), 로그아웃=세션 무효화·30분 만료, CSRF 토큰(상태 변경), 초대/재설정 토큰 해시 저장, presigned 단명 URL — ERD·api-spec과 동일.
- **구현됨(MVP)**: TC 실패→결함 등록·양방향 연결/해제, TC CSV import(클라이언트 파싱·미리보기 후 일괄 등록), TC 수정·담당자 단건/일괄 지정(팀 QA), 결함 수정(팀 또는 본인 등록 고객사·담당자는 팀만). 모두 단순 CRUD + 서비스 가드.
- **미작성(후속 필요 시)**: 검색 인덱싱, 알림 fan-out 상세. (단순 CRUD·JIT로 충분)
