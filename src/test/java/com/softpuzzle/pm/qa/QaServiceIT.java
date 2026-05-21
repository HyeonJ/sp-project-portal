package com.softpuzzle.pm.qa;

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
import com.softpuzzle.pm.qa.dto.CreateDefectRequest;
import com.softpuzzle.pm.qa.dto.CreateTestCaseRequest;
import com.softpuzzle.pm.qa.dto.DefectDetail;
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
class QaServiceIT {

    @Autowired TestCaseService tcService;
    @Autowired DefectService defectService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("QA조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("QA조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("QA조직").getId();
        pm = ensure("qa-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("qa-client@test.com", "고객", "client", null, orgId);
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
                "QA " + System.nanoTime(), "QA조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        return p;
    }

    private CreateTestCaseRequest tcReq(String title) {
        return new CreateTestCaseRequest(title, "인증", "High", null, "1. 로그인", "성공");
    }

    @Test
    void tc_create_assignsSequentialProjectCodes() {
        Project p = project();
        TestCase a = tcService.create(p.getId(), tcReq("로그인"), pm);
        TestCase b = tcService.create(p.getId(), tcReq("로그아웃"), pm);
        assertThat(a.getCode()).isEqualTo("TC-001");
        assertThat(b.getCode()).isEqualTo("TC-002");
        assertThat(tcService.list(p.getId(), pm)).hasSize(2);
    }

    @Test
    void tc_csvImport_bulkAssignsCodes() {
        Project p = project();
        int n = tcService.importCsv(p.getId(), "제목,단계,우선순위\n비번재설정,인증,High\n검색,요구사항,Low\n", pm);
        assertThat(n).isEqualTo(2);
        assertThat(tcService.list(p.getId(), pm)).extracting(TestCase::getCode)
                .containsExactly("TC-001", "TC-002");
    }

    @Test
    void tc_create_byClient_isForbidden() {
        Project p = project();
        assertThatThrownBy(() -> tcService.create(p.getId(), tcReq("x"), client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void tc_updateStatus() {
        Project p = project();
        TestCase a = tcService.create(p.getId(), tcReq("로그인"), pm);
        tcService.updateStatus(p.getId(), a.getId(), "failed", "401 발생", pm);
        assertThat(tcService.detail(p.getId(), a.getId(), pm).testCase().getStatus()).isEqualTo("failed");
    }

    @Test
    void defect_create_byClient_withTcLink_andCodeDef001() {
        Project p = project();
        TestCase tc = tcService.create(p.getId(), tcReq("로그인"), pm);
        Defect d = defectService.create(p.getId(),
                new CreateDefectRequest("로그인 401", "High", "1. 시도", "Chrome", tc.getId()), client);
        assertThat(d.getCode()).isEqualTo("DEF-001");
        assertThat(d.getReporterId()).isEqualTo(client.getId());

        DefectDetail detail = defectService.detail(p.getId(), d.getId(), pm);
        assertThat(detail.linkedTestCases()).extracting(TestCase::getId).contains(tc.getId());
    }

    @Test
    void defect_updateStatus_byClient_isForbidden_byTeamOk() {
        Project p = project();
        Defect d = defectService.create(p.getId(),
                new CreateDefectRequest("버그", "Medium", null, null, null), client);
        assertThatThrownBy(() -> defectService.updateStatus(p.getId(), d.getId(), "resolved", client))
                .isInstanceOf(ApiException.class);
        defectService.updateStatus(p.getId(), d.getId(), "in_progress", pm);
        assertThat(defectService.detail(p.getId(), d.getId(), pm).defect().getStatus()).isEqualTo("in_progress");
    }

    @Test
    void defect_attachment_uploadDownloadDelete() throws IOException {
        Project p = project();
        Defect d = defectService.create(p.getId(),
                new CreateDefectRequest("버그", "High", null, null, null), pm);
        DefectAttachment a = defectService.addAttachment(p.getId(), d.getId(), "log.txt", "text/plain", 5,
                new ByteArrayInputStream("hello".getBytes()), pm);

        DefectAttachment dl = defectService.attachmentForDownload(p.getId(), d.getId(), a.getId(), client);
        try (InputStream in = defectService.openAttachment(dl.getStorageKey())) {
            assertThat(in.readAllBytes()).hasSize(5);
        }

        defectService.deleteAttachment(p.getId(), d.getId(), a.getId(), pm);
        assertThat(defectService.detail(p.getId(), d.getId(), pm).attachments()).isEmpty();
    }
}
