# API 명세서

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SoftPuzzle PM |
| 버전 | **v1.3 (전체 구현 정합 — §2~§11 코드 대조 동기화)**. 슬롯 경로 평탄화(`/versions/{vno}/assets/*`→`/{slot}/files·urls`), `upstream-review`→`ack-upstream`, 알림 read `PATCH`→`POST`, `audit-logs`→`audit`, search 전역화(`/api/search`), Export 동기 단건(`GET /export`), 인증/계정 경로 정정. **미구현은 `🔲 미구현(MVP 범위 외)`로 표기** — 동결 스펙(v1.0)과 실제 구현이 광범위하게 드리프트해 일괄 정합. |
| 이전 버전 | v1.2 (TC·결함 §9~10 동기화) · v1.1 (세션 SSR 정렬) · v1.0 (마감 baseline) · v0.3 (코덱스 리뷰) · v0.2 · v0.1 |
| 작성일 | 2026-05-21 |
| 기준 | ERD(`erd.md` v1.3) · 화면 설계서(`screen-design/` v1.5) · SRS(`requirements.md`) · 아키텍처(`architecture.md` v0.2) |
| 스택 | Spring Boot · MyBatis · PostgreSQL · Thymeleaf SSR + jQuery · 세션(Spring Security + Spring Session JDBC) |
| 관련 단계 | 12~13 (화면 설계서·ERD 안정 후, 13단계 종료 전 v1.0) |

> 산출물 No.11. **설계 시점 명세는 이 문서(사람이 읽는 계약)**, **런타임 Swagger UI는 구현 시 springdoc-openapi가 코드에서 자동 생성**한다 (이중관리 회피). 화면 설계서 컨트롤·ERD 변경 시 동기화.
>
> **v1.0 마감 기준**: 화면 설계서 v1.5의 모든 화면·컨트롤에 대응 엔드포인트 존재(커버리지 점검 완료), ERD v1.2 정합, 미결 0, 코덱스 리뷰 반영. **개발(14단계) 입력 baseline으로 동결** — 이후 변경은 버전 증가로 추적. 엔드포인트별 상세 스키마(요청/응답 전체 필드)는 구현 시 springdoc 어노테이션이 SoT.

---

## 1. 공통 규약

### 1-1. 기본
- **프런트 = SSR Thymeleaf + jQuery**: 화면 진입은 Thymeleaf 컨트롤러가 HTML 렌더, **본 명세의 JSON 엔드포인트는 jQuery AJAX·상태 변경 액션용**(전부 분리된 REST API를 소비하는 SPA가 아님). 인증은 세션 쿠키(§1-4).
- **Base URL**: JSON 엔드포인트 `/api`. (페이지 라우트는 `/projects/...` 등 HTML)
- **포맷**: JSON 요청/응답 `application/json` (파일 업로드만 `multipart/form-data`)
- **시각**: ISO-8601 UTC (`2026-05-21T07:20:00Z`), 표시는 클라이언트가 KST 변환

### 1-2. 응답 봉투 (글로벌 규칙)
```jsonc
// 성공
{ "success": true, "data": { /* ... */ } }
// 에러 — code(머신 식별)·message(사람용)·fieldErrors(검증 실패 시)
{ "success": false, "code": "GATE_OUT_OF_ORDER", "message": "이전 게이트(Gate 7) 미통과로 컨펌할 수 없습니다.",
  "fieldErrors": { "newPassword": "8자 이상·영문·숫자·특수문자" } }
```
`code` 예: `VALIDATION_FAILED`(400) · `UNAUTHORIZED`(401) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `STATE_CONFLICT`/`GATE_OUT_OF_ORDER`/`DUPLICATE`(409). `fieldErrors`는 검증 실패 시에만.
목록은 `data`에 페이지 정보 포함:
```jsonc
{ "success": true, "data": { "items": [ ... ], "page": 1, "size": 20, "total": 134 } }
```

