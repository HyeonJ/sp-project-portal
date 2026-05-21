package com.softpuzzle.pm.common;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** dev 시드 (local/dev 프로필만, 멱등). 조직·계정·샘플 프로젝트. */
@Component
@Profile({"local", "dev", "docker"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_PASSWORD = "Passw0rd!";
    private static final String ORG_NAME = "㈜ACME";

    private final AccountMapper accountMapper;
    private final ClientOrgMapper clientOrgMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AccountMapper accountMapper, ClientOrgMapper clientOrgMapper,
                      ProjectMapper projectMapper, ProjectService projectService,
                      PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.clientOrgMapper = clientOrgMapper;
        this.projectMapper = projectMapper;
        this.projectService = projectService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long orgId = ensureOrg(ORG_NAME);
        ensureAccount("admin@softpuzzle.com", "관리자", "admin", null, null);
        ensureAccount("pm@softpuzzle.com", "박PM", "team", "pm", null);
        ensureAccount("client@acme.com", "고객담당", "client", null, orgId);
        ensureSampleProject();
        log.info("[DataSeeder] 시드 완료 (비밀번호: {})", SEED_PASSWORD);
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

    private void ensureSampleProject() {
        Account pm = accountMapper.findByEmail("pm@softpuzzle.com");
        if (pm == null || !projectMapper.findActiveByMember(pm.getId()).isEmpty()) {
            return;
        }
        CreateProjectRequest req = new CreateProjectRequest(
                "ACME 포털 구축", ORG_NAME, "SaaS",
                "고객사 ACME의 B2B 포털 구축 프로젝트 (샘플)",
                LocalDate.now(), LocalDate.now().plusMonths(3));
        Project project = projectService.create(req, pm);

        Account client = accountMapper.findByEmail("client@acme.com");
        if (client != null) {
            projectService.addMember(project.getId(), client.getId(), pm.getId());
        }
        log.info("[DataSeeder] 샘플 프로젝트 생성 id={}", project.getId());
    }
}
