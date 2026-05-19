# 시스템 구성도 (S/W·인프라)

| 항목 | 내용 |
|------|------|
| 프로젝트명 | sp-project-portal |
| 작성일 | 2026-05-19 |
| 버전 | v0.1 |
| 관련 단계 | 10~17 (설계 전반, 시안 진입 전 작성 권장) |
| 관련 산출물 | `deliverables.md` No.6 |
| 입력 문서 | `requirements.md` v1.1, `architecture.md` v0.1 |
| 후속 문서 | `deliverables.md` No.20 배포 가이드는 본 문서를 기반으로 운영 시점에 확정 |

> 본 문서는 **물리적 구성·S/W 스택 버전·외부 의존성**을 정의한다. 논리 아키텍처·보안 설계는 `architecture.md` 참조.  
> AWS 기준으로 작성. 다른 클라우드/온프레미스 채택 시 동일한 역할 컴포넌트로 매핑.

---

## 1. S/W 스택 (버전 명세)

### 1-1. 런타임·프레임워크

| 구분 | 항목 | 버전 | 비고 |
|------|------|------|------|
| 언어 | Java | 17 LTS | Spring Boot 3.x 최소 요구 |
| 런타임 | JRE | Eclipse Temurin 17 | 운영 표준 |
| 프레임워크 | Spring Boot | 3.2.x | REQ-DEV-001 출력 스택 |
| 보안 | Spring Security | 6.2.x | 세션 인증 |
| 영속성 | MyBatis Spring Boot Starter | 3.0.x | XML Mapper |
| 마이그레이션 | Flyway | 10.x | `resources/db/migration/V{n}__{desc}.sql` |
| 빌드 | Gradle | 8.x (Wrapper 포함) | clone 후 즉시 빌드 가능 |
| 테스트 | JUnit 5, Testcontainers | 최신 | Testcontainers + PostgreSQL |
| 뷰 | Thymeleaf | 3.1.x | SSR |
| 클라이언트 | jQuery | 3.7.x | 글로벌 컨벤션 |
| 로깅 | Slf4j + Logback | Spring Boot 기본 | 글로벌 컨벤션 |

### 1-2. 데이터 저장소

| 구분 | 항목 | 버전 | 용도 |
|------|------|------|------|
| RDBMS | PostgreSQL | 15.x | 모든 도메인 데이터 |
| 세션 저장소 | PostgreSQL (Spring Session JDBC) | — | 세션 영속화 (MVP) — Redis 대안은 운영 결정 |
| 파일 스토리지 | S3 (운영) / 로컬 디렉토리(dev) | — | 산출물 파일 저장 |
| 큐 | DB 기반 큐 (MVP) | — | AI 개발·이메일 비동기 처리. SQS는 추후 검토 |

### 1-3. 외부 의존성

| 의존성 | 용도 | 채택 후보 | 비고 |
|--------|------|----------|------|
| 이메일 발송 | REQ-NTF-001 | AWS SES / SendGrid | 운영 시 1차 채택 결정. 개발 환경은 MailHog |
| AI 개발 백엔드 | REQ-DEV-001 | 외부 SaaS 또는 내부 모델 | 외부 인터페이스만 정의(REQ-DEV-001 §12-1), 실제 백엔드는 별도 |
| 파일 저장소 | REQ-FILE-001 | AWS S3 | 최대 50MB/파일 |
| 도메인·DNS | 운영 | AWS Route 53 | — |
| 인증서 | HTTPS | AWS ACM | 무료 발급·자동 갱신 |

---

## 2. 환경별 구성

### 2-1. 환경 요약

| 환경 | 인프라 위치 | DB | 파일 스토리지 | 이메일 | 데이터 |
|------|-----------|----|--------------|--------|--------|
| **local** | 개발자 머신 | Testcontainers (PostgreSQL) | 로컬 디렉토리 | MailHog | 더미 |
| **dev** | AWS (단일 인스턴스) | RDS PostgreSQL (단일 AZ) | S3 (dev 버킷) | SES (sandbox) | 더미 |
| **staging** | AWS (단일 인스턴스) | RDS PostgreSQL (단일 AZ) | S3 (staging 버킷) | SES | 운영 마스킹 사본 |
| **prod** | AWS (다중 인스턴스 권장) | RDS PostgreSQL (Multi-AZ) | S3 (prod 버킷, 버전 관리 활성) | SES (운영 인증) | 실데이터 |

