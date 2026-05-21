package com.softpuzzle.pm.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.softpuzzle.pm.TestcontainersConfiguration;
import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMemberMapper;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
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
class MemberAuthIT {

    @Autowired MemberService memberService;
    @Autowired PasswordResetService passwordResetService;
    @Autowired ProjectService projectService;
    @Autowired AccountMapper accountMapper;
    @Autowired ProjectMemberMapper memberMapper;
    @Autowired ClientOrgMapper clientOrgMapper;

    private Account pm;
    private Account client;

    @BeforeEach
    void seed() {
        if (clientOrgMapper.findByName("멤버조직") == null) {
            ClientOrg o = new ClientOrg();
            o.setName("멤버조직");
            clientOrgMapper.insert(o);
        }
        Long orgId = clientOrgMapper.findByName("멤버조직").getId();
        pm = ensure("mb-pm@test.com", "팀PM", "team", "pm", null);
        client = ensure("mb-client@test.com", "고객", "client", null, orgId);
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
                "멤버 " + System.nanoTime(), "멤버조직", "SaaS", null,
                LocalDate.now(), LocalDate.now().plusDays(10)), pm);
        return p;
    }

    private String token(String url) {
        return url.substring(url.indexOf("token=") + 6);
    }

    @Test
    void invite_existingAccount_addsMember() {
        Project p = project();
        Account dev = ensure("mb-dev@test.com", "개발자", "team", "developer", null);
        MemberService.InviteResult r = memberService.invite(p.getId(), "mb-dev@test.com", "team_member", pm);
        assertThat(r.type()).isEqualTo("added");
        assertThat(memberMapper.existsActive(p.getId(), dev.getId())).isTrue();
    }

    @Test
    void invite_newEmail_createsPendingAccount_thenAcceptActivates() {
        Project p = project();
        String email = "invitee-" + System.nanoTime() + "@test.com";
        MemberService.InviteResult r = memberService.invite(p.getId(), email, "team_member", pm);
        assertThat(r.type()).isEqualTo("invited");
        Account pending = accountMapper.findByEmail(email);
        assertThat(pending.getStatus()).isEqualTo("pending");

        memberService.accept(token(r.acceptUrl()), "Passw0rd1");
        Account activated = accountMapper.findByEmail(email);
        assertThat(activated.getStatus()).isEqualTo("active");
        assertThat(memberMapper.existsActive(p.getId(), activated.getId())).isTrue();
    }

    @Test
    void invite_byNonTeam_isForbidden() {
        Project p = project();
        projectService.addMember(p.getId(), client.getId(), pm.getId());
        assertThatThrownBy(() -> memberService.invite(p.getId(), "x@test.com", "team_member", client))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void removeMember_andSelfRemoveBlocked() {
        Project p = project();
        Account dev = ensure("mb-dev2@test.com", "개발자2", "team", "developer", null);
        projectService.addMember(p.getId(), dev.getId(), pm.getId());

        memberService.remove(p.getId(), dev.getId(), pm);
        assertThat(memberMapper.existsActive(p.getId(), dev.getId())).isFalse();

        assertThatThrownBy(() -> memberService.remove(p.getId(), pm.getId(), pm))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void passwordReset_requestThenReset_tokenSingleUse() {
        String url = passwordResetService.requestReset("mb-pm@test.com");
        assertThat(url).contains("token=");
        String tk = token(url);
        passwordResetService.reset(tk, "NewPass99");
        // 동일 토큰 재사용 불가
        assertThatThrownBy(() -> passwordResetService.reset(tk, "Another99"))
                .isInstanceOf(ApiException.class);
    }
}
