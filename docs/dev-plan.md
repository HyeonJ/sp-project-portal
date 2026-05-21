# 개발 계획 (18단계 — 풀스택 구현)

| 항목 | 내용 |
|------|------|
| 작성일 | 2026-05-21 |
| 기준 | ERD v1.3 · API v1.1 · 클래스/시퀀스 v0.4 · 화면설계서 v1.5 · 아키텍처 v0.2 · CLAUDE.md |
| 스택 | Spring Boot 3.2 · MyBatis(XML) · PostgreSQL 15 · Flyway · Spring Security 세션 + Spring Session JDBC · Thymeleaf SSR + jQuery · S3(dev=로컬) · Testcontainers · Gradle(wrapper) |
| 런타임 | Java 21 설치(아키텍처 17 floor와 호환). Gradle 9.3.1 / Docker 실행 중(Testcontainers) |

> **단일 Spring Boot 앱**(SSR 프런트 + jQuery AJAX용 JSON). 코덱스 리뷰 반영: **walking-skeleton-first** — 넓은 인프라보다 얇은 end-to-end 수직으로 통합 리스크를 먼저 노출. 매 페이즈 `gradlew build`(컴파일+테스트) 통과 후 진행, 절반 컴파일 코드 금지.

## Phase A — Walking Skeleton (아키텍처 증명)
Gradle 스캐폴드+wrapper · 프로필(local/dev/test/prod)+`application-test.yml`+기동 시 prod env 검증 · Flyway · Testcontainers · MyBatis 배선 · **Spring Security 세션 + Spring Session JDBC(Flyway가 스키마 소유, 자동 init off, 인덱스 포함)** · CSRF 전역(레이아웃 meta + jQuery 헤더) · 레이아웃 fragment(디자인 토큰 CSS 이식) · actuator health · SSR 오류페이지(403/404/500)+AJAX JSON 에러 · `ApiResponse`/`@RestControllerAdvice`/커스텀 예외 · `FileStorage`(local 구현) · 시드 유저(BCrypt) → **로그인 → 보호 페이지 → AJAX POST 1건(CSRF) + 매퍼 IT 1건**. ✅ build 통과 = 세션·CSRF·MyBatis·Flyway·Testcontainers·SSR/AJAX 통합 증명.

## Phase B — 제품 코어(승인 경로), 5분할
- **B1** 프로젝트·멤버십·게이트·대시보드(thin). 멤버십 인가 서비스 가드.
- **B2** 슬롯·버전·파일 업로드(S3-local). 업로드 먼저→짧은 tx→커밋→실패 시 best-effort 삭제. logical_key/position.
- **B3** 검토 상태머신(요청/컨펌/반려/회수). slot_version `FOR UPDATE`, 게이트 해제(project_gate 행 락), activity, 알림 after-commit.
- **B4** 새 버전 무효화(과거 버전 confirmed 유지·슬롯 포인터 이동+activity invalidate) + 선행 배지(파생 비교, ackUpstream 재-스탬프).
- **B5** 코멘트(인라인 편집/삭제 confirm)·활동 타임라인·파일 뷰어(PDF·이미지/폴백)·presigned 다운로드 권한. → 골든 경로 브라우저 검증.

## Phase C — Breadth (시간 허용 시)
TC·결함(CSV·연결·첨부) → 알림·검색·감사·관리자·개발트리거(dev_run 사전조건)·Export → 진행현황·비번재설정·초대·polish.

## 횡단·규약 (전 페이즈)
- enum = **ASCII 코드 저장 + UI 한글 라벨 매핑**(저장에 표시문자 금지).
- 인가 = 라우트 가시성 + **서비스 레이어 멤버십/tier 검증**(defense in depth). 인가 테스트 매트릭스(agency/client/admin/member/non-member).
- 시드 = dev 프로필 `CommandLineRunner`(멱등·프로필 게이트), prod 마이그레이션엔 미포함.
- 테스트 = Testcontainers PostgreSQL IT(매퍼/서비스), 최소 1개 `@SpringBootTest`(로그인+보호 AJAX POST), 매퍼 생기면 즉시 매퍼 IT.
- DB 제약으로 워크플로우 불변식 강제(부분 유니크·복합 FK·CHECK). 업로드 크기·타입 설정. 시크릿 env(하드코딩 금지).

## 진행 기록
- (착수: 2026-05-21)