### 2-2. 운영(prod) 환경 구성도

```
                    ┌─────────────────────────────┐
                    │      AWS Route 53           │
                    │  sp-project-portal.com      │
                    └────────────┬────────────────┘
                                 │ HTTPS (ACM 인증서)
                                 ▼
                    ┌─────────────────────────────┐
                    │       ALB (Public)          │
                    │   · L7 라우팅                │
                    │   · TLS 종단                 │
                    └────────────┬────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                ▼                ▼                ▼
        ┌───────────────┐┌───────────────┐┌──────────────┐
        │  EC2 (AZ-a)   ││  EC2 (AZ-c)   ││  Worker EC2  │
        │ Spring Boot   ││ Spring Boot   ││ AI 개발 워커  │
        │ (Private SN)  ││ (Private SN)  ││ 이메일 워커   │
        └──────┬────────┘└──────┬────────┘└──────┬───────┘
               │                │                │
               └────────┬───────┴───────┬────────┘
                        ▼               ▼
              ┌──────────────────┐ ┌────────────────────┐
              │  RDS PostgreSQL  │ │  S3 (산출물 파일)   │
              │  Multi-AZ        │ │  · 버전 관리       │
              │  Private Subnet  │ │  · SSE-S3 암호화   │
              └────────┬─────────┘ └────────────────────┘
                       │
                       ▼
              ┌──────────────────┐
              │  자동 백업       │
              │  · 일일 스냅샷    │
              │  · 7일 보존      │
              └──────────────────┘

  ┌────────────────────┐    ┌──────────────────────┐
  │  AWS SES (이메일)   │    │  CloudWatch          │
  │  · 알림 발송        │    │  · 로그·메트릭       │
  └────────────────────┘    │  · 알람              │
                             └──────────────────────┘
```

### 2-3. 네트워크 구성 (운영)

| 영역 | 구성 |
|------|------|
| VPC | 단일 VPC (10.0.0.0/16 등 사이트별 결정) |
| Public Subnet | ALB만 배치, 2개 AZ |
| Private Subnet (App) | EC2 (Spring Boot) 배치, 2개 AZ |
| Private Subnet (DB) | RDS 배치, 2개 AZ |
| Security Group: ALB | Inbound 443/80 (Internet) |
| Security Group: App | Inbound 8080 (ALB SG만 허용) |
| Security Group: DB | Inbound 5432 (App SG만 허용) |
| NAT Gateway | App에서 외부 호출(SES·AI 개발 백엔드) 시 사용 |

---

## 3. 인스턴스 사양 (권장 초기 사양)

### 3-1. App 서버

| 환경 | EC2 타입 | vCPU | 메모리 | 인스턴스 수 | 비고 |
|------|----------|:----:|:------:|:----------:|------|
| dev | t3.small | 2 | 2 GB | 1 | 단순 검증용 |
| staging | t3.medium | 2 | 4 GB | 1 | UAT 부하 |
| prod | t3.medium | 2 | 4 GB | 2 (AZ 분산) | Auto Scaling Group 권장(추후) |
| worker (prod) | t3.small | 2 | 2 GB | 1 | AI 개발 큐·이메일 워커 |

### 3-2. RDS PostgreSQL

| 환경 | 인스턴스 | 스토리지 | Multi-AZ | 백업 |
|------|---------|---------|:--------:|------|
| dev | db.t3.micro | 20 GB gp3 | ❌ | 1일 |
| staging | db.t3.small | 50 GB gp3 | ❌ | 3일 |
| prod | db.t3.medium | 100 GB gp3 (Autoscaling 활성) | ✅ | 7일 |

### 3-3. S3

| 버킷 | 환경 | 버전 관리 | 암호화 | 보존 |
|------|------|:--------:|:------:|------|
| spp-deliverables-dev | dev | ❌ | SSE-S3 | — |
| spp-deliverables-staging | staging | ✅ | SSE-S3 | — |
| spp-deliverables-prod | prod | ✅ | SSE-S3 | 종료 후 1년 (REQ-NFR-005) |
| spp-audit-prod | prod | ❌ | SSE-S3 | 1년 (REQ-AUD-001), Object Lock 검토 |

---

## 4. 운영 관찰성·모니터링

