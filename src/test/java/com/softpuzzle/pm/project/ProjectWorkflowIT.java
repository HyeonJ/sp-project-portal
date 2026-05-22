package com.softpuzzle.pm.project;

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
import com.softpuzzle.pm.dev.DevRunService;
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
class ProjectWorkflowIT {

    @Autowired ProjectService projectService;
    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired DevRunService devRunService;
    @Autowired ProjectMapper projectMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("워크플로조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("워크플로조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("워크플로조직").getId();
        pm = ensure("wf-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("wf-client@test.com", "고객", "client", null, orgId);
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
                "워크플로 " + System.nanoTime(), "워크플로조직", "SaaS", null,
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

    private short stage(Long pid) {
        return projectMapper.findById(pid).getCurrentStage();
    }

    @Test
    void confirm_bumpsCurrentStage() {
        Project p = project();
        assertThat(stage(p.getId())).isEqualTo((short) 1);
        confirmSlot(p.getId(), "requirements");
        assertThat(stage(p.getId())).isEqualTo((short) 5);
    }

    @Test
    void devTrigger_advancesTo14_andUnlocksUatGate() {
        Project p = project();
        confirmSlot(p.getId(), "requirements");
        confirmSlot(p.getId(), "ia");
        confirmSlot(p.getId(), "design");
        confirmSlot(p.getId(), "prototype");
        confirmSlot(p.getId(), "figma");
        devRunService.trigger(p.getId(), pm);
        assertThat(stage(p.getId())).isEqualTo((short) 14);
    }

    @Test
    void uatApprove_afterDev_completesProject() {
        Project p = project();
        confirmSlot(p.getId(), "requirements");
        confirmSlot(p.getId(), "ia");
        confirmSlot(p.getId(), "design");
        confirmSlot(p.getId(), "prototype");
        confirmSlot(p.getId(), "figma");
        devRunService.trigger(p.getId(), pm);

        projectService.uatApprove(p.getId(), client);
        Project done = projectMapper.findById(p.getId());
        assertThat(done.getStatus()).isEqualTo("completed");
        assertThat(done.getCurrentStage()).isEqualTo((short) 20);
    }

    @Test
    void uatApprove_beforeDev_isRejected() {
        Project p = project();
        confirmSlot(p.getId(), "requirements");
        confirmSlot(p.getId(), "ia");
        confirmSlot(p.getId(), "design");
        confirmSlot(p.getId(), "prototype");
        // dev 미실행 → gate22 lock
        assertThatThrownBy(() -> projectService.uatApprove(p.getId(), client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void uatApprove_byTeam_isForbidden() {
        Project p = project();
        assertThatThrownBy(() -> projectService.uatApprove(p.getId(), pm))
                .isInstanceOf(ApiException.class);
    }
}
