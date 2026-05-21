package com.softpuzzle.pm.admin;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.admin.dto.AdminRequests;
import com.softpuzzle.pm.audit.AuditLog;
import com.softpuzzle.pm.audit.AuditService;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectMapper;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 콘솔: 계정 관리·전체 프로젝트·감사 로그. admin tier 전용. */
@Service
public class AdminService {

    private static final String TEMP_PASSWORD = "Welcome1!";

    private final AccountMapper accountMapper;
    private final ClientOrgMapper clientOrgMapper;
    private final ProjectMapper projectMapper;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AccountMapper accountMapper, ClientOrgMapper clientOrgMapper,
                        ProjectMapper projectMapper, AuditService auditService,
                        PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.clientOrgMapper = clientOrgMapper;
        this.projectMapper = projectMapper;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts(String tier, Account actor) {
        assertAdmin(actor);
        List<Account> accounts = accountMapper.findByTier(tier);
        accounts.forEach(a -> a.setPasswordHash(null));
        return accounts;
    }

    @Transactional
    public Account createAccount(AdminRequests.CreateAccount req, Account actor) {
        assertAdmin(actor);
        if (accountMapper.findByEmail(req.email()) != null) {
            throw ApiException.conflict("EMAIL_EXISTS", "이미 사용 중인 이메일입니다.");
        }
        String tier = req.tier();
        Account a = new Account();
        a.setEmail(req.email().trim());
        a.setName(req.name().trim());
        a.setTier(tier);
        if ("team".equals(tier)) {
            if (req.job() == null || req.job().isBlank()) {
                throw ApiException.conflict("JOB_REQUIRED", "프로젝트팀 계정은 직무가 필요합니다.");
            }
            a.setJob(req.job());
        } else if ("client".equals(tier)) {
            if (req.clientOrgName() == null || req.clientOrgName().isBlank()) {
                throw ApiException.conflict("ORG_REQUIRED", "고객사 계정은 회사명이 필요합니다.");
            }
            a.setClientOrgId(findOrCreateOrg(req.clientOrgName().trim()));
        }
        a.setPasswordHash(passwordEncoder.encode(TEMP_PASSWORD));
        a.setStatus("active");
        accountMapper.insert(a);
        auditService.log(actor, "CREATE_ACCOUNT", "email=" + a.getEmail() + " tier=" + tier);
        a.setPasswordHash(null);
        return a;
    }

    @Transactional
    public void setStatus(Long accountId, String status, Account actor) {
        assertAdmin(actor);
        Account target = accountMapper.findById(accountId);
        if (target == null) {
            throw ApiException.notFound("계정을 찾을 수 없습니다.");
        }
        accountMapper.updateStatus(accountId, status);
        auditService.log(actor, "SET_ACCOUNT_STATUS", "account=" + target.getEmail() + " → " + status);
    }

    @Transactional(readOnly = true)
    public List<Project> allProjects(Account actor) {
        assertAdmin(actor);
        return projectMapper.findAll();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> auditLog(Account actor) {
        assertAdmin(actor);
        return auditService.recent(100);
    }

    private Long findOrCreateOrg(String name) {
        ClientOrg existing = clientOrgMapper.findByName(name);
        if (existing != null) {
            return existing.getId();
        }
        ClientOrg org = new ClientOrg();
        org.setName(name);
        clientOrgMapper.insert(org);
        return org.getId();
    }

    private void assertAdmin(Account actor) {
        if (!"admin".equals(actor.getTier())) {
            throw ApiException.forbidden("관리자만 접근할 수 있습니다.");
        }
    }
}
