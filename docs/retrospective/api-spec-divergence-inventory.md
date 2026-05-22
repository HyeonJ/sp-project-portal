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

## 근본 원인 (왜 다르게 구현됐나 — dev-plan.md 근거)

프론트와 결은 비슷하나 메커니즘이 다르다. 4가지가 겹쳤다:

| # | 원인 | 근거 | 어떤 불일치를 설명 |
|---|------|------|--------------------|
| R1 | **계획이 "엔드포인트 계약"이 아니라 "기능·데이터모델"로 분해됨.** api-spec은 `dev-plan.md` "기준" 6개 문서 중 하나로 평평하게 나열됐을 뿐, "스펙 경로 그대로 구현"이라는 지시가 없었다. Phase B/C는 "슬롯·버전·파일 업로드", "검토 상태머신"처럼 도메인 능력·트랜잭션 관심사로 기술됨 → 구현자가 코드/모델에서 자연스러운 엔드포인트를 **재설계** | dev-plan L6·L15~23 | B(슬롯 평탄화)·C(네이밍·메서드·스코프) |
| R2 | **Breadth를 계획이 "시간 허용 시"로 명시.** Phase C(TC·결함·알림·검색·감사·관리자·Export·비번재설정·초대·polish)가 optional-by-plan → 2차 엔드포인트가 시간 압박에 조용히 잘림. 스펙은 잘린 사실을 반영 안 함 | dev-plan L22 | A(미구현 9건) |
| R3 | **스펙이 의존한 drift 방지 장치(springdoc=런타임 SoT)가 계획에 없음.** api-spec은 "런타임 SoT는 springdoc 코드 생성"이라 적었지만 dev-plan 스택·횡단 규약에 springdoc 부재 → 코드↔스펙 피드백 루프 0 | api-spec L13·15 vs dev-plan L7·L25~30 | 드리프트가 안 잡힌 이유 |
| R4 | **정합 게이트 부재.** Phase 완료 기준 = `gradlew build` 그린 + 골든 경로 브라우저. "엔드포인트 ↔ api-spec diff"가 어느 DoD에도 없음 | dev-plan L10·L20 | 사후 발견(프론트와 동일) |

> **중요한 뉘앙스**: 불일치의 상당수(B·C)는 **나쁜 게 아니라 더 나은 설계**다. 예: 스펙은 `/versions/{vno}/assets/...`였지만 런타임엔 서버가 현재 draft 버전을 알므로 path의 `vno`는 중복 → 평탄화가 옳다. 즉 **스펙이 데이터모델 함의를 알기 전에 너무 일찍 쓰였다**는 신호. 반면 A(미구현)는 명백한 스코프 누락이다. 두 종류를 구분해야 개선책이 달라진다.

## 개선안

**핵심: SoT를 하나로 정하고 장치로 강제한다.** 둘 중 택일:

- **(A) 코드-우선** — 손으로 쓴 스펙의 권위를 내려놓고 **springdoc 연결**해 스펙을 코드에서 생성. 수기 문서는 "설계 스케치(비권위)"로 명시. MVP/속도 우선이면 이쪽.
- **(B) 스펙-우선(계약)** — api-spec을 구속력 있는 계약으로 두고 **contract test**(REST-assured 등)로 경로·메서드·스키마를 강제. 스펙과 다르면 빌드 실패. 안정 API·외부 소비자 있으면 이쪽.

보조 규율(둘 다 공통):
1. **스코프 컷은 같은 커밋에서 스펙에 반영** — 계획이 자른 엔드포인트는 즉시 `🔲 미구현`으로 표기(스펙이 거짓말하지 않게).
2. **의도된 분기는 그 자리에서 "왜" 한 줄** — 예: "vno path 제거 — 서버가 현재 버전 판별". silent drift → 추적 가능한 결정으로.
3. **Phase DoD에 "엔드포인트 ↔ 스펙 diff" 추가** — 불일치는 (스펙 갱신) 또는 (코드 수정)으로 같은 PR에서 해소.
4. **api-spec을 "기준 6개 중 하나"가 아니라 권위 수준을 명시** — 계약인지 스케치인지 dev-plan이 한 줄로 규정.

> 개선 프롬프트: [`prompt-spec-driven-backend-dev.md`](prompt-spec-driven-backend-dev.md)
