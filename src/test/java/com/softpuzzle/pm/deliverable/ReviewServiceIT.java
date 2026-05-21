package com.softpuzzle.pm.deliverable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectGate;
import com.softpuzzle.pm.project.ProjectGateMapper;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewServiceIT {

    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired ProjectService projectService;
    @Autowired DeliverableSlotMapper slotMapper;
    @Autowired ProjectGateMapper gateMapper;
    @Autowired ActivityEventMapper activityMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        ensureOrg("리뷰조직");
        pm = ensure("rv-pm@test.com", "팀PM", "team", "pm", null);
        Long orgId = clientOrgMapper.findByName("리뷰조직").getId();
        client = ensure("rv-client@test.com", "고객", "client", null, orgId);
    }

    private void ensureOrg(String name) {
        if (clientOrgMapper.findByName(name) == null) {
            ClientOrg o = new ClientOrg();
            o.setName(name);
            clientOrgMapper.insert(o);
        }
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
                "리뷰 " + System.nanoTime(), "리뷰조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    private void upload(Long projectId, String slotType) {
        slotService.uploadFile(projectId, slotType, slotType + ".pdf", "application/pdf", 3,
                new ByteArrayInputStream("abc".getBytes()), pm);
    }

    private Map<Short, String> gates(Long projectId) {
        return gateMapper.findByProject(projectId).stream()
                .collect(Collectors.toMap(ProjectGate::getGateStage, ProjectGate::getStatus));
    }

    @Test
    void request_thenConfirm_passesGate9_andUnlocks11() {
        Project p = project();
        upload(p.getId(), "requirements");

        reviewService.requestReview(p.getId(), "requirements", pm);
        assertThat(slotMapper.findByProjectAndType(p.getId(), "requirements").getStatus())
                .isEqualTo("pending-review");

        reviewService.confirm(p.getId(), "requirements", client);
        assertThat(slotMapper.findByProjectAndType(p.getId(), "requirements").getStatus())
                .isEqualTo("confirmed");

        Map<Short, String> gates = gates(p.getId());
        assertThat(gates.get((short) 9)).isEqualTo("pass");
        assertThat(gates.get((short) 11)).isEqualTo("wait");
        assertThat(gates.get((short) 13)).isEqualTo("lock");

        Long slotId = slotMapper.findByProjectAndType(p.getId(), "requirements").getId();
        assertThat(activityMapper.findBySlot(slotId)).extracting(ActivityEvent::getEventType)
                .contains("review_requested", "confirmed");
    }

    @Test
    void reject_setsRejected_andStoresReasonInActivity() {
        Project p = project();
        upload(p.getId(), "requirements");
        reviewService.requestReview(p.getId(), "requirements", pm);

        reviewService.reject(p.getId(), "requirements", "여백이 부족합니다", client);

        DeliverableSlot slot = slotMapper.findByProjectAndType(p.getId(), "requirements");
        assertThat(slot.getStatus()).isEqualTo("rejected");
        assertThat(activityMapper.findBySlot(slot.getId()))
                .anyMatch(e -> "rejected".equals(e.getEventType()) && "여백이 부족합니다".equals(e.getBody()));
    }

    @Test
    void recall_returnsToDraft() {
        Project p = project();
        upload(p.getId(), "requirements");
        reviewService.requestReview(p.getId(), "requirements", pm);

        reviewService.recall(p.getId(), "requirements", pm);
        assertThat(slotMapper.findByProjectAndType(p.getId(), "requirements").getStatus()).isEqualTo("draft");
    }

    @Test
    void confirm_outOfOrder_isRejected() {
        Project p = project();
        upload(p.getId(), "ia");
        reviewService.requestReview(p.getId(), "ia", pm);

        assertThatThrownBy(() -> reviewService.confirm(p.getId(), "ia", client))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Gate 9");
    }

    @Test
    void requestReview_byClient_isForbidden() {
        Project p = project();
        upload(p.getId(), "requirements");
        assertThatThrownBy(() -> reviewService.requestReview(p.getId(), "requirements", client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void confirm_byTeam_isForbidden() {
        Project p = project();
        upload(p.getId(), "requirements");
        reviewService.requestReview(p.getId(), "requirements", pm);
        assertThatThrownBy(() -> reviewService.confirm(p.getId(), "requirements", pm))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requestReview_onEmptySlot_isRejected() {
        Project p = project();
        assertThatThrownBy(() -> reviewService.requestReview(p.getId(), "design", pm))
                .isInstanceOf(ApiException.class);
    }
}
