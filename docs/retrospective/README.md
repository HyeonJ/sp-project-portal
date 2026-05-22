# 회고 — MVP 산출물↔구현 정합

> 한 줄: **baseline(프로토타입·api-spec)을 "선언"만 하고 강제 장치를 두지 않아, 프론트·백엔드 모두 baseline에서 광범위하게 드리프트했다.** 발견은 전부 사후 수동.

## 프론트엔드

- **무엇이**: 프로토타입(3,023줄·39화면)을 baseline으로 지시했으나, 빌드 계획이 프로토타입 역할을 "디자인 토큰 이식"으로 좁히고 화면은 화면설계서 기준으로 재구현 → 마크업 포팅 ≈ 0%, 화면마다 퍼블 드리프트.
- **증상**: 삭제 confirm·CSV 모달·등록/수정 모달·드롭다운 등 동일 유형 드리프트 반복, 사용자가 사람 diff로 잡아냄.
- **원인**: 지시→계획 번역에서 baseline 의미 축소 / 화면 기준 이중화(프로토타입 vs 화면설계서) / **충실도 게이트 부재**.
- 상세: [`frontend-fidelity-drift.md`](frontend-fidelity-drift.md) · 개선 프롬프트: [`prompt-prototype-frontend-dev.md`](prompt-prototype-frontend-dev.md)

## 백엔드

- **무엇이**: api-spec "v1.0 동결 baseline"이 실제 구현과 §2~§11 전 섹션에서 불일치.
- **증상**: 미구현 9건, 슬롯 API 경로 전면 평탄화, 네이밍/메서드/스코프 10건+, 스펙에 없는 신규 2건.
- **원인(dev-plan 근거)**: ① 계획이 엔드포인트 계약이 아니라 기능·데이터모델로 분해(스펙은 "기준 6개 중 하나") ② Phase C breadth를 "시간 허용 시"로 명시 → 2차 엔드포인트 컷이 스펙 미반영 ③ 스펙이 의존한 drift 방지 장치(springdoc) 미연결 ④ 정합 게이트 부재. *단, B·C 불일치 상당수는 더 나은 설계(스펙이 너무 일찍 쓰임), A는 스코프 누락 — 구분 필요.*
- 상세: [`api-spec-divergence-inventory.md`](api-spec-divergence-inventory.md)(원인·개선안 포함) · 개선 프롬프트: [`prompt-spec-driven-backend-dev.md`](prompt-spec-driven-backend-dev.md) · 정합 결과: api-spec **v1.3**

## 공통 교훈

1. **선언된 baseline은 게이트로 강제하지 않으면 썩는다.** 퍼블=화면별 눈 비교, API=codegen/contract test.
2. 지시→계획 번역 시 핵심 명사(baseline)의 의미 보존을 확인.
3. 정합 검증을 별도 사후 작업이 아니라 각 단계 DoD에 포함.
