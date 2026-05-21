package com.softpuzzle.pm.project;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.ClientOrg;
import com.softpuzzle.pm.account.ClientOrgMapper;
import com.softpuzzle.pm.audit.AuditService;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.deliverable.DeliverableSlot;
import com.softpuzzle.pm.deliverable.DeliverableSlotMapper;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final short[] GATE_STAGES = {9, 11, 13, 15, 22};
    private static final String[] SLOT_TYPES = {"requirements", "ia", "design", "prototype", "figma"};

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectGateMapper gateMapper;
    private final ClientOrgMapper clientOrgMapper;
    private final DeliverableSlotMapper slotMapper;
    private final AuditService auditService;
    private final MembershipGuard guard;

    public ProjectService(ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                          ProjectGateMapper gateMapper, ClientOrgMapper clientOrgMapper,
                          DeliverableSlotMapper slotMapper, AuditService auditService, MembershipGuard guard) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.gateMapper = gateMapper;
        this.clientOrgMapper = clientOrgMapper;
        this.slotMapper = slotMapper;
        this.auditService = auditService;
        this.guard = guard;
    }

    @Transactional
    public Project create(CreateProjectRequest req, Account creator) {
        guard.assertCanCreateProject(creator);
        log.info("[create] name={} by={}", req.name(), creator.getEmail());

        Long clientOrgId = findOrCreateClientOrg(req.clientOrgName());

        Project project = new Project();
        project.setName(req.name());
        project.setClientOrgId(clientOrgId);
        project.setType(req.type());
        project.setDescription(req.description());
        project.setStartDate(req.startDate());
        project.setEndDate(req.endDate());
        project.setCurrentStage((short) 1);
        project.setStatus("active");
        projectMapper.insert(project);

        seedGates(project.getId());
        seedSlots(project.getId());
        addMember(project.getId(), creator.getId(), creator.getId());
        auditService.log(creator, "CREATE_PROJECT", "project=" + project.getName());

        return projectMapper.findById(project.getId());
    }

    private void seedSlots(Long projectId) {
        for (String slotType : SLOT_TYPES) {
            DeliverableSlot slot = new DeliverableSlot();
            slot.setProjectId(projectId);
            slot.setSlotType(slotType);
            slot.setStatus("empty");
            slotMapper.insert(slot);
        }
    }

    private Long findOrCreateClientOrg(String name) {
        ClientOrg existing = clientOrgMapper.findByName(name);
        if (existing != null) {
            return existing.getId();
        }
        ClientOrg org = new ClientOrg();
        org.setName(name);
        clientOrgMapper.insert(org);
        return org.getId();
    }

    private void seedGates(Long projectId) {
        for (short stage : GATE_STAGES) {
            ProjectGate gate = new ProjectGate();
            gate.setProjectId(projectId);
            gate.setGateStage(stage);
            gate.setStatus(stage == 9 ? "wait" : "lock");
            gateMapper.insert(gate);
        }
    }

    /** 멤버 추가 (없으면 insert, 제외됐던 1쌍이면 reactivate — 멱등). */
    @Transactional
    public void addMember(Long projectId, Long accountId, Long invitedBy) {
        if (memberMapper.existsActive(projectId, accountId)) {
            return;
        }
        ProjectMember existing = memberMapper.findAny(projectId, accountId);
        if (existing != null) {
            memberMapper.reactivate(projectId, accountId, invitedBy);
            return;
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setAccountId(accountId);
        member.setInvitedBy(invitedBy);
        memberMapper.insert(member);
    }

    @Transactional(readOnly = true)
    public List<Project> myProjects(Account account) {
        return guard.isAdmin(account)
                ? projectMapper.findAll()
                : projectMapper.findActiveByMember(account.getId());
    }

    @Transactional(readOnly = true)
    public Project view(Long projectId, Account account) {
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, account);
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> members(Long projectId, Account account) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, account);
        return memberMapper.findActiveMembers(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectGate> gates(Long projectId, Account account) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, account);
        return gateMapper.findByProject(projectId);
    }
}
