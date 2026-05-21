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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SlotServiceIT {

    @Autowired SlotService slotService;
    @Autowired ProjectService projectService;
    @Autowired DeliverableSlotMapper slotMapper;
    @Autowired SlotVersionMapper versionMapper;
    @Autowired FileAssetMapper assetMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account outsider;

    @BeforeEach
    void seed() {
        ensureOrg("슬롯조직");
        pm = ensure("slot-pm@test.com", "팀PM", "team", "pm", null);
        outsider = ensure("slot-out@test.com", "외부", "team", "developer", null);
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

    private Project newProject() {
        return projectService.create(new CreateProjectRequest(
                "슬롯 " + System.nanoTime(), "슬롯조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
    }

    private FileAsset upload(Long projectId, String slotType, String name, byte[] bytes) {
        return slotService.uploadFile(projectId, slotType, name, "application/pdf", bytes.length,
                new ByteArrayInputStream(bytes), pm);
    }

    @Test
    void create_seedsFiveEmptySlots() {
        Project p = newProject();
        List<DeliverableSlot> slots = slotMapper.findByProject(p.getId());
        assertThat(slots).extracting(DeliverableSlot::getSlotType)
                .containsExactly("requirements", "ia", "design", "prototype", "figma");
        assertThat(slots).allMatch(s -> "empty".equals(s.getStatus()));
    }

    @Test
    void upload_createsDraftVersion_andSyncsSlotStatus() {
        Project p = newProject();
        FileAsset asset = upload(p.getId(), "requirements", "spec.pdf", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(asset.getId()).isNotNull();
        assertThat(asset.getStorageKey()).isNotBlank();

        SlotDetail detail = slotService.slotDetail(p.getId(), "requirements", pm);
        assertThat(detail.slot().getStatus()).isEqualTo("draft");
        assertThat(detail.version().getVersionNo()).isEqualTo((short) 1);
        assertThat(detail.version().getStatus()).isEqualTo("draft");
        assertThat(detail.files()).hasSize(1);
        assertThat(detail.files().get(0).getLogicalKey()).isEqualTo("spec.pdf");
    }

    @Test
    void upload_duplicateName_getsUniqueLogicalKey() {
        Project p = newProject();
        upload(p.getId(), "ia", "doc.pdf", "a".getBytes());
        upload(p.getId(), "ia", "doc.pdf", "b".getBytes());

        SlotDetail detail = slotService.slotDetail(p.getId(), "ia", pm);
        assertThat(detail.files()).hasSize(2);
        assertThat(detail.files()).extracting(FileAsset::getLogicalKey)
                .containsExactlyInAnyOrder("doc.pdf", "doc.pdf (2)");
    }

    @Test
    void deleteAsset_removesFromDraft() {
        Project p = newProject();
        FileAsset asset = upload(p.getId(), "design", "img.png", "x".getBytes());
        slotService.deleteAsset(p.getId(), "design", asset.getId(), pm);

        SlotDetail detail = slotService.slotDetail(p.getId(), "design", pm);
        assertThat(detail.files()).isEmpty();
    }

    @Test
    void addUrl_createsUrlAsset() {
        Project p = newProject();
        FileAsset asset = slotService.addUrl(p.getId(), "figma", "Figma 링크", "https://figma.com/file/x", pm);
        assertThat(asset.getAssetKind()).isEqualTo("url");
        assertThat(asset.getExternalUrl()).contains("figma.com");

        SlotDetail detail = slotService.slotDetail(p.getId(), "figma", pm);
        assertThat(detail.files()).hasSize(1);
    }

    @Test
    void upload_byNonMember_isForbidden() {
        Project p = newProject();
        assertThatThrownBy(() ->
                slotService.uploadFile(p.getId(), "requirements", "x.pdf", "application/pdf", 3,
                        new ByteArrayInputStream("abc".getBytes()), outsider))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void upload_toLockedVersion_isRejected() {
        Project p = newProject();
        upload(p.getId(), "prototype", "v1.pdf", "x".getBytes());

        // 슬롯을 잠금 상태로 (ensureDraftVersionLocked는 슬롯 상태로 판정. B3 전 직접 전이)
        DeliverableSlot slot = slotMapper.findByProjectAndType(p.getId(), "prototype");
        slotMapper.updateStatus(slot.getId(), "pending-review");

        assertThatThrownBy(() -> upload(p.getId(), "prototype", "v2.pdf", "y".getBytes()))
                .isInstanceOf(ApiException.class);
    }
}
