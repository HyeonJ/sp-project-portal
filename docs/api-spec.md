# API 명세서

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SoftPuzzle PM |
| 버전 | v0.1 (초안 — 전 도메인 엔드포인트 1차 도출) |
| 작성일 | 2026-05-21 |
| 기준 | ERD(`erd.md` v1.1) · 화면 설계서(`screen-design/`) · SRS(`requirements.md`) |
| 스택 | Spring Boot · MyBatis · PostgreSQL · JWT |
| 관련 단계 | 16~17 (화면 설계서·ERD 안정 후, 17단계 종료 전 v1.0) |

> 산출물 No.11. **설계 시점 명세는 이 문서(사람이 읽는 계약)**, **런타임 Swagger UI는 구현 시 springdoc-openapi가 코드에서 자동 생성**한다 (이중관리 회피). 화면 설계서 컨트롤·ERD 변경 시 동기화.

---

## 1. 공통 규약

### 1-1. 기본
- **Base URL**: `/api`
- **포맷**: 요청/응답 `application/json` (파일 업로드만 `multipart/form-data`)
- **시각**: ISO-8601 UTC (`2026-05-21T07:20:00Z`), 표시는 클라이언트가 KST 변환

### 1-2. 응답 봉투 (글로벌 규칙)
```jsonc
// 성공
{ "success": true, "data": { /* ... */ } }
// 에러
{ "success": false, "message": "사람이 읽을 에러 메시지" }
```
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
- **Access token**: JWT, `Authorization: Bearer <token>`. 무상태(미저장).
- **Refresh token**: HttpOnly Secure 쿠키. `POST /auth/refresh`로 갱신(회전 — 이전 토큰 폐기, 재사용 탐지 시 계정 전체 폐기). 저장은 `refresh_token` 테이블.
- 만료/누락 = 401.

### 1-5. 권한 (3-tier)
`admin`(관리자) / `team`(프로젝트팀) / `client`(고객사). 프로젝트 스코프 리소스는 **참여(`project_member`)** 기준 접근. 위반 시 403. (MVP는 tier 단위, 팀 내 직무 RBAC 제외 — SRS)

### 1-6. 페이지네이션·정렬
목록 공통 쿼리: `?page=1&size=20&sort=created_at,desc`. 검색·필터는 도메인별 쿼리 파라미터.

### 1-7. 코드값
요청/응답의 enum은 **ASCII 머신 코드**(ERD §5) — 예: `status=passed`, `tier=client`. UI 한글 라벨 매핑은 클라이언트 책임.

---

## 2. 인증 · 온보딩 (`/api/auth`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| POST | `/auth/login` | 공개 | 로그인. `{email, password}` → access token + refresh 쿠키. 5회 실패 시 잠금(REQ-AUT) |
| POST | `/auth/refresh` | 쿠키 | access token 갱신(refresh 회전) |
| POST | `/auth/logout` | 인증 | 세션 종료(refresh 폐기) |
| POST | `/auth/password/reset-request` | 공개 | `{email}` → 재설정 메일 발송(존재 여부 노출 안 함) |
| POST | `/auth/password/reset` | 공개 | `{token, newPassword}` → 비밀번호 설정, 토큰 무효화 |
| GET | `/auth/invitations/{token}` | 공개 | 초대 정보 조회(조직·역할·참여 프로젝트) |
| POST | `/auth/invitations/{token}/accept` | 공개 | 온보딩 수락 `{name?, password}` → 계정 활성 + 자동 로그인. 토큰 1회용 |

**예) `POST /auth/login`**
```jsonc
// req
{ "email": "pm@agency.com", "password": "••••••••" }
// res 200
{ "success": true, "data": {
  "accessToken": "eyJ...",
  "account": { "id": 12, "name": "김PM", "tier": "team", "job": "pm" }
} }
// res 400 (5회 실패 잠금)
{ "success": false, "message": "로그인 5회 실패로 계정이 잠겼습니다. 잠시 후 다시 시도하세요." }
```

---

