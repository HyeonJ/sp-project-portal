# SoftPuzzle PM

> 제품(사이트)명: **SoftPuzzle PM** — SoftPuzzle의 B2B 프로젝트 관리 SaaS. 레포 슬러그·폴더는 `sp-project-portal` 유지.

고객사와 대행사 간 승인/컨펌 워크플로우 중심의 프로젝트 진행 관리 웹 시스템.

## 브랜치 구조

| 브랜치 | 용도 | 비고 |
|--------|------|------|
| **`mvp`** | **현재 작업 브랜치** — 요구사항을 파일(PDF) 업로드 방식으로 단순화한 MVP | SRS v2.0 |
| `main` | v1.4 시점 안정판 (참고용 폴백) | SRS v1.4 |
| `archive/v1.4-individual-req-entry` | 요구사항 항목별 등록 방식 (구 MVP) 보존 | 동결 |

> 새 세션 시작 시 `git status`로 현재 브랜치 확인 후 `PROGRESS.md`로 현재 단계 파악.

## 주요 문서

- `docs/flow-diagram.md` — 20단계 프로젝트 플로우
- `docs/requirements.md` — 요구사항 정의서 (SRS)
- `docs/deliverables.md` — 산출물 목록
- `PROGRESS.md` — 현재 진행 단계 추적

## 사용자 구조

| 역할 | 설명 |
|------|------|
| 관리자 | 플랫폼 전체 관리. 프로젝트팀·고객사 계정 생성·수정·비활성화, 전체 프로젝트 조회 |
| 프로젝트팀 | PM, 기획자, 디자이너, 개발자, QA. 프로젝트 생성·진행 주체 |
| 고객사 | 요구사항 확인·컨펌·UAT 수행 |

MVP: 3-tier 접근 구분, RBAC 제외.

## 작업 규칙

- 현재 단계는 항상 `PROGRESS.md`에서 확인
- 각 게이트(9단계, 22단계) 통과 전 해당 산출물 완료 필수
- 반복 루프(7↔8, 20↔21)는 PROGRESS.md에 회차 기록

## 산출물 파일 관리 규칙

- **산출물 파일을 신규 생성하면 즉시 `docs/deliverables.md`의 해당 No 행 `파일` 컬럼에 경로 기재** (레포 루트 기준 상대 경로, 예: `docs/ia.md`, `docs/design/candidates.html`)
- 파일명·경로를 바꾸면 `docs/deliverables.md`의 경로도 동시에 갱신
- 산출물 자체가 파일이 아닌 경우(예: Git 저장소, `PROGRESS.md` 갈음)는 해당 표기 사용
- 아직 작성 전이면 `—`

## 커뮤니케이션 규칙

- 방어적으로 답변하지 말 것 — 충분하지 않으면 충분하지 않다고 말할 것
- 객관적 판단을 우선하고, 동의를 구하는 식의 표현 자제