### 1-3. HTTP 상태 코드
| 코드 | 의미 |
|------|------|
| 200 | 성공 |
| 201 | 생성 성공 |
| 400 | 잘못된 요청(검증 실패) |
| 401 | 미인증(토큰 없음·만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌(게이트 순서 위반·상태 불일치·중복) |
| 500 | 서버 에러 |

### 1-4. 인증
- **세션 인증**: **Spring Security 세션** — 로그인 시 서버가 세션 생성, `SESSION` 쿠키(**HttpOnly · Secure · SameSite=Lax**) 발급. 세션 영속 = **Spring Session JDBC**(`SPRING_SESSION`). JWT·Bearer·refresh **미사용**(SRS REQ-NFR-001·아키텍처).
- **만료**: 30분 무활동 자동 만료(REQ-AUT-009), 로그아웃 시 즉시 무효화(REQ-AUT-008). 미인증·만료 = 401 → 로그인으로.
- **CSRF**: 상태 변경 요청(POST/PUT/PATCH/DELETE)은 **CSRF 토큰** 필요(Spring Security CSRF) — SSR 폼은 hidden 필드, jQuery AJAX는 `X-CSRF-TOKEN` 헤더.
- **토큰 해시**: 초대·비밀번호 재설정 토큰은 DB에 **해시로만** 저장(원문은 메일 링크에만, 1회용).

### 1-5. 권한 (3-tier)
`admin`(관리자) / `team`(프로젝트팀) / `client`(고객사).
- **team·client**: 프로젝트 스코프 리소스는 **참여(`project_member`, `left_at IS NULL`)** 기준. 위반 시 403.
- **admin**: 프로젝트 멤버가 아니어도 **전 프로젝트 조회·다운로드·감사(읽기 전용)** 가능. **편집·새 버전·컨펌/반려·초대 외 쓰기는 불가**(계정 관리·감사 제외). 즉 admin = 전역 read + 계정/감사 write.

(MVP는 tier 단위, 팀 내 직무 RBAC 제외 — SRS)

### 1-6. 페이지네이션·정렬
- **모든 GET 목록**은 `?page=1&size=20&sort=field,dir` 지원(page는 **1-based**, 서버가 Spring Pageable 0-based로 변환).
- `sort` 필드는 **화이트리스트**만 허용(임의 컬럼명 거부 — SQL injection 차단).
- 검색·필터는 도메인별 쿼리 파라미터.

### 1-7. 코드값
요청/응답의 enum은 **ASCII 머신 코드**(ERD §5) — 예: `status=passed`, `tier=client`. UI 한글 라벨 매핑은 클라이언트 책임.

---

## 2. 인증 · 온보딩

> 로그인/로그아웃은 **Spring Security 폼 인증**(JSON 엔드포인트 아님). 비밀번호 재설정·온보딩만 `/api` JSON.

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/login` (폼) | 공개 | **Spring Security 폼 로그인** — `username`·`password` 폼 전송 → 세션 생성. 실패 잠금(REQ-AUT-007) |
| POST | `/logout` (폼) | 인증 | Spring Security 폼 로그아웃 → 세션 무효화(REQ-AUT-008) |
| POST | `/api/password/reset-request` | 공개 | `{email}` → 재설정 메일 발송(존재 여부 노출 안 함) |
| POST | `/api/password/reset` | 공개 | `{token, newPassword}` → 비밀번호 설정, 토큰 무효화 |
| GET | `/invite/accept` (페이지) | 공개 | 초대 정보는 **SSR 페이지**가 렌더 (별도 JSON 조회 없음) |
| POST | `/api/invite/accept` | 공개 | 온보딩 수락 — 토큰은 **요청 본문**(`{token, ...}`) → 계정 활성. 토큰 1회용 |

> v1.0~v1.1 스펙은 `/api/auth/login`·`/auth/invitations/{token}/accept`(토큰 path) 형태였으나, 실제는 Spring Security 폼 로그인 + `/api/invite/accept`(토큰 body)로 구현됨.

---

## 3. 계정 (`/api/me`, `/api/admin/accounts`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/me` | 인증 | 현재 계정(이름·이메일·tier·job·참여 프로젝트) |
| PATCH | `/me/password` | 인증 | 🔲 미구현(MVP 범위 외) — 로그인 사용자 비밀번호 변경 |
| GET | `/admin/accounts` | admin | 계정 목록 `?tier=&status=&q=` (팀/고객사) |
| POST | `/admin/accounts` | admin | 계정 초대 (신규=온보딩 / 기존=참여 추가, 스마트 분기). *스펙 v1.0의 `/accounts/invite`에서 경로 변경* |
| PATCH | `/admin/accounts/{id}/status` | admin | 활성/비활성 토글. *스펙 v1.0의 일반 수정(`{name?,job?,status?}`)이 아니라 **status 전용***. 이름·직무 편집 🔲 미구현 |
| POST | `/admin/accounts/{id}/resend-invite` | admin | 🔲 미구현(MVP 범위 외) — 초대 재발송 |
| PUT | `/admin/clients/{id}/projects` | admin | 🔲 미구현(MVP 범위 외) — 고객사 참여 프로젝트 일괄 편집 |

---

## 4. 프로젝트 (`/api/projects`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects` | 인증 | 목록(역할별: admin 전체 / team 담당 / client 참여). `?status=&q=&sort=` |
| POST | `/projects` | team | 생성 `{name, clientOrgName, type, startDate?, endDate?, description?}` |
| GET | `/projects/{id}` | 참여자 | 기본 정보 |
| PATCH | `/projects/{id}` | team | 설정 편집 `{name?, type?, startDate?, endDate?, description?}` (생성일·ID 불변, 감사 기록) |
| GET | `/projects/{id}/dashboard` | 참여자 | 대시보드 집계(현재 단계·게이트·진행률·미처리 항목·최근 활동·산출물 요약) |
| GET | `/projects/{id}/roadmap` (페이지) | 참여자 | 진행 현황(20단계·6게이트·8마일스톤). **SSR 페이지로만 제공** — `/api` JSON 아님 |
| GET | `/projects/{id}/gates` | 참여자 | 게이트 상태(5·7·9·11·13·18) |
| POST | `/projects/{id}/uat-approve` | team | UAT 게이트 승인 (스펙 v1.0엔 없던 신규 — 코드에 존재) |
| POST | `/projects/{id}/archive` | team | 보관 — 읽기 전용 전환(SCR-SET-003) |
| POST | `/projects/{id}/restore` | team | 🔲 미구현(MVP 범위 외) — 보관·삭제 복구 |
| DELETE | `/projects/{id}` | team | **소프트 삭제** — 프로젝트명 정확 입력 확인(요청 본문 `{confirmName}`), 30일 복구 가능. 영구 삭제는 30일 후 admin |

> 대시보드 `미처리 항목`·진행률은 **파생 집계**(저장 X) — 슬롯 상태·미해결 결함·응답 대기 코멘트에서 계산. 보관/삭제는 감사 로그 기록(REQ-NFR-005).

---

## 5. 멤버십 · 초대 (`/api/projects/{id}/members`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/members` | 참여자 | 팀 멤버 + 고객사(초대 상태 포함) |
| POST | `/projects/{id}/members/invite` | team | 초대 `{email, name, type:"team_member"\|"client", job?}`. 이메일 판별 — 신규=온보딩 / 기존=참여만 추가+알림 / 이미 참여=무동작 |
| DELETE | `/projects/{id}/members/{accountId}` | team | 제외(소프트 — `left_at`, 계정·이력 보존) |

**예) `POST .../members/invite` (스마트 분기 응답)**
```jsonc
// res 200 (기존 고객사 → 참여만 추가)
{ "success": true, "data": { "result": "joined_existing", "accountId": 30, "notified": true } }
// res 201 (신규 → 온보딩 초대)
{ "success": true, "data": { "result": "invited", "invitationId": 88, "expiresAt": "2026-05-21T07:50:00Z" } }
```

