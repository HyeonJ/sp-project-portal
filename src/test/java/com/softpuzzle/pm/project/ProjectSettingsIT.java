package com.softpuzzle.pm.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import com.softpuzzle.pm.project.dto.EditProjectRequest;
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
class ProjectSettingsIT {

    @Autowired ProjectService projectService;
    @Autowired ProjectMapper projectMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("설정조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("설정조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("설정조직").getId();
        pm = ensure("set-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("set-client@test.com", "고객", "client", null, orgId);
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
                "설정 " + System.nanoTime(), "설정조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    @Test
    void editInfo_updatesFields() {
        Project p = project();
        projectService.editInfo(p.getId(),
                new EditProjectRequest("변경된 이름", "웹사이트", "새 설명", LocalDate.now(), LocalDate.now().plusDays(20)), pm);
        Project updated = projectMapper.findById(p.getId());
        assertThat(updated.getName()).isEqualTo("변경된 이름");
        assertThat(updated.getType()).isEqualTo("웹사이트");
        assertThat(updated.getDescription()).isEqualTo("새 설명");
    }

    @Test
    void editInfo_byClient_isForbidden() {
        Project p = project();
        assertThatThrownBy(() -> projectService.editInfo(p.getId(),
                new EditProjectRequest("x", null, null, null, null), client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void archive_setsStatus() {
        Project p = project();
        projectService.archive(p.getId(), pm);
        assertThat(projectMapper.findById(p.getId()).getStatus()).isEqualTo("archived");
    }

    @Test
    void softDelete_requiresExactName_andExcludesFromList() {
        Project p = project();
        assertThatThrownBy(() -> projectService.softDelete(p.getId(), "틀린이름", pm))
                .isInstanceOf(ApiException.class);

        projectService.softDelete(p.getId(), p.getName(), pm);
        assertThat(projectMapper.findById(p.getId()).getStatus()).isEqualTo("deleted");
        assertThat(projectService.myProjects(pm)).extracting(Project::getId).doesNotContain(p.getId());
    }
}