## 3. 계정 (`/api/me`, `/api/admin/accounts`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/me` | 인증 | 현재 계정(이름·이메일·tier·job·참여 프로젝트) |
| PATCH | `/me/password` | 인증 | `{currentPassword, newPassword}` 비밀번호 변경(정책 검증) |
| GET | `/admin/accounts` | admin | 계정 목록 `?tier=&status=&q=` (팀/고객사) |
| POST | `/admin/accounts/invite` | admin | 계정 초대 `{email, name, tier, job?, projectId}` (신규=온보딩 / 기존=참여 추가, 스마트 분기) |
| PATCH | `/admin/accounts/{id}` | admin | 수정·비활성화 `{name?, job?, status?}` |
| POST | `/admin/accounts/{id}/resend-invite` | admin | 초대 재발송(이전 토큰 무효화) |
| PUT | `/admin/clients/{id}/projects` | admin | 고객사 참여 프로젝트 일괄 편집 `{projectIds:[...]}` (추가분 참여 알림) |

---

## 4. 프로젝트 (`/api/projects`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects` | 인증 | 목록(역할별: admin 전체 / team 담당 / client 참여). `?status=&q=&sort=` |
| POST | `/projects` | team | 생성 `{name, clientOrgName, type, startDate?, endDate?, description?}` |
| GET | `/projects/{id}` | 참여자 | 기본 정보 |
| PATCH | `/projects/{id}` | team | 설정 편집 `{name?, type?, startDate?, endDate?, description?}` (생성일·ID 불변, 감사 기록) |
| GET | `/projects/{id}/dashboard` | 참여자 | 대시보드 집계(현재 단계·게이트·진행률·미처리 항목·최근 활동·산출물 요약) |
| GET | `/projects/{id}/roadmap` | 참여자 | 진행 현황(24단계·5게이트·7마일스톤 상태) |
| GET | `/projects/{id}/gates` | 참여자 | 게이트 상태(9·11·13·15·22) |

> 대시보드 `미처리 항목`·진행률은 **파생 집계**(저장 X) — 슬롯 상태·미해결 결함·응답 대기 코멘트에서 계산.

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
| GET | `/projects/{id}/slots/{slot}` | 참여자 | 슬롯 + 최신 버전 + 파일 묶음 + 코멘트 + 활동 이력. 선행 변경 배지 여부 포함 |
| GET | `/projects/{id}/slots/{slot}/versions` | 참여자 | 버전 목록 |
| GET | `/projects/{id}/slots/{slot}/versions/{vno}` | 참여자 | 특정 버전 스냅샷(파일·코멘트, 읽기 전용) |
| POST | `/projects/{id}/slots/{slot}/versions` | team | **새 버전 만들기** `{changeSummary}` → v+1 draft(현재 파일 사본). **컨펌 상태면 이전 컨펌 자동 무효화**(REQ-WF-005) |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/files` | team | 파일 업로드(multipart, draft만). 50MB/파일 |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/links` | team | 외부 링크 추가 `{name, url}` (Figma 등, draft만) |
| PUT | `/projects/{id}/slots/{slot}/versions/{vno}/files/{fileId}` | team | 파일 교체(multipart, draft만) |
| DELETE | `/projects/{id}/slots/{slot}/versions/{vno}/files/{fileId}` | team | 파일·링크 삭제(draft만) |
| GET | `/files/{fileId}/download` | 참여자 | 파일 바이너리(서명 URL 또는 스트림) |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/review-request` | team | 검토 요청 발송 → 버전 잠금 + 고객 알림(draft만) |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/confirm` | client | 컨펌 → 게이트 해제(검토중만, 게이트 순서) |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/reject` | client | 반려 `{reason}` (사유 필수, 검토중만) |
| POST | `/projects/{id}/slots/{slot}/upstream-review` | team | 선행 변경 `검토 완료(영향 없음)` — 배지 해소(REQ-WF-005) |

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
{ "success": false, "message": "이전 게이트(Gate 11) 미통과로 컨펌할 수 없습니다." }
```

---

## 7. 코멘트 (`/api/.../comments`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/slots/{slot}/versions/{vno}/comments` | 참여자 | 스냅샷별 코멘트 |
| POST | `/projects/{id}/slots/{slot}/versions/{vno}/comments` | 참여자 | 등록 `{body}` (현재 스냅샷 귀속) |
| GET | `/defects/{defectId}/comments` | 참여자 | 결함 코멘트 |
| POST | `/defects/{defectId}/comments` | 참여자 | 결함 코멘트 등록 `{body}` |
| PATCH | `/comments/{id}` | 본인 | 수정 `{body}` (인라인 편집) |
| DELETE | `/comments/{id}` | 본인·admin | 삭제(소프트, 관리자 강제 삭제 시 감사 기록) |