---

## 6. 산출물 슬롯 · 버전 · 파일 (`/api/projects/{id}/slots`)

`{slot}` ∈ `requirements`/`ia`/`design`/`prototype`/`figma`.

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/slots` | 참여자 | 전 슬롯 요약 목록 (스펙 v1.0엔 없던 신규 — 코드에 존재) |
| GET | `/projects/{id}/slots/{slot}` | 참여자 | 슬롯 + 최신 버전 + 파일 묶음 + 코멘트 + 활동 이력. 선행 변경 배지 포함 |
| POST | `/projects/{id}/slots/{slot}/versions` | team | **새 버전 만들기** `{changeSummary}` → v+1 draft(현재 파일 사본). 컨펌 상태면 이전 컨펌 자동 무효화(REQ-WF-005) |
| POST | `/projects/{id}/slots/{slot}/files` | team | 파일 업로드(multipart, draft만). *스펙 v1.0 `.../versions/{vno}/assets/files`에서 **슬롯 평탄화*** |
| POST | `/projects/{id}/slots/{slot}/urls` | team | 외부 링크 추가 `{name, url}`(Figma 등, draft만). *스펙 v1.0 `assets/links` → **urls*** |
| DELETE | `/projects/{id}/slots/{slot}/files/{assetId}` | team | 자산(파일·링크) 삭제(draft만). *평탄화* |
| GET | `/projects/{id}/slots/{slot}/files/{assetId}/download` | 참여자 | 권한 확인 후 `200 {url, expiresAt}`(단명 presigned, §13-1). *스펙 v1.0 `/assets/{id}/download` → 슬롯 스코프* |
| POST | `/projects/{id}/slots/{slot}/review-request` | team | 검토 요청 → 버전 잠금 + 고객 알림(draft만) |
| POST | `/projects/{id}/slots/{slot}/review-recall` | team | 검토 요청 회수 → draft 복귀(검토중만) |
| POST | `/projects/{id}/slots/{slot}/confirm` | client | 컨펌 → 게이트 해제(검토중만, 게이트 순서) |
| POST | `/projects/{id}/slots/{slot}/reject` | client | 반려 `{reason}` (사유 필수, 검토중만) |
| POST | `/projects/{id}/slots/{slot}/ack-upstream` | team | 선행 변경 `검토 완료` — 배지 해소(REQ-WF-005). *스펙 v1.0 `upstream-review` → **ack-upstream*** |
| GET | `/projects/{id}/slots/{slot}/activity` | 참여자 | 슬롯 활동 이력(append-only). *스펙 §8 `activities` → **activity*** |
| GET | `/projects/{id}/slots/{slot}/versions` | 참여자 | 🔲 미구현(MVP 범위 외) — 버전 목록 |
| GET | `/projects/{id}/slots/{slot}/versions/{vno}` | 참여자 | 🔲 미구현(MVP 범위 외) — 특정 버전 스냅샷 조회 |
| PUT | `/projects/{id}/slots/{slot}/files/{assetId}` | team | 🔲 미구현(MVP 범위 외) — 자산 교체(삭제 후 재업로드로 대체) |

> **구현 정합(v1.3)**: 스펙 v1.0은 자산을 `.../versions/{vno}/assets/...`로 버전·자산 스코프했으나, 실제 구현은 **슬롯 평탄화** — 파일=`/{slot}/files`, 링크=`/{slot}/urls`(파일은 multipart, 링크는 `{name,url}`), 삭제·다운로드는 `/{slot}/files/{assetId}`. 워크플로우 액션(review-request/recall/confirm/reject/ack-upstream)도 버전번호 없이 슬롯에 직접. 현재 버전(draft/검토중)을 서버가 판별하므로 path에 `vno` 불필요.

**예) `GET /projects/1/slots/requirements`**
```jsonc
{ "success": true, "data": {
  "slot": { "slotType": "requirements", "status": "draft", "currentVersion": "v3", "upstreamChanged": false },
  "version": {
    "versionNo": 3, "status": "draft", "changeSummary": "§7 NFR 추가",
    "files": [
      { "id": 41, "kind": "file", "name": "main-srs.pdf", "contentType": "application/pdf", "sizeBytes": 1992294, "logicalKey": "main-srs", "badge": "replaced" },
      { "id": 44, "kind": "url", "name": "참고 링크", "externalUrl": "https://..." }
    ],
    "comments": [ { "id": 7, "author": "김PM", "body": "...", "createdAt": "..." } ]
  },
  "activities": [ { "type": "version_created", "actor": "김PM", "body": "v3 스냅샷 생성", "createdAt": "..." } ]
} }
```
**상태 전이 에러 예 (409)**
```jsonc
// 검토중이 아닌 버전에 confirm
{ "success": false, "message": "검토 요청된 버전만 컨펌할 수 있습니다." }
// 게이트 순서 위반
{ "success": false, "message": "이전 게이트(Gate 7) 미통과로 컨펌할 수 없습니다." }
```

---

## 7. 코멘트 (`/api/.../comments`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/slots/{slot}/comments` | 참여자 | 슬롯 코멘트. *스펙 v1.0 `.../versions/{vno}/comments`에서 **버전 세그먼트 제거*** |
| POST | `/projects/{id}/slots/{slot}/comments` | 참여자 | 등록 `{body}` (현재 스냅샷 귀속) |
| PATCH | `/projects/{id}/comments/{commentId}` | 본인 | 수정 `{body}` (인라인 편집) |
| DELETE | `/projects/{id}/comments/{commentId}` | 본인·admin | 삭제(소프트, 관리자 강제 삭제 시 감사 기록) |
| GET·POST | `/projects/{id}/defects/{defectId}/comments` | 참여자 | 🔲 미구현(MVP 범위 외) — 결함 코멘트. 테이블(`comment.defect_id`)은 있으나 엔드포인트 없음 |

