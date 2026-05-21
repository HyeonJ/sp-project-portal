package com.softpuzzle.pm.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.deliverable.ReviewService;
import com.softpuzzle.pm.deliverable.SlotService;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import com.softpuzzle.pm.qa.DefectService;
import com.softpuzzle.pm.qa.TestCaseService;
import com.softpuzzle.pm.qa.dto.CreateDefectRequest;
import com.softpuzzle.pm.qa.dto.CreateTestCaseRequest;
import com.softpuzzle.pm.search.SearchService;
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
class NotifySearchIT {

    @Autowired NotificationService notificationService;
    @Autowired SearchService searchService;
    @Autowired ReviewService reviewService;
    @Autowired SlotService slotService;
    @Autowired TestCaseService tcService;
    @Autowired DefectService defectService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("알림조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("알림조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("알림조직").getId();
        pm = ensure("ns-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("ns-client@test.com", "고객", "client", null, orgId);
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

    private Project project(String name) {
        Project p = projectService.create(new CreateProjectRequest(
                name, "알림조직", "SaaS", null, LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    @Test
    void reviewRequest_notifiesClient_confirm_notifiesTeam() {
        Project p = project("알림 " + System.nanoTime());
        int clientBefore = notificationService.unreadCount(client);
        int pmBefore = notificationService.unreadCount(pm);

        slotService.uploadFile(p.getId(), "requirements", "spec.pdf", "application/pdf", 3,
                new ByteArrayInputStream("abc".getBytes()), pm);
        reviewService.requestReview(p.getId(), "requirements", pm);
        assertThat(notificationService.unreadCount(client)).isEqualTo(clientBefore + 1);
        assertThat(notificationService.list(client).get(0).getBody()).contains("검토 요청");

        reviewService.confirm(p.getId(), "requirements", client);
        assertThat(notificationService.unreadCount(pm)).isEqualTo(pmBefore + 1);
        assertThat(notificationService.list(pm).get(0).getBody()).contains("컨펌");
    }

    @Test
    void search_findsProjectTestCaseDefect_byKeyword() {
        String token = "검색키" + System.nanoTime();
        Project p = project(token + " 프로젝트");
        tcService.create(p.getId(), new CreateTestCaseRequest(token + " TC", "인증", "High", null, null, null), pm);
        defectService.create(p.getId(), new CreateDefectRequest(token + " 결함", "High", null, null, null), pm);

        SearchService.SearchResults r = searchService.search(token, pm);
        assertThat(r.projects()).extracting(Project::getName).anyMatch(n -> n.contains(token));
        assertThat(r.testCases()).extracting(com.softpuzzle.pm.qa.TestCase::getTitle).anyMatch(t -> t.contains(token));
        assertThat(r.defects()).extracting(com.softpuzzle.pm.qa.Defect::getTitle).anyMatch(t -> t.contains(token));
    }

    @Test
    void search_excludesNonMemberProjects() {
        String token = "비공개" + System.nanoTime();
        project(token + " 프로젝트");
        Account outsider = ensure("ns-out@test.com", "외부", "team", "developer", null);
        SearchService.SearchResults r = searchService.search(token, outsider);
        assertThat(r.projects()).isEmpty();
    }
}
