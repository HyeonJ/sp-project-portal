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
import java.time.LocalDate;
import java.util.List;
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
class ProjectServiceIT {

    @Autowired ProjectService projectService;
    @Autowired ProjectMapper projectMapper;
    @Autowired ProjectGateMapper gateMapper;
    @Autowired ProjectMemberMapper memberMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;
    private Account admin;
    private Account outsider;

    @BeforeEach
    void seed() {
        Long orgId = ensureOrg("테스트조직");
        pm = ensure("svc-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("svc-client@test.com", "고객", "client", null, orgId);
        admin = ensure("svc-admin@test.com", "관리자", "admin", null, null);
        outsider = ensure("svc-outsider@test.com", "외부팀", "team", "developer", null);
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

    private CreateProjectRequest req() {
        return new CreateProjectRequest("프로젝트 " + System.nanoTime(), "테스트조직", "SaaS",
                "설명", LocalDate.now(), LocalDate.now().plusDays(30));
    }

    @Test
    void create_seedsFiveGates_andCreatorMembership() {
        Project p = projectService.create(req(), pm);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getClientOrgName()).isEqualTo("테스트조직");
        assertThat(p.getCurrentStage()).isEqualTo((short) 1);

        Map<Short, String> gates = gateMapper.findByProject(p.getId()).stream()
                .collect(Collectors.toMap(ProjectGate::getGateStage, ProjectGate::getStatus));
        assertThat(gates).containsOnlyKeys((short) 9, (short) 11, (short) 13, (short) 15, (short) 22);
        assertThat(gates.get((short) 9)).isEqualTo("wait");
        assertThat(gates.get((short) 11)).isEqualTo("lock");

        assertThat(memberMapper.existsActive(p.getId(), pm.getId())).isTrue();
    }

    @Test
    void create_byClient_isForbidden() {
        assertThatThrownBy(() -> projectService.create(req(), client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void view_memberAllowed_adminAllowed_outsiderForbidden() {
        Project p = projectService.create(req(), pm);

        assertThat(projectService.view(p.getId(), pm).getId()).isEqualTo(p.getId());   // 멤버
        assertThat(projectService.view(p.getId(), admin).getId()).isEqualTo(p.getId()); // 관리자 전역

        assertThatThrownBy(() -> projectService.view(p.getId(), outsider))   // 비멤버 팀
                .isInstanceOf(ApiException.class);
    }

    @Test
    void addMember_isIdempotent_andReactivates() {
        Project p = projectService.create(req(), pm);

        projectService.addMember(p.getId(), client.getId(), pm.getId());
        projectService.addMember(p.getId(), client.getId(), pm.getId()); // 멱등
        List<ProjectMember> members = memberMapper.findActiveMembers(p.getId());
        assertThat(members).extracting(ProjectMember::getAccountId).contains(pm.getId(), client.getId());

        memberMapper.softRemove(p.getId(), client.getId());
        assertThat(memberMapper.existsActive(p.getId(), client.getId())).isFalse();

        projectService.addMember(p.getId(), client.getId(), pm.getId()); // reactivate
        assertThat(memberMapper.existsActive(p.getId(), client.getId())).isTrue();
    }

    @Test
    void myProjects_memberSeesOwn_adminSeesAll() {
        Project p = projectService.create(req(), pm);

        assertThat(projectService.myProjects(pm)).extracting(Project::getId).contains(p.getId());
        assertThat(projectService.myProjects(admin)).extracting(Project::getId).contains(p.getId());
        assertThat(projectService.myProjects(outsider)).extracting(Project::getId).doesNotContain(p.getId());
    }
}
