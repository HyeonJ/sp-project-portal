package com.softpuzzle.pm.dev;

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
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DevRunServiceIT {

    @Autowired DevRunService devRunService;
    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("개발조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("개발조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("개발조직").getId();
        pm = ensure("dev-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("dev-client@test.com", "고객", "client", null, orgId);
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
                "개발 " + System.nanoTime(), "개발조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    private void confirmSlot(Long pid, String slotType) {
        slotService.uploadFile(pid, slotType, slotType + ".pdf", "application/pdf", 3,
                new ByteArrayInputStream("abc".getBytes()), pm);
        reviewService.requestReview(pid, slotType, pm);
        reviewService.confirm(pid, slotType, client);
    }

    @Test
    void trigger_blocked_whenGatesNotAllPassed() {
        Project p = project();
        confirmSlot(p.getId(), "requirements"); // G5만 통과
        assertThatThrownBy(() -> devRunService.trigger(p.getId(), pm))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Gate 11");
    }

    @Test
    void trigger_succeeds_whenAllGatesPassed() {
        Project p = project();
        confirmSlot(p.getId(), "requirements");
        confirmSlot(p.getId(), "ia");
        confirmSlot(p.getId(), "design");
        confirmSlot(p.getId(), "prototype");
        confirmSlot(p.getId(), "figma");

        DevRun run = devRunService.trigger(p.getId(), pm);
        assertThat(run.getResult()).isEqualTo("success");
        assertThat(devRunService.history(p.getId(), pm)).extracting(DevRun::getResult).contains("success");
    }

    @Test
    void trigger_byClient_isForbidden() {
        Project p = project();
        assertThatThrownBy(() -> devRunService.trigger(p.getId(), client))
                .isInstanceOf(ApiException.class);
    }
}
