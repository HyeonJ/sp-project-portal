package com.softpuzzle.pm.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.deliverable.ReviewService;
import com.softpuzzle.pm.deliverable.SlotService;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import com.softpuzzle.pm.qa.DefectService;
import com.softpuzzle.pm.qa.TestCaseService;
import com.softpuzzle.pm.qa.dto.CreateDefectRequest;
import com.softpuzzle.pm.qa.dto.CreateTestCaseRequest;
import java.io.ByteArrayInputStream;
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
class DashboardServiceIT {

    @Autowired DashboardService dashboardService;
    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired DefectService defectService;
    @Autowired TestCaseService tcService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;
    private Account outsider;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("대시조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("대시조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("대시조직").getId();
        pm = ensure("dash-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("dash-client@test.com", "고객", "client", null, orgId);
        outsider = ensure("dash-out@test.com", "외부", "team", "developer", null);
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

    private Project project() {
        Project p = projectService.create(new CreateProjectRequest(
                "대시 " + System.nanoTime(), "대시조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboard_aggregatesCounts() {
        Project p = project();
        slotService.uploadFile(p.getId(), "requirements", "spec.pdf", "application/pdf", 3,
                new ByteArrayInputStream("abc".getBytes()), pm);
        reviewService.requestReview(p.getId(), "requirements", pm);
        reviewService.confirm(p.getId(), "requirements", client);
        tcService.create(p.getId(), new CreateTestCaseRequest("로그인", "인증", "High", null, null, null), pm);
        defectService.create(p.getId(), new CreateDefectRequest("버그", "High", null, null, null), pm);

        Map<String, Object> data = dashboardService.dashboard(p.getId(), pm);
        Map<String, Object> counts = (Map<String, Object>) data.get("counts");
        assertThat(((Number) counts.get("confirmedSlots")).intValue()).isEqualTo(1);
        assertThat(((Number) counts.get("openDefects")).intValue()).isEqualTo(1);
        assertThat(((Number) counts.get("totalTc")).intValue()).isEqualTo(1);

        Map<String, Object> progress = (Map<String, Object>) data.get("progress");
        assertThat(((Number) progress.get("gatesPassed")).intValue()).isEqualTo(1);
    }

    @Test
    void dashboard_byNonMember_forbidden() {
        Project p = project();
        assertThatThrownBy(() -> dashboardService.dashboard(p.getId(), outsider))
                .isInstanceOf(ApiException.class);
    }
}
