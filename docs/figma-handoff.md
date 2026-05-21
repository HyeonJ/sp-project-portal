# Figma 핸드오프 (REQ-DSN-004)

| 항목 | 내용 |
|------|------|
| 프로젝트명 | SoftPuzzle PM |
| 버전 | v1.0 (16~17단계 핸드오프) |
| 작성일 | 2026-05-21 |
| 기준 | 프로토타입(`prototype/index.html`, Gate 15 컨펌) · 화면 설계서(`screen-design/` v1.5) |
| 단계 | 16~17 (Figma 정제·핸드오프). 컨펌 게이트 없음 — 등록만(REQ-DSN-004) |

> **핸드오프 형태 = Figma 링크(필수) + export 에셋(선택)**, `.fig` 업로드 안 함(api-spec/ERD 결정). 실제 Figma 파일은 외부 도구에 있고, **본 문서는 그 핸드오프의 명세(디자인 토큰·프레임 매핑·컴포넌트·에셋)** 다. 토큰의 단일 진실 원천(SoT)은 프로토타입 `:root` CSS, 화면 구조는 화면 설계서.

---

## 1. 핸드오프 개요

- **Figma 링크(등록)**: `https://www.figma.com/file/SPpm/handoff` (Dev Mode — 메인·어드민). 산출물 figma 슬롯에 `asset_kind=url`로 등록(프로토타입 figma 슬롯 참조).
- **Dev Mode 소비**: 개발자는 Dev Mode에서 측정값·CSS·토큰·컴포넌트 스펙·에셋 export를 직접 확인. 본 문서는 그 매핑·요약.
- **범위**: 프로토타입 v2(Gate 15 컨펌)의 디자인 시스템과 31개 화면/모달. 새 디자인이 아니라 **확정 프로토타입의 Figma 정제본**.

---

## 2. 디자인 토큰 (SoT: 프로토타입 `:root`)

### 2-1. 컬러
| 토큰 | 값 | 용도 |
|------|----|------|
| `--accent` | `#2f6feb` 코발트 | 주요 액션·링크·활성 |
| `--accent-soft` | accent + transparent 94% | 액센트 배경 칩·hover |
| `--accent-deep` | accent + fg 24% | 액센트 강조 텍스트 |
| `--bg` / `--surface` | `#fafafa` / `#ffffff` | 페이지 / 카드 |
| `--surface-2` | surface + bg 68% | 보조 표면 |
| `--fg` / `--muted` | `#111111` / `#6b6b6b` | 본문 / 보조 텍스트 |
| `--border` / `--divider` | `#e5e5e5` / border + transparent 46% | 보더 / 구분선 |
| `--ok`(success) | `#17a34a` | 통과·컨펌·완료 (pill, lvl dot) |
| `--warn` | `#eab308` | 검토중·진행중·선행 경고 |
| `--hot`(danger) | `#dc2626` | 미해결·반려·삭제·High |
| `--accent-on` | `#ffffff` | 컬러 배경 위 텍스트 |

> 색공간은 OKLAB `color-mix` 파생(soft/deep). 명도 대비 WCAG AA 기준.

### 2-2. 타이포그래피 — **Inter**
| 토큰 | px | 용도 |
|------|----|------|
| text-xs / sm / base | 12 / 14 / 16 | 메타·캡션 / 본문 / 기본 |
| text-lg / xl | 20 / 24 | 소제목 / 제목 |
| text-2xl / 3xl / 4xl | 32 / 48 / 64 | 대형 헤드라인 |
| leading | body 1.5 / tight 1.2 | |
| tracking | display -0.01em | 제목 자간 |
| mono | JetBrains Mono | 코드·ID·치수 |

### 2-3. 스페이싱 · 형태 · 모션
| 분류 | 값 |
|------|----|
| space | 4 · 8 · 12 · 16 · 20 · 24 · 32 · 48 · 80 |
| radius | sm 8 · md 12 · lg 16 · pill 9999 |
| shadow | `--panel-shadow`(은은한 액센트 틴트) · `--soft-inset`(상단 1px inset) |
| motion | fast 150ms · base 200ms · ease `cubic-bezier(0.2,0,0,1)` |

