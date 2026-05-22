package com.softpuzzle.pm.common;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.deliverable.ReviewService;
import com.softpuzzle.pm.deliverable.SlotService;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** dev 시드 (local/dev/docker 프로필만, 멱등). 조직·계정·샘플 프로젝트 3개(진행 단계 다양). */
@Component
@Profile({"local", "dev", "docker"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_PASSWORD = "Passw0rd!";

    /** 슬롯별 시드 파일명. */
    private static final Map<String, String> SLOT_FILE = Map.of(
            "requirements", "요구사항정의서-v1.pdf",
            "ia", "IA-사이트맵-v1.txt",
            "design", "디자인시안-A안.txt",
            "prototype", "프로토타입-v1.txt");

    /** 요구사항 슬롯 시드 파일 — 미리보기 시연용 3페이지 PDF (classpath). */
    private static final String REQUIREMENTS_PDF = "/seed/requirements-sample.pdf";

    private final AccountMapper accountMapper;
    private final ClientOrgMapper clientOrgMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final SlotService slotService;
    private final ReviewService reviewService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AccountMapper accountMapper, ClientOrgMapper clientOrgMapper,
                      ProjectMapper projectMapper, ProjectService projectService,
                      SlotService slotService, ReviewService reviewService,
                      PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.clientOrgMapper = clientOrgMapper;
        this.projectMapper = projectMapper;
        this.projectService = projectService;
        this.slotService = slotService;
        this.reviewService = reviewService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        ensureAccount("admin@softpuzzle.com", "관리자", "admin", null, null);
        ensureAccount("pm@softpuzzle.com", "박PM", "team", "pm", null);
        Account pm = accountMapper.findByEmail("pm@softpuzzle.com");
        if (pm == null || !projectMapper.findActiveByMember(pm.getId()).isEmpty()) {
            return; // 멱등 — 이미 시드됨
        }

        // ACME — 요구사항~프로토타입 컨펌 완료 + Figma 핸드오프 등록 (단계 12, 게이트 5·7·9·11 통과·게이트 13 대기)
        Project acme = seedProject("ACME 포털 구축", "㈜ACME", "SaaS", "고객사 ACME의 B2B 포털 구축 프로젝트 (샘플)",
                "client@acme.com", "고객담당", pm, LocalDate.now().minusWeeks(6), LocalDate.now().plusMonths(3),
                List.of("requirements", "ia", "design", "prototype"));
        // 12단계: Figma 핸드오프 등록 (draft·미컨펌) → current_stage 12
        slotService.addUrl(acme.getId(), "figma", "Figma 핸드오프 (Dev Mode)",
                "https://www.figma.com/file/SPpm/handoff", pm);
        projectMapper.bumpStage(acme.getId(), 12);

        // 베타 — 요구사항만 컨펌 (단계 5, 게이트 5 통과)
        seedProject("베타 커머스 리뉴얼", "㈜베타", "웹사이트", "베타 커머스 사이트 리뉴얼 (샘플)",
                "client@beta.com", "최베타", pm, LocalDate.now().minusWeeks(2), LocalDate.now().plusMonths(2),
                List.of("requirements"));

        // 감마 — 착수 직후 (단계 1, 슬롯 비어 있음)
        seedProject("감마 모바일 앱", "㈜감마", "모바일", "감마 신규 모바일 앱 구축 (샘플)",
                "client@gamma.com", "정감마", pm, LocalDate.now(), LocalDate.now().plusMonths(4),
                List.of());

        log.info("[DataSeeder] 시드 완료 (프로젝트 3개, 비밀번호: {})", SEED_PASSWORD);
    }

    /** 프로젝트 생성 + 고객사 멤버 추가 + confirmSlots 순서대로 업로드→검토요청→컨펌(게이트 통과). */
    private Project seedProject(String name, String orgName, String type, String desc,
                               String clientEmail, String clientName, Account pm,
                               LocalDate start, LocalDate end, List<String> confirmSlots) {
        Long orgId = ensureOrg(orgName);
        ensureAccount(clientEmail, clientName, "client", null, orgId);
        Account client = accountMapper.findByEmail(clientEmail);

        Project project = projectService.create(
                new CreateProjectRequest(name, orgName, type, desc, start, end), pm);
        projectService.addMember(project.getId(), client.getId(), pm.getId());

        for (String slot : confirmSlots) {
            progressSlot(project.getId(), slot, pm, client);
        }
        log.info("[DataSeeder] 프로젝트 시드 id={} name={} 컨펌슬롯={}", project.getId(), name, confirmSlots);
        return project;
    }

    /** 한 슬롯: 파일 업로드(draft) → 검토 요청(잠금) → 컨펌(게이트 통과·단계 전진). */
    private void progressSlot(Long projectId, String slotType, Account pm, Account client) {
        String fileName = SLOT_FILE.getOrDefault(slotType, slotType + "-v1.txt");
        byte[] content;
        String contentType;
        if ("requirements".equals(slotType)) {
            content = readClasspathBytes(REQUIREMENTS_PDF);
            contentType = "application/pdf";
        } else {
            content = ("샘플 " + slotType + " 산출물 (시드 데이터)\n프로젝트 " + projectId).getBytes(StandardCharsets.UTF_8);
            contentType = "text/plain";
        }
        slotService.uploadFile(projectId, slotType, fileName, contentType, content.length,
                new ByteArrayInputStream(content), pm);
        reviewService.requestReview(projectId, slotType, pm);
        reviewService.confirm(projectId, slotType, client);
    }

    private byte[] readClasspathBytes(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("시드 리소스를 찾을 수 없음: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("시드 리소스 읽기 실패: " + path, e);
        }
    }

    private Long ensureOrg(String name) {
        ClientOrg org = clientOrgMapper.findByName(name);
        if (org != null) {
            return org.getId();
        }
        ClientOrg created = new ClientOrg();
        created.setName(name);
        clientOrgMapper.insert(created);
        return created.getId();
    }

    private void ensureAccount(String email, String name, String tier, String job, Long clientOrgId) {
        if (accountMapper.findByEmail(email) != null) {
            return;
        }
        Account a = new Account();
        a.setEmail(email);
        a.setName(name);
        a.setTier(tier);
        a.setJob(job);
        a.setClientOrgId(clientOrgId);
        a.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
        a.setStatus("active");
        accountMapper.insert(a);
        log.info("[DataSeeder] 계정 생성 email={} tier={}", email, tier);
    }
}
