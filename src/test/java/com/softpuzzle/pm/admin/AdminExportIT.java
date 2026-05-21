package com.softpuzzle.pm.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.admin.dto.AdminRequests;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.export.ExportService;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminExportIT {

    @Autowired AdminService adminService;
    @Autowired ExportService exportService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account admin;
    private Account pm;
    private Account outsider;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("관리조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("관리조직");
            clientOrgMapper.insert(o);
        }
        admin = ensure("ad-admin@test.com", "관리자", "admin", null, null);
        pm = ensure("ad-pm@test.com", "팀PM", "team", "pm", null);
        outsider = ensure("ad-out@test.com", "외부", "team", "developer", null);
    }

    private Account ensure(String email, String name, String tier, String job, Long orgId) {
        Account existing = accountMapper.findByEmail(email);
        if (existing != null) {
            return existing;
        }
        Account a = new Account();
        a.setEmail(email);
        a.setName(name);
        a.setTier(tier);
        a.setJob(job);
        a.setClientOrgId(orgId);
        a.setPasswordHash("x");
        a.setStatus("active");
        accountMapper.insert(a);
        return a;
    }

    @Test
    void createAccount_team_andList_andStatusToggle() {
        String email = "new-team-" + System.nanoTime() + "@test.com";
        Account created = adminService.createAccount(
                new AdminRequests.CreateAccount(email, "신규팀원", "team", "designer", null), admin);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getPasswordHash()).isNull(); // 해시 미노출

        assertThat(adminService.listAccounts("team", admin)).extracting(Account::getEmail).contains(email);

        adminService.setStatus(created.getId(), "inactive", admin);
        assertThat(accountMapper.findById(created.getId()).getStatus()).isEqualTo("inactive");
    }

    @Test
    void createAccount_client_findsOrCreatesOrg() {
        String email = "new-client-" + System.nanoTime() + "@test.com";
        Account created = adminService.createAccount(
                new AdminRequests.CreateAccount(email, "신규고객", "client", null, "새고객사"), admin);
        assertThat(created.getClientOrgId()).isNotNull();
        assertThat(clientOrgMapper.findByName("새고객사")).isNotNull();
    }

    @Test
    void adminActions_byNonAdmin_forbidden() {
        assertThatThrownBy(() -> adminService.listAccounts("team", pm)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adminService.allProjects(pm)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adminService.auditLog(pm)).isInstanceOf(ApiException.class);
    }

    @Test
    void auditLog_recordsAccountCreation() {
        adminService.createAccount(
                new AdminRequests.CreateAccount("audit-" + System.nanoTime() + "@test.com", "감사대상", "admin", null, null), admin);
        assertThat(adminService.auditLog(admin)).extracting("action").contains("CREATE_ACCOUNT");
    }

    @Test
    void export_returnsProjectSummary_memberOk_outsiderForbidden() {
        Project p = projectService.create(new CreateProjectRequest(
                "내보내기 " + System.nanoTime(), "관리조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(5)), pm);

        Map<String, Object> data = exportService.export(p.getId(), pm);
        assertThat(data).containsKeys("project", "gates", "deliverables", "testCases", "defects", "exportedAt");
        @SuppressWarnings("unchecked")
        Map<String, Object> proj = (Map<String, Object>) data.get("project");
        assertThat(proj.get("id")).isEqualTo(p.getId());

        assertThatThrownBy(() -> exportService.export(p.getId(), outsider)).isInstanceOf(ApiException.class);
    }
}