---

## 3. 화면 → Figma 프레임 인벤토리

화면 설계서 SCR-* ↔ Figma 프레임 1:1. 페이지 그룹:

| Figma 페이지 | 프레임(SCR/MOD) |
|-------------|----------------|
| 00 공통 | SCR-LAY(인증전/글로벌/프로젝트 3변형) |
| 01 진입·랜딩 | SCR-AUT-001 로그인 · SCR-GLB-001 프로젝트 목록 · SCR-PRJ-001 대시보드 · SCR-RDM-001 진행 현황 |
| 02 핵심 도메인 | SCR-REQ-001 요구사항 · SCR-DSN-001 산출물 + MOD-VER/WF-001/WF-002/FILE-001 |
| 03 부가 | SCR-SCH-001 검색 · SCR-DEV-001 개발 진행 · SCR-TST-001~004 TC/결함 + MOD-DEV-001 |
| 04 계정·관리 | SCR-AUT-002/003/004 · SCR-ACC-001 · SCR-ADM-001~005 · SCR-SET-001/002/003 · SCR-PRJ-002 + MOD-AUT-001 |
| 05 시스템 | SCR-SYS-401~500 오류 4종 |

> 와이어프레임(`wireframe/`)의 화면 번호·정보/컨트롤 라벨이 프레임 주석의 기준.

---

## 4. 핵심 컴포넌트 (Figma 컴포넌트셋 ↔ 프로토타입 클래스)

| 컴포넌트 | 변형(variant) | 비고 |
|---------|--------------|------|
| Button | default / primary / accent(보더) / danger(보더) / **danger-solid** / ok / warn / locked(비활성) / sm | 모든 액션 |
| Pill(상태) | ok / warn / hot / muted / accent | 버전·결함·TC 상태 |
| Level dot(`.lvl`) | high(빨강) / med(앰버) / low(회색) | 우선순위·심각도 = 점+텍스트 |
| Category chip(`.cat-tag`) | 중립 | TC 단계 등 |
| Card | flat / 기본(보더+soft-shadow) | |
| Modal | head / body / foot · `lg` · **confirm(아이콘+제목 인라인, B안)** | 위험=빨강 휴지통, 일반=코발트 알림 |
| File row | file / **url(🔗·열기)** / replacing(인라인 확인) | 산출물 파일 묶음 |
| Sidebar nav item | active(좌측 액센트 바) / locked / hover translateX | |
| KPI/Stat card | default / accent | 대시보드·진행 현황 |
| Comment | item / 인라인 편집(textarea) | |

---

## 5. Export 에셋

- **아이콘**: 사이드바·액션·상태 아이콘은 **인라인 SVG(stroke 2, Lucide 계열)** — 프로토타입에 정의됨. Figma는 동일 SVG 컴포넌트로 export.
- **이미지/래스터**: 로고, 시안 미리보기 등은 PNG/SVG export(`icons-export.zip` 형태).
- **번들**: figma 슬롯 export 에셋(zip)으로 등록 — `asset_kind=file`.

---

## 6. 핸드오프 자산 등록 (REQ-DSN-004)

산출물 **figma 슬롯** = url 1개(필수) + export 에셋(선택)의 묶음:
- `url`: Figma Dev Mode 링크(위 §1). 가능하면 **버전 링크**로 고정.
- `file`: `icons-export.zip` 등 export 에셋.

> 컨펌 게이트 없음(개발 사전조건 등록만). 라이브 Figma URL 가변성은 버전 링크로 완화(강제 불가). 프로토타입 figma 슬롯이 이 묶음을 시연.

---

## 7. 노트 · 한계

- **실제 Figma 파일은 외부 도구**에 있으며 본 문서는 그 핸드오프 명세/매핑. 토큰 정확값은 프로토타입 `:root`, 화면 구조는 화면 설계서가 SoT.
- 프로토타입 v2가 디자인 시스템을 이미 구현하므로, Figma 정제는 **새 디자인이 아닌 동기화**. 토큰/컴포넌트 불일치 시 프로토타입 기준.
- 개발(18) 입력으로 사용 — Dev Mode 측정·CSS·토큰을 프론트 구현 기준으로.