> 모든 코멘트 경로는 **프로젝트 스코프**(IDOR 차단). 서버는 comment→(slot_version|defect)→project→project_member 조인으로 접근 검증.

> 반려 사유는 코멘트가 아니라 `activity_event`에 저장(영구). 활동 이력엔 코멘트 미기록(REQ-WF-004).

---

## 8. 활동 이력 · 알림

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/slots/{slot}/activity` | 참여자 | 슬롯 활동 이력(append-only, 전체). *§6과 동일 — `activities`→`activity`* |
| GET | `/notifications` | 인증 | 내 알림 `?unread=true` |
| POST | `/notifications/{id}/read` | 인증 | 읽음 처리. *스펙 v1.0 `PATCH` → 코드는 **POST*** |
| POST | `/notifications/read-all` | 인증 | 전체 읽음 |

---

## 9. 테스트 케이스 (`/api/projects/{id}/test-cases`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/test-cases` | 참여자 | 목록 `?phase=&priority=&status=&q=` |
| POST | `/projects/{id}/test-cases` | team(QA) | 등록 `{title, phase, priority, precondition?, steps?, expectedResult?, assigneeId?}` (status=pending 시작) |
| GET | `/projects/{id}/test-cases/{tcId}` | 참여자 | 상세(전제·절차·기대·실제·담당자·연결 결함) |
| PATCH | `/projects/{id}/test-cases/{tcId}` | team(QA) | 편집 `{title, phase, priority, precondition?, steps?, expectedResult?, assigneeId?}` (상태·실제결과·코드는 유지) |
| PATCH | `/projects/{id}/test-cases/{tcId}/status` | team(QA) | 결과 `{status:"passed"\|"failed"\|"pending", actualResult?}` |
| POST | `/projects/{id}/test-cases/import` | team(QA) | CSV 일괄 등록 `{csv}`(텍스트 — 파일은 클라이언트서 읽어 전송). `{imported}` 반환. RFC4180 파서(따옴표·필드 내 쉼표/개행·BOM·헤더 행 처리) |
| POST | `/projects/{id}/test-cases/assign` | team(QA) | 담당자 일괄 지정 `{testCaseIds:[...], assigneeId}` (assigneeId=null이면 미지정 해제). `{assigned}` 반환 |

