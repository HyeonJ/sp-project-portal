package com.softpuzzle.pm.qa;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.storage.FileStorage;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.qa.dto.CreateDefectRequest;
import com.softpuzzle.pm.qa.dto.DefectDetail;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DefectService {

    private static final Logger log = LoggerFactory.getLogger(DefectService.class);
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final List<String> STATUSES = List.of("open", "in_progress", "resolved", "cannot_reproduce");

    private final DefectMapper defectMapper;
    private final TestCaseMapper tcMapper;
    private final DefectAttachmentMapper attachmentMapper;
    private final CodeSequenceMapper codeSequenceMapper;
    private final ProjectMapper projectMapper;
    private final FileStorage storage;
    private final MembershipGuard guard;
    private final TransactionTemplate tx;

    public DefectService(DefectMapper defectMapper, TestCaseMapper tcMapper,
                         DefectAttachmentMapper attachmentMapper, CodeSequenceMapper codeSequenceMapper,
                         ProjectMapper projectMapper, FileStorage storage, MembershipGuard guard,
                         PlatformTransactionManager txManager) {
        this.defectMapper = defectMapper;
        this.tcMapper = tcMapper;
        this.attachmentMapper = attachmentMapper;
        this.codeSequenceMapper = codeSequenceMapper;
        this.projectMapper = projectMapper;
        this.storage = storage;
        this.guard = guard;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<Defect> list(Long projectId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        return defectMapper.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public DefectDetail detail(Long projectId, Long defectId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        Defect defect = requireDefect(projectId, defectId);
        return new DefectDetail(defect, defectMapper.findLinkedTestCases(defectId),
                attachmentMapper.findByDefect(defectId));
    }

    @Transactional
    public Defect create(Long projectId, CreateDefectRequest req, Account actor) {
        requireProject(projectId);
        guard.assertMember(projectId, actor); // 팀·고객사 모두 결함 등록 가능
        int no = codeSequenceMapper.allocate(projectId, "defect", 1);
        Defect d = new Defect();
        d.setProjectId(projectId);
        d.setCode(String.format("DEF-%03d", no));
        d.setTitle(req.title().trim());
        d.setSeverity(req.severity() == null ? "Medium" : req.severity());
        d.setStatus("open");
        d.setReproSteps(blankToNull(req.reproSteps()));
        d.setEnvironment(blankToNull(req.environment()));
        d.setReporterId(actor.getId());
        if (guard.isTeam(actor)) {
            d.setAssigneeId(validateAssignee(projectId, req.assigneeId())); // 담당자 배정은 팀만
        }
        defectMapper.insert(d);
        if (req.testCaseId() != null) {
            requireTcInProject(projectId, req.testCaseId());
            defectMapper.insertLink(req.testCaseId(), d.getId());
        }
        return d;
    }

    /** 결함 필드 수정 — 팀 전체 + 고객사는 본인 등록분만. 상태·연결·코드는 유지. */
    @Transactional
    public void update(Long projectId, Long defectId, CreateDefectRequest req, Account actor) {
        requireProject(projectId);
        guard.assertMember(projectId, actor); // 관리자(읽기 전용) 차단
        Defect d = requireDefect(projectId, defectId);
        if ("client".equals(actor.getTier()) && !actor.getId().equals(d.getReporterId())) {
            throw ApiException.forbidden("본인이 등록한 결함만 수정할 수 있습니다.");
        }
        d.setTitle(req.title().trim());
        d.setSeverity(req.severity() == null ? "Medium" : req.severity());
        d.setReproSteps(blankToNull(req.reproSteps()));
        d.setEnvironment(blankToNull(req.environment()));
        if (guard.isTeam(actor)) {
            d.setAssigneeId(validateAssignee(projectId, req.assigneeId())); // 팀만 담당자 변경, 고객사는 기존 유지
        }
        defectMapper.updateFields(d);
    }

    private Long validateAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!guard.isActiveMember(projectId, assigneeId)) {
            throw ApiException.conflict("BAD_ASSIGNEE", "담당자는 프로젝트 멤버여야 합니다.");
        }
        return assigneeId;
    }

    @Transactional
    public void updateStatus(Long projectId, Long defectId, String status, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        if (!STATUSES.contains(status)) {
            throw ApiException.conflict("BAD_STATUS", "상태 값이 올바르지 않습니다.");
        }
        requireDefect(projectId, defectId);
        defectMapper.updateStatus(defectId, status);
    }

    @Transactional
    public void linkTestCase(Long projectId, Long defectId, Long tcId, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        requireDefect(projectId, defectId);
        requireTcInProject(projectId, tcId);
        defectMapper.insertLink(tcId, defectId);
    }

    @Transactional
    public void unlinkTestCase(Long projectId, Long defectId, Long tcId, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        requireDefect(projectId, defectId);
        defectMapper.deleteLink(tcId, defectId);
    }

    public DefectAttachment addAttachment(Long projectId, Long defectId, String name, String contentType,
                                          long size, InputStream content, Account actor) {
        requireProject(projectId);
        guard.assertMember(projectId, actor);
        requireDefect(projectId, defectId);
        if (size > MAX_FILE_BYTES) {
            throw ApiException.conflict("FILE_TOO_LARGE", "첨부는 50MB 이하만 가능합니다.");
        }
        String storageKey = storage.put(name, contentType, size, content);
        try {
            return tx.execute(s -> {
                DefectAttachment a = new DefectAttachment();
                a.setDefectId(defectId);
                a.setOriginalName(name);
                a.setContentType(contentType);
                a.setSizeBytes(size);
                a.setStorageKey(storageKey);
                a.setUploadedBy(actor.getId());
                attachmentMapper.insert(a);
                return a;
            });
        } catch (RuntimeException e) {
            storage.deleteQuietly(storageKey);
            throw e;
        }
    }

    public void deleteAttachment(Long projectId, Long defectId, Long attachmentId, Account actor) {
        requireProject(projectId);
        guard.assertMember(projectId, actor);
        requireDefect(projectId, defectId);
        String storageKey = tx.execute(s -> {
            DefectAttachment a = attachmentMapper.findById(attachmentId);
            if (a == null || !a.getDefectId().equals(defectId)) {
                throw ApiException.notFound("첨부를 찾을 수 없습니다.");
            }
            attachmentMapper.delete(attachmentId);
            return a.getStorageKey();
        });
        if (storageKey != null) {
            storage.deleteQuietly(storageKey);
        }
    }

    @Transactional(readOnly = true)
    public DefectAttachment attachmentForDownload(Long projectId, Long defectId, Long attachmentId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        requireDefect(projectId, defectId);
        DefectAttachment a = attachmentMapper.findById(attachmentId);
        if (a == null || !a.getDefectId().equals(defectId)) {
            throw ApiException.notFound("첨부를 찾을 수 없습니다.");
        }
        return a;
    }

    public InputStream openAttachment(String storageKey) {
        return storage.openStream(storageKey);
    }

    // --- 내부 ---

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void requireProject(Long projectId) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
    }

    private Defect requireDefect(Long projectId, Long defectId) {
        Defect d = defectMapper.findById(defectId);
        if (d == null || !d.getProjectId().equals(projectId)) {
            throw ApiException.notFound("결함을 찾을 수 없습니다.");
        }
        return d;
    }

    private void requireTcInProject(Long projectId, Long tcId) {
        TestCase tc = tcMapper.findById(tcId);
        if (tc == null || !tc.getProjectId().equals(projectId)) {
            throw ApiException.notFound("연결할 테스트 케이스를 찾을 수 없습니다.");
        }
    }
}
