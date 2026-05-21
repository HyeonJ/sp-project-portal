package com.softpuzzle.pm.deliverable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.deliverable.dto.SlotDetail;
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
class VersionServiceIT {

    @Autowired VersionService versionService;
    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired ProjectService projectService;
    @Autowired DeliverableSlotMapper slotMapper;
    @Autowired SlotVersionMapper versionMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("버전조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("버전조직");
            clientOrgMapper.insert(o);
        }
        pm = ensure("ver-pm@test.com", "팀PM", "team", "pm", null);
        Long orgId = clientOrgMapper.findByName("버전조직").getId();
        client = ensure("ver-client@test.com", "고객", "client", null, orgId);
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
                "버전 " + System.nanoTime(), "버전조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    private void upload(Long pid, String slotType) {
        slotService.uploadFile(pid, slotType, slotType + ".pdf", "application/pdf", 3,
                new ByteArrayInputStream("abc".getBytes()), pm);
    }

    private void requestAndConfirm(Long pid, String slotType) {
        reviewService.requestReview(pid, slotType, pm);
        reviewService.confirm(pid, slotType, client);
    }

    @Test
    void newVersion_fromConfirmed_copiesFiles_keepsOldConfirmed_logsInvalidated() {
        Project p = project();
        upload(p.getId(), "requirements");
        requestAndConfirm(p.getId(), "requirements");
        Long v1Id = slotMapper.findByProjectAndType(p.getId(), "requirements").getCurrentVersionId();

        SlotVersion v2 = versionService.createNewVersion(p.getId(), "requirements", "2차 정의", pm);

        assertThat(v2.getVersionNo()).isEqualTo((short) 2);
        // 슬롯 포인터 이동 + draft
        DeliverableSlot slot = slotMapper.findByProjectAndType(p.getId(), "requirements");
        assertThat(slot.getStatus()).isEqualTo("draft");
        assertThat(slot.getCurrentVersionId()).isEqualTo(v2.getId());
        // 과거 컨펌 버전 보존
        assertThat(versionMapper.findById(v1Id).getStatus()).isEqualTo("confirmed");
        // 파일 사본
        SlotDetail detail = slotService.slotDetail(p.getId(), "requirements", pm);
        assertThat(detail.files()).hasSize(1);
        assertThat(detail.files().get(0).getLogicalKey()).isEqualTo("requirements.pdf");
    }

    @Test
    void newVersion_requiresChangeSummary() {
        Project p = project();
        upload(p.getId(), "requirements");
        requestAndConfirm(p.getId(), "requirements");
        assertThatThrownBy(() -> versionService.createNewVersion(p.getId(), "requirements", "  ", pm))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void newVersion_blockedWhenDraftExists() {
        Project p = project();
        upload(p.getId(), "requirements"); // draft 존재
        assertThatThrownBy(() -> versionService.createNewVersion(p.getId(), "requirements", "x", pm))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void upstreamChange_setsBadge_ackClearsIt() {
        Project p = project();
        upload(p.getId(), "requirements");
        requestAndConfirm(p.getId(), "requirements");
        upload(p.getId(), "ia");
        requestAndConfirm(p.getId(), "ia"); // ia가 req v1을 선행으로 스탬프

        assertThat(slotService.slotDetail(p.getId(), "ia", pm).slot().isUpstreamChanged()).isFalse();

        // 요구사항 새 버전 → 컨펌 (req v2)
        versionService.createNewVersion(p.getId(), "requirements", "개정", pm);
        requestAndConfirm(p.getId(), "requirements");

        // ia의 선행 스탬프(req v1) ≠ req 현재(v2) → 배지
        assertThat(slotService.slotDetail(p.getId(), "ia", pm).slot().isUpstreamChanged()).isTrue();

        // 선행 검토 완료 → 배지 해제
        versionService.ackUpstream(p.getId(), "ia", pm);
        assertThat(slotService.slotDetail(p.getId(), "ia", pm).slot().isUpstreamChanged()).isFalse();
    }
}