> CSV 템플릿(헤더 + 예시 행)은 **클라이언트에서 Blob으로 생성·다운로드** — 별도 API 없음. 일괄 등록은 단건 폼과 달리 **multipart 아닌 텍스트 JSON**(브라우저서 FileReader로 읽음).

---

## 10. 결함 (`/api/projects/{id}/defects`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/defects` | 참여자 | 목록 `?status=&severity=&assigneeId=&q=` |
| POST | `/projects/{id}/defects` | 공통(UAT는 client도) | 등록 `{title, severity, reproSteps?, environment?, testCaseId?, assigneeId?}` (status=open. 담당자 배정은 팀만, testCaseId 지정 시 해당 TC에 자동 연결) |
| GET | `/projects/{id}/defects/{defectId}` | 참여자 | 상세(재현·환경·담당자·첨부·연결 TC·코멘트) |
| PATCH | `/projects/{id}/defects/{defectId}` | team·등록자 | 편집 `{title, severity?, reproSteps?, environment?, assigneeId?}` (고객사는 **본인 등록분만**, 담당자 변경은 팀만 — 고객사 수정 시 기존 담당자 유지) |
| PATCH | `/projects/{id}/defects/{defectId}/status` | team | `{status:"open"\|"in_progress"\|"resolved"\|"cannot_reproduce"}` |
| POST | `/projects/{id}/defects/{defectId}/attachments` | 참여자 | 증거 파일 추가(multipart) |
| GET | `/projects/{id}/defects/{defectId}/attachments/{attId}/download` | 참여자 | 첨부 다운로드(`200 {url, expiresAt}`, §13-1) |
| DELETE | `/projects/{id}/defects/{defectId}/attachments/{attId}` | team·등록자 | 첨부 삭제 |
| POST | `/projects/{id}/defects/{defectId}/test-cases/{tcId}` | team | TC 연결(건별, 다대다) |
| DELETE | `/projects/{id}/defects/{defectId}/test-cases/{tcId}` | team | TC 연결 해제 |

