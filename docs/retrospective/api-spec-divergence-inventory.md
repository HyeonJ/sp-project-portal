# api-spec ↔ 구현 불일치 인벤토리

| 항목 | 내용 |
|------|------|
| 작성일 | 2026-05-22 |
| 대상 | `docs/api-spec.md` v1.0~v1.1(동결 baseline) ↔ 실제 컨트롤러 구현 |
| 방법 | 전 컨트롤러 매핑 추출 ↔ 스펙 §2~§11 엔드포인트 1:1 대조 |
| 후속 | 본 인벤토리 기준으로 api-spec **v1.3**에 일괄 정합 + 미구현 `🔲` 표기 |

> 참고: `/api` 접두사 누락은 불일치 아님 — 스펙 §1-1이 "JSON base = `/api`"로 명시. TC §9·결함 §10은 v1.2에서 선반영.

## A. 미구현 — 스펙엔 있으나 코드에 없음 (9건)

| 스펙 엔드포인트 | 기능 | 상태 |
|---|---|---|
| `PATCH /me/password` | 로그인 사용자 비밀번호 변경 | 코드 0건 |
| `POST /admin/accounts/{id}/resend-invite` | 초대 재발송 | 미구현 |
| `PUT /admin/clients/{id}/projects` | 고객사 참여 프로젝트 일괄 편집 | 미구현 |
| `PATCH /admin/accounts/{id}` 이름·직무 편집 | 일반 계정 수정 | `/status`만 구현 |
| `POST /projects/{id}/restore` | 보관·삭제 복구 | 미구현 |
| `GET /slots/{slot}/versions` | 버전 목록 | 미구현 |
| `GET /slots/{slot}/versions/{vno}` | 특정 버전 스냅샷 조회 | 미구현 |
| `PUT .../assets/{assetId}` | 자산(파일) 교체 | 미구현(삭제+재업로드로 대체) |
| `GET·POST /defects/{id}/comments` | 결함 코멘트 | 테이블만 있고 엔드포인트 없음 |

## B. 산출물 슬롯 §6 — API 설계 전면 재편 (최대 드리프트)

스펙은 **버전·자산 스코프**(`.../versions/{vno}/assets/*`), 코드는 **슬롯 평탄화**:

| 스펙 v1.0 | 구현 |
|---|---|
| `POST .../versions/{vno}/assets/files` | `POST /{slot}/files` |
| `POST .../versions/{vno}/assets/links` | `POST /{slot}/urls` (links→**urls**) |
| `DELETE .../versions/{vno}/assets/{id}` | `DELETE /{slot}/files/{id}` |
| `GET /projects/{id}/assets/{id}/download` | `GET /{slot}/files/{id}/download` |
| `POST .../versions/{vno}/{review-request·recall·confirm·reject}` | `POST /{slot}/{...}` (버전번호 제거) |
| `POST .../upstream-review` | `POST /{slot}/ack-upstream` |

## C. 네이밍·메서드·스코프 불일치

| 스펙 | 구현 | 유형 |
|---|---|---|
| `GET .../activities` | `GET /{slot}/activity` | 복수→단수 |
| `PATCH /notifications/{id}/read` | `POST /notifications/{id}/read` | **메서드** |
| `GET /admin/audit-logs` | `GET /admin/audit` | 네이밍 |
| `GET /projects/{id}/search` | `GET /api/search` | 프로젝트스코프→**전역** |
| `POST /admin/accounts/invite` | `POST /admin/accounts` | 경로 |
| `POST /auth/password/reset-request` | `POST /password/reset-request` | `/auth` 제거 |
| `POST /auth/invitations/{token}/accept` | `POST /invite/accept` (토큰 body) | 경로·형태 |
| `POST /auth/login`·`/logout` (JSON) | Spring Security 폼 로그인 | JSON 아님 |
| `GET /projects/{id}/roadmap` (JSON) | SSR 페이지만 | API 미존재 |
| Export `POST /exports`+`GET /exports/{id}` (비동기 잡) | `GET /export` (동기 단건) | 설계 |

## D. 스펙에 없는데 코드에만 있음 (2건)

- `POST /projects/{id}/uat-approve` — UAT 게이트 승인
- `GET /projects/{id}/slots` — 전 슬롯 요약 목록

## 수치 요약

- 감사한 기능 섹션 **9개 전부** 불일치
- 미구현 **9건** · 슬롯 경로 전면 평탄화 · 네이밍/메서드/스코프 **10건+** · 신규 **2건**
- 발견 경로: **사후 수동 대조**(자동/게이트 감지 0건) — `frontend-fidelity-drift.md`와 동일한 근본 원인(강제 장치 없는 동결 baseline)