| 항목 | 도구 | 용도 |
|------|------|------|
| 로그 수집 | CloudWatch Logs | 애플리케이션 로그, 액세스 로그 |
| 메트릭 | CloudWatch Metrics | CPU·메모리·DB 연결·5xx 응답 수 |
| 알람 | CloudWatch Alarms | 5xx 임계치 초과, DB 연결 실패, 디스크 80%↑ |
| 분산 추적 | (MVP 미포함) | 추후 Spring Cloud Sleuth·OpenTelemetry 검토 |
| 사용자 행위 추적 | 감사 로그 (REQ-AUD-001) | DB append-only 테이블 |
| 헬스체크 | `/actuator/health` | ALB 헬스체크 엔드포인트 |

---

## 5. 비밀·시크릿 관리

| 항목 | 저장 위치 |
|------|----------|
| DB 비밀번호 | AWS Secrets Manager 또는 환경변수 (운영 결정) |
| SES API 키 / SMTP 자격증명 | Secrets Manager |
| 세션 키 / 암호화 키 | Secrets Manager |
| AI 개발 백엔드 토큰 | Secrets Manager |
| 환경변수 주입 | Spring Boot `application-{env}.yml` + `${ENV_VAR}` 바인딩 |

> 글로벌 컨벤션: 시크릿 기본값 하드코딩 금지. 환경변수 없으면 애플리케이션 시작 실패.

---

## 6. 백업·재해복구

| 항목 | 정책 |
|------|------|
| DB 백업 | RDS 자동 스냅샷 일일, 7일 보존. 주간 수동 스냅샷 4주 보존. |
| S3 백업 | 버전 관리 + 90일 라이프사이클로 이전 버전 Glacier 이동 |
| 감사 로그 | append-only, 변조 불가 (REQ-AUD-001). 별도 버킷 Object Lock 검토 |
| 복구 목표 | RTO 4h / RPO 1h (MVP 기준, 추후 조정) |
| 재해 복구 절차 | 별도 산출물 No.24 장애 대응 가이드에서 상세 정의 |

---

## 7. 운영 비용 가이드 (대략)

| 항목 | 월간 예상 (USD) | 비고 |
|------|----------------|------|
| EC2 (prod App ×2 + Worker) | ~120 | t3.medium ×2 + t3.small ×1 |
| RDS (prod Multi-AZ) | ~110 | db.t3.medium Multi-AZ |
| S3 (산출물) | ~10 | 100 GB 기준 |
| ALB | ~25 | — |
| Route 53 | ~1 | — |
| SES | ~5 | 5만 통/월 기준 |
| 데이터 전송 | ~10 | — |
| **합계 (운영)** | **~280 USD/월** | dev·staging 포함 시 +100~150 |

> 견적은 us-east-1 기준 대략치. 실제 운영 시 트래픽·저장 용량에 따라 변동.

---

## 8. 결정 사항·전제

| 항목 | 결정 | 사유 |
|------|------|------|
| AWS 채택 | 클라우드 표준, 한국 리전(ap-northeast-2) 가능 | 보편적 운영, 매뉴얼 풍부 |
| 단일 VPC | 환경별 VPC 분리 안 함 | MVP 단순화. 운영 본격화 시 분리 검토 |
| Multi-AZ 운영 | prod만 적용, dev/staging 단일 AZ | 비용 절감 |
| DB 큐 (MVP) | SQS·RabbitMQ 채택 안 함 | 운영 단순화. 부하 증가 시 마이그레이션 |
| Spring Session JDBC | Redis 안 씀 | 인프라 단순화. 다중 인스턴스 정합 시 Redis 전환 검토 |
| Auto Scaling 미적용 | prod도 고정 2대 | MVP 트래픽 예측 불가, 운영 데이터 기반 추후 적용 |
| Object Storage 분리 | 산출물·감사 로그 버킷 분리 | 권한·보존 정책 분리 |
| 비용 최적화 | t3 시리즈 채택 (burstable) | 초기 부하 적음, Reserved Instance는 1년 후 검토 |

---

## 변경 이력

| 버전 | 날짜 | 내용 |
|------|------|------|
| v0.1 | 2026-05-19 | 초안 작성 (S/W 스택·환경별 구성·인스턴스 사양·모니터링·시크릿·백업·비용·결정 사항) |
