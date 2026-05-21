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
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
class CommentServiceIT {

    @Autowired CommentService commentService;
    @Autowired SlotService slotService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;
    private Account admin;
    private Account outsider;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("코멘트조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("코멘트조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("코멘트조직").getId();
        pm = ensure("cm-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("cm-client@test.com", "고객", "client", null, orgId);
        admin = ensure("cm-admin@test.com", "관리자", "admin", null, null);
        outsider = ensure("cm-out@test.com", "외부", "team", "developer", null);
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

    private Project projectWithFile() {
        Project p = projectService.create(new CreateProjectRequest(
                "코멘트 " + System.nanoTime(), "코멘트조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        slotService.uploadFile(p.getId(), "requirements", "spec.pdf", "application/pdf", 5,
                new ByteArrayInputStream("hello".getBytes()), pm);
        return p;
    }

    @Test
    void add_list_edit_delete_byAuthor() {
        Project p = projectWithFile();
        Comment c = commentService.add(p.getId(), "requirements", "첫 코멘트", pm);
        assertThat(commentService.list(p.getId(), "requirements", pm)).hasSize(1);

        commentService.edit(p.getId(), c.getId(), "수정된 코멘트", pm);
        var list = commentService.list(p.getId(), "requirements", pm);
        assertThat(list.get(0).getBody()).isEqualTo("수정된 코멘트");
        assertThat(list.get(0).getEditedAt()).isNotNull();

        commentService.delete(p.getId(), c.getId(), pm);
        assertThat(commentService.list(p.getId(), "requirements", pm)).isEmpty();
    }

    @Test
    void edit_byNonAuthor_isForbidden() {
        Project p = projectWithFile();
        Comment c = commentService.add(p.getId(), "requirements", "PM 코멘트", pm);
        assertThatThrownBy(() -> commentService.edit(p.getId(), c.getId(), "변조", client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void delete_byNonAuthorMember_forbidden_byAdmin_ok() {
        Project p = projectWithFile();
        Comment c = commentService.add(p.getId(), "requirements", "PM 코멘트", pm);
        assertThatThrownBy(() -> commentService.delete(p.getId(), c.getId(), client))
                .isInstanceOf(ApiException.class);
        commentService.delete(p.getId(), c.getId(), admin); // 관리자 강제 삭제
        assertThat(commentService.list(p.getId(), "requirements", pm)).isEmpty();
    }

    @Test
    void add_byNonMember_isForbidden() {
        Project p = projectWithFile();
        assertThatThrownBy(() -> commentService.add(p.getId(), "requirements", "외부", outsider))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void download_member_ok_nonMember_forbidden_andStreams() throws IOException {
        Project p = projectWithFile();
        Long assetId = slotService.slotDetail(p.getId(), "requirements", pm).files().get(0).getId();

        FileAsset asset = slotService.assetForDownload(p.getId(), assetId, pm);
        assertThat(asset.getStorageKey()).isNotBlank();
        try (InputStream in = slotService.openAsset(asset.getStorageKey())) {
            assertThat(in.readAllBytes()).isNotEmpty();
        }

        assertThatThrownBy(() -> slotService.assetForDownload(p.getId(), assetId, outsider))
                .isInstanceOf(ApiException.class);
    }
}
