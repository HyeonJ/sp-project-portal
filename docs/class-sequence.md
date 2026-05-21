# 클래스 · 시퀀스 다이어그램

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SoftPuzzle PM |
| 버전 | v0.1 (초안 — 계층 구조 + 핵심 워크플로우 흐름) |
| 작성일 | 2026-05-21 |
| 기준 | ERD(`erd.md` v1.2) · API 명세서(`api-spec.md` v0.3) · 화면 설계서 |
| 스택 | Spring Boot · MyBatis · PostgreSQL · JWT · S3 |
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
        +boolean upstreamChanged
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
    DeliverableService ..> SlotVersionMapper
    DeliverableService ..> FileAssetMapper
    DeliverableService ..> GateService
    DeliverableService ..> NotificationService
    DeliverableService ..> ActivityRecorder
    DeliverableService ..> FileStorageService
    DeliverableSlot "1" --> "*" SlotVersion
    SlotVersion "1" --> "*" FileAsset
```

> 공통: 모든 Controller는 `@RestController` + `ApiResponse<T>` 봉투, 예외는 `@RestControllerAdvice`가 `{success:false, code, message}`로 변환. Service는 `@Transactional`(읽기는 `readOnly=true`). 인증은 `JwtAuthFilter` → `SecurityContext`. 동시성 민감 동작은 `SlotVersionMapper.lockForUpdate`(SELECT … FOR UPDATE, api-spec §14).

---

## 2. 시퀀스 다이어그램

### 2-1. 로그인 · 토큰 회전

```mermaid
sequenceDiagram
    autonumber
    actor U as 사용자
    participant C as AuthController
    participant S as AuthService
    participant M as AccountMapper
    participant R as RefreshTokenMapper
    U->>C: POST /auth/login {email, password}
    C->>S: login(email, password)
    S->>M: findByEmail(lower(email))
    M-->>S: Account
    S->>S: 비밀번호 검증 (실패 시 시도 카운트++, 5회=잠금)
    S->>R: insert(refresh hash, expiresAt)
    S-->>C: accessToken(JWT) + account
    C-->>U: 200 {accessToken, account} + Set-Cookie(refresh)
    Note over U,R: 이후 access 만료 시
    U->>C: POST /auth/refresh (refresh 쿠키)
    C->>S: refresh(token)
    S->>R: findByHash(hash)
    alt 이미 폐기된 토큰 재사용
        S->>R: revokeAll(accountId) — 탈취 의심
        S-->>C: 401 reuse_detected
    else 정상
        S->>R: revoke(old) + insert(new)
        S-->>C: new accessToken + Set-Cookie(new refresh)
    end
```

### 2-2. 검토 요청 → 컨펌 / 반려 (게이트 해제)

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀(PM)
    actor CL as 고객사
    participant C as DeliverableController
    participant S as DeliverableService
    participant VM as SlotVersionMapper
    participant N as NotificationService
    participant A as ActivityRecorder
    participant G as GateService
    T->>C: POST .../versions/{vno}/review-request
    C->>S: requestReview(...)
    S->>VM: lockForUpdate(versionId)
    S->>S: assert status==draft
    S->>VM: updateStatus(pending-review) + review_requested_*
    S->>A: record(review_requested)
    S->>N: notifyReviewRequested → 고객사
    S-->>T: 200
    CL->>C: POST .../versions/{vno}/confirm
    C->>S: confirm(...)
    S->>VM: lockForUpdate(versionId)
    S->>S: assert status==pending-review
    S->>G: assertConfirmableInOrder(stage) — 게이트 순서
    S->>VM: updateStatus(confirmed) + reviewed_* + confirmedUpstreamVersionId 스탬프
    S->>G: openGate(stage)
    S->>A: record(confirmed)
    S->>N: notifyConfirmed → 팀
    S-->>CL: 200
    Note over CL,S: 반려 시 — status=rejected + reason은 activity_event(rejected).body(영구)
```

### 2-3. 새 버전 만들기 (컨펌 무효화 + 선행 배지 전파, REQ-WF-005)

```mermaid
sequenceDiagram
    autonumber
    actor T as 프로젝트팀
    participant C as DeliverableController
    participant S as DeliverableService
    participant VM as SlotVersionMapper
    participant SM as SlotMapper
    participant A as ActivityRecorder
    T->>C: POST .../slots/{slot}/versions {changeSummary}
    C->>S: createVersion(slot, changeSummary)
    S->>SM: lockForUpdate(slotId)
    S->>VM: findLatest(slotId)
    S->>S: 현재 파일 묶음 사본 → v+1 draft 생성(logicalKey 유지)
    S->>VM: insert(newVersion, draft)
    alt 이전 상태 == confirmed
        S->>A: record(invalidate) — 컨펌 자동 무효화
    end
    S->>A: record(version_created, changeSummary)
    S->>SM: 하류 슬롯 조회(UPSTREAM 체인)
    loop 직속 하류가 confirmed면
        S->>SM: setUpstreamChanged(downstreamSlot, true)
    end
    S-->>T: 201 {versionNo, status:draft}
    Note over T,S: 하류 슬롯 진입 시 '선행 산출물 변경·검토 권장' 배지 → ackUpstream 또는 새 버전으로 해소
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
    participant FM as FileAssetMapper
    participant S3 as S3
    T->>C: POST .../assets/files (multipart)
    C->>S: addFile(versionId, file)
    S->>S: assert version==draft
    S->>F: upload(randomKey, stream)
    F->>S3: putObject
    S->>FM: insert(fileAsset)
    alt DB insert 실패
        S->>F: deleteQuietly(key) — orphan 보상
    end
    S-->>T: 201 {assetId}
    Note over T,S3: 다운로드 — 권한 확인 후 presigned URL 발급
    T->>C: GET .../assets/{assetId}/download
    C->>S: download(assetId)
    S->>F: presignedDownloadUrl(key, 5m)
    S-->>T: 200 {url, expiresAt}
    T->>S3: GET presigned url (직접)
```

---

## 3. 노트

- **계층 일관성**: TC·결함·프로젝트·계정 등 다른 도메인도 `Controller → Service(@Transactional) → Mapper` 동일 패턴. 본 문서는 가장 복잡한 산출물 워크플로우만 대표로 명세.
- **트랜잭션·동시성**: 상태 전이(검토 요청/회수/컨펌/반려/새 버전)는 `SELECT … FOR UPDATE`로 직렬화 (api-spec §14, ERD 부분 유니크 보완).
- **반려 사유**·**활동 이력**: `activity_event`(append-only)에 기록, 코멘트와 분리(REQ-WF-004/CMT-002).
- **보안**: refresh 회전·재사용 탐지, 초대/토큰 해시 저장, presigned 단명 URL — ERD·api-spec과 동일.
- **미작성(후속 필요 시)**: 개발 트리거(dev_run) 사전조건 검증 시퀀스, 검색 인덱싱, 알림 fan-out 상세.