> 반려 사유는 코멘트가 아니라 `activity_event`에 저장(영구). 활동 이력엔 코멘트 미기록(REQ-WF-004).

---

## 8. 활동 이력 · 알림

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/slots/{slot}/activities` | 참여자 | 슬롯 활동 이력(append-only, 전체) |
| GET | `/notifications` | 인증 | 내 알림 `?unread=true` |
| PATCH | `/notifications/{id}/read` | 인증 | 읽음 처리 |
| POST | `/notifications/read-all` | 인증 | 전체 읽음 |

---

## 9. 테스트 케이스 (`/api/projects/{id}/test-cases`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/test-cases` | 참여자 | 목록 `?phase=&priority=&status=&q=` |
| POST | `/projects/{id}/test-cases` | team(QA) | 등록 `{title, phase, priority, assigneeId?}` (status=pending 시작) |
| GET | `/projects/{id}/test-cases/{tcId}` | 참여자 | 상세(전제·절차·기대·실제·연결 결함) |
| PATCH | `/projects/{id}/test-cases/{tcId}` | team(QA) | 편집 `{title?, precondition?, steps?, expectedResult?, ...}` |
| PATCH | `/projects/{id}/test-cases/{tcId}/result` | team(QA) | 결과 `{status:"passed"\|"failed"\|"pending", actualResult?}` |
| GET | `/projects/{id}/test-cases/template` | team | CSV 템플릿 다운로드 |
| POST | `/projects/{id}/test-cases/import` | team(QA) | CSV/Excel 일괄 등록(multipart). `{created, errors[]}` 반환 |

---

## 10. 결함 (`/api/projects/{id}/defects`)

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/defects` | 참여자 | 목록 `?status=&severity=&assigneeId=&q=` |
| POST | `/projects/{id}/defects` | 공통(UAT는 client도) | 등록 `{title, severity, assigneeId?, repro?, environment?, linkedTestCaseId?}` (status=open) |
| GET | `/projects/{id}/defects/{defectId}` | 참여자 | 상세(재현·환경·첨부·연결 TC·코멘트) |
| PATCH | `/projects/{id}/defects/{defectId}/status` | team | `{status:"open"\|"in_progress"\|"resolved"\|"cannot_reproduce"}` |
| PATCH | `/projects/{id}/defects/{defectId}/assignee` | team | `{assigneeId}` |
| POST | `/projects/{id}/defects/{defectId}/attachments` | 참여자 | 증거 파일(multipart) |
| POST | `/projects/{id}/defects/{defectId}/links` | team | TC 연결 `{testCaseIds:[...]}` (다대다) |
| DELETE | `/projects/{id}/defects/{defectId}/links/{tcId}` | team | TC 연결 해제 |

---

## 11. 검색 · 감사 · 개발

| Method | Path | 권한 | 설명 |
|--------|------|------|------|
| GET | `/projects/{id}/search` | 참여자 | 통합 검색 `?q=` (요구사항·산출물·파일·TC·결함·코멘트) |
| GET | `/admin/audit-logs` | admin | 감사 로그 `?actorId=&action=&from=&to=` |
| POST | `/projects/{id}/dev-runs` | team | 개발 시작 트리거 — 사전조건(9·11·13·15 게이트 + 내부 산출물) 미충족 시 409 |
| GET | `/projects/{id}/dev-runs` | 참여자 | 개발 실행 이력 |
| POST | `/projects/{id}/export` | 참여자 | 산출물 Export — Gate 22 통과 후만(REQ-DEV-002) |

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

> 단일 진실 원천 = SRS §3 권한 매트릭스. 본 표는 API 요약.

---

## 13. 설계 노트 · 미결

1. **파일 다운로드** — S3 사전서명 URL 리다이렉트 vs 백엔드 스트림. 보안·만료 정책 확정 필요.
2. **버전 경로** — `{vno}`(버전 번호) vs 버전 id. 사람이 읽기 쉬운 번호 채택했으나 내부는 id 조인.
3. **대량 import 비동기** — CSV 대용량 시 동기 vs 잡 큐(202 Accepted) 검토.
4. **Idempotency** — 컨펌·검토 요청 등 중복 클릭 방지 키 도입 여부.
5. **springdoc 채택** — 구현 시 어노테이션 기반 OpenAPI 3.1 자동 생성, 본 문서는 설계 계약으로 유지.