> 담당자 변경은 **별도 엔드포인트 없이** 등록·편집 본문의 `assigneeId`로 처리(팀만). 연결은 spec상 배열(`/links`)이 아니라 **건별 리소스**(`/test-cases/{tcId}` POST·DELETE)로 구현.

> **삭제 정책(MVP)**: TC·결함은 **하드 삭제 미지원** — 상태(`pending`/`open`…)·연결로 관리. 잘못 등록 시 상태 전이로 처리, 영구 삭제는 후속(archive). 슬롯 파일 삭제는 draft 한정(§6).

---

## 11. 검색 · 감사 · 개발

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/search?projectId=&q=` | 참여자 | 통합 검색 (요구사항·산출물·파일·TC·결함·코멘트). *스펙 v1.0 `/projects/{id}/search`(경로 스코프) → **전역 `/api/search`**(projectId 쿼리)* |
| GET | `/admin/audit` | admin | 감사 로그 `?actorId=&action=&from=&to=`. *스펙 v1.0 `audit-logs` → **audit*** |
| POST | `/projects/{id}/dev-runs` | team | 개발 시작 트리거 — 사전조건(5·7·9·11·13 게이트 + 내부 산출물) 미충족 시 409 |
| GET | `/projects/{id}/dev-runs` | 참여자 | 개발 실행 이력 |
| GET | `/projects/{id}/export` | 참여자 | 산출물 Export(**동기 단건** — 즉시 응답, Gate 18 통과 후만, REQ-DEV-002). *스펙 v1.0의 비동기 잡(`POST /exports` + `GET /exports/{exportId}`)이 아니라 동기 GET으로 구현* |

---

## 12. 권한 매트릭스 (요약)

| 동작 | admin | team | client |
|------|:----:|:----:|:----:|
| 프로젝트 생성·설정 | ❌ | ✅ | ❌ |
| 새 버전·검토 요청·파일 편집 | ❌ | ✅ | ❌ |
| 컨펌·반려 | ❌ | ❌ | ✅ |
| 팀원·고객사 초대 | ✅ | ✅(자기 프로젝트) | ❌ |
| 계정 수정·비활성화 | ✅ | ❌ | ❌ |
| TC 등록·결과 | ❌ | ✅(QA) | ❌(UAT 조회) |
| 결함 등록 | ✅조회 | ✅ | ✅(UAT) |
| 감사 로그 | ✅ | ❌ | ❌ |

> 단일 진실 원천 = SRS §3 권한 매트릭스. 본 표는 API 요약. **admin은 전 프로젝트 조회·다운로드 가능(읽기 전용)** — 위 ❌는 *쓰기* 기준(편집·컨펌·새 버전 등은 불가).

---

## 13. 설계 결정 (구 미결 — 2026-05-21 정리)

1. **파일 다운로드 = S3 사전서명(presigned) URL, `200 {url, expiresAt}` 단일 형태** — 302 리다이렉트는 envelope와 충돌하므로 비채택. 권한 확인 후 **단명(5분) presigned URL**을 envelope에 담아 반환. **private bucket · object key 난수화 · `Content-Disposition`(원본 파일명)·`Content-Type` 고정 · no-cache**. 권한은 URL 발급 시점 검증. 백엔드 스트림은 비채택(앱 부하).
2. **버전 경로 = `{vno}`(슬롯 스코프 번호)** 유지 — `/slots/{slot}/versions/3`. 사람이 읽기 쉽고 슬롯 내 유일(`UNIQUE(slot_id, version_no)`). 서버가 `(slot_id, vno)`로 내부 id 조인.
3. **대량 import = MVP 동기 처리** — CSV/Excel은 수십 행 규모라 동기로 `{created, errors[]}` 즉시 반환. 파일이 커지면(수천 행) 잡 큐 비동기(202 Accepted + 진행 폴링)로 전환 — 후속.
4. **Idempotency = 상태 가드 + 행 잠금** — 컨펌·검토 요청/회수·반려·새 버전은 상태 머신이 중복을 막되(잘못된 전이 = 409), 동시 클릭 레이스는 **트랜잭션 내 `SELECT ... FOR UPDATE`(또는 낙관적 version 컬럼)** 으로 직렬화. partial unique(검토중 1개)만으로는 게이트 순서·current_version 동기화를 다 막지 못함. 결제류 위험 동작이 생기면 명시적 idempotency-key 재검토.
5. **런타임 Swagger = springdoc-openapi 자동 생성** — 구현 시 어노테이션 기반 OpenAPI 3.1을 코드에서 생성. **본 마크다운은 설계 계약(SoT)** 으로 유지, 둘을 이중관리하지 않음.

---

## 14. 구현 규칙 (코덱스 리뷰 반영)

- **파일 업로드 원자성**: multipart 저장(S3)과 DB insert는 원자적이지 않음 → **S3 업로드 성공 후 DB insert, DB 실패 시 S3 object 보상 삭제**(또는 주기적 orphan GC). presigned PUT 사용 시 미완료 업로드 정리 정책 명시.
- **CSV import 계약**: 최대 행 수·파일 크기·타임리밋 명시, **부분 성공 허용**, row-level 에러 스키마 `errors:[{row, field, message}]` 반환.
- **페이지**: 계약은 **1-based `page`**, 서버가 Spring `Pageable`(0-based)로 변환하는 커스텀 resolver.
- **enum 매핑**: 하이픈 값(`pending-review`)은 Java enum 매핑 시 `@JsonValue`/`@JsonCreator` 필요(구현 규칙).
- **정렬 화이트리스트**: `sort` 파라미터는 허용 컬럼 화이트리스트로만 변환(문자열 직접 치환 금지 — SQL injection).
