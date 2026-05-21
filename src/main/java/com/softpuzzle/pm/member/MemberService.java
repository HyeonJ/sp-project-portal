package com.softpuzzle.pm.member;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.audit.AuditService;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.Tokens;
import com.softpuzzle.pm.notify.NotificationService;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.project.ProjectMemberMapper;
import com.softpuzzle.pm.project.ProjectService;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버·초대. 스마트 분기(REQ-AUT-002): 기존 계정=참여 추가+알림 / 신규=초대(토큰)+pending 계정.
 */
@Service
public class MemberService {

    public record InviteResult(String type, String acceptUrl) {
    }

    private final AccountMapper accountMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final InvitationMapper invitationMapper;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MembershipGuard guard;
    private final PasswordEncoder passwordEncoder;

    public MemberService(AccountMapper accountMapper, ProjectMapper projectMapper,
                         ProjectMemberMapper memberMapper, InvitationMapper invitationMapper,
                         ProjectService projectService, NotificationService notificationService,
                         AuditService auditService, MembershipGuard guard, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.invitationMapper = invitationMapper;
        this.projectService = projectService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.guard = guard;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public InviteResult invite(Long projectId, String email, String inviteType, Account actor) {
        Project project = requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        if (!"client".equals(inviteType) && !"team_member".equals(inviteType)) {
            throw ApiException.conflict("BAD_TYPE", "초대 유형이 올바르지 않습니다.");
        }
        String normalized = email.trim();
        Account existing = accountMapper.findByEmail(normalized);
        if (existing != null) {
            projectService.addMember(projectId, existing.getId(), actor.getId());
            notificationService.notify(existing.getId(), "project_joined",
                    "프로젝트 '" + project.getName() + "'에 참여하게 되었습니다.", "/projects/" + projectId);
            auditService.log(actor, "ADD_MEMBER", "project=" + projectId + " account=" + normalized);
            return new InviteResult("added", null);
        }

        // 신규 — pending 계정 + 초대 토큰
        Account pending = new Account();
        pending.setEmail(normalized);
        pending.setName(normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')) : normalized);
        pending.setStatus("pending");
        if ("client".equals(inviteType)) {
            pending.setTier("client");
            pending.setClientOrgId(project.getClientOrgId());
        } else {
            pending.setTier("team");
            pending.setJob("developer");
        }
        accountMapper.insert(pending);

        invitationMapper.expirePending(projectId, normalized, inviteType);
        String raw = Tokens.generate();
        Invitation inv = new Invitation();
        inv.setEmail(normalized);
        inv.setProjectId(projectId);
        inv.setInviteType(inviteType);
        inv.setTokenHash(Tokens.hash(raw));
        inv.setInvitedBy(actor.getId());
        inv.setExpiresAt(OffsetDateTime.now().plusDays(7)); // MVP: 7일(이메일 없이 링크 전달)
        invitationMapper.insert(inv);
        auditService.log(actor, "INVITE", "project=" + projectId + " email=" + normalized + " type=" + inviteType);
        return new InviteResult("invited", "/invite/accept?token=" + raw);
    }

    @Transactional
    public void accept(String rawToken, String password) {
        Invitation inv = invitationMapper.findByTokenHash(Tokens.hash(rawToken));
        if (inv == null || !"pending".equals(inv.getStatus())) {
            throw ApiException.conflict("INVALID_TOKEN", "유효하지 않은 초대입니다.");
        }
        if (inv.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.conflict("EXPIRED", "만료된 초대입니다.");
        }
        validatePassword(password);
        Account account = accountMapper.findByEmail(inv.getEmail());
        if (account == null) {
            throw ApiException.notFound("계정을 찾을 수 없습니다.");
        }
        accountMapper.updatePasswordAndActivate(account.getId(), passwordEncoder.encode(password));
        projectService.addMember(inv.getProjectId(), account.getId(), inv.getInvitedBy());
        invitationMapper.markAccepted(inv.getId());
    }

    @Transactional
    public void remove(Long projectId, Long accountId, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        if (accountId.equals(actor.getId())) {
            throw ApiException.conflict("SELF_REMOVE", "본인은 제외할 수 없습니다.");
        }
        memberMapper.softRemove(projectId, accountId);
        auditService.log(actor, "REMOVE_MEMBER", "project=" + projectId + " account=" + accountId);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw ApiException.conflict("WEAK_PASSWORD", "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.");
        }
    }

    private Project requireProject(Long projectId) {
        Project p = projectMapper.findById(projectId);
        if (p == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        return p;
    }
}
