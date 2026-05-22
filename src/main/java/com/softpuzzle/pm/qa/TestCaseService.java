package com.softpuzzle.pm.qa;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.qa.dto.CreateTestCaseRequest;
import com.softpuzzle.pm.qa.dto.TestCaseDetail;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaseService {

    private static final Logger log = LoggerFactory.getLogger(TestCaseService.class);

    private final TestCaseMapper tcMapper;
    private final DefectMapper defectMapper;
    private final CodeSequenceMapper codeSequenceMapper;
    private final ProjectMapper projectMapper;
    private final MembershipGuard guard;

    public TestCaseService(TestCaseMapper tcMapper, DefectMapper defectMapper,
                           CodeSequenceMapper codeSequenceMapper, ProjectMapper projectMapper,
                           MembershipGuard guard) {
        this.tcMapper = tcMapper;
        this.defectMapper = defectMapper;
        this.codeSequenceMapper = codeSequenceMapper;
        this.projectMapper = projectMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<TestCase> list(Long projectId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        return tcMapper.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public TestCaseDetail detail(Long projectId, Long tcId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        TestCase tc = requireTc(projectId, tcId);
        return new TestCaseDetail(tc, defectMapper.findLinkedDefects(tcId));
    }

    @Transactional
    public TestCase create(Long projectId, CreateTestCaseRequest req, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        int no = codeSequenceMapper.allocate(projectId, "test_case", 1);
        TestCase tc = toTestCase(projectId, code(no), req.title(), req.phase(),
                req.priority(), req.precondition(), req.steps(), req.expectedResult());
        tc.setAssigneeId(validateAssignee(projectId, req.assigneeId()));
        tcMapper.insert(tc);
        return tc;
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

    /** TC 필드 수정 — 팀 전용. 상태·실제결과·코드는 유지. */
    @Transactional
    public void update(Long projectId, Long tcId, CreateTestCaseRequest req, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        TestCase tc = requireTc(projectId, tcId);
        tc.setTitle(req.title().trim());
        tc.setPhase(req.phase() == null || req.phase().isBlank() ? "기타" : req.phase().trim());
        tc.setPriority(normalizePriority(req.priority()));
        tc.setPrecondition(blankToNull(req.precondition()));
        tc.setSteps(blankToNull(req.steps()));
        tc.setExpectedResult(blankToNull(req.expectedResult()));
        tc.setAssigneeId(validateAssignee(projectId, req.assigneeId()));
        tcMapper.updateFields(tc);
    }

    /** 담당자 일괄 지정 — 팀 전용. assigneeId null이면 미지정 해제. */
    @Transactional
    public int bulkAssign(Long projectId, List<Long> tcIds, Long assigneeId, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        Long validated = validateAssignee(projectId, assigneeId);
        int n = 0;
        for (Long id : tcIds) {
            requireTc(projectId, id);
            tcMapper.updateAssignee(id, validated);
            n++;
        }
        return n;
    }

    /** CSV 일괄 가져오기 — 헤더(제목/단계/우선순위...) 한 줄, 이후 행. 최소: 제목,단계,우선순위. */
    @Transactional
    public int importCsv(Long projectId, String csv, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        List<String[]> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            throw ApiException.conflict("EMPTY_CSV", "가져올 행이 없습니다.");
        }
        int start = codeSequenceMapper.allocate(projectId, "test_case", rows.size());
        int n = 0;
        for (String[] r : rows) {
            String priority = normalizePriority(col(r, 2));
            TestCase tc = toTestCase(projectId, code(start + n), col(r, 0), col(r, 1),
                    priority, col(r, 3), col(r, 4), col(r, 5));
            tcMapper.insert(tc);
            n++;
        }
        log.info("[importCsv] project={} imported={}", projectId, n);
        return n;
    }

    @Transactional
    public void updateStatus(Long projectId, Long tcId, String status, String actualResult, Account actor) {
        requireProject(projectId);
        guard.assertTeamMember(projectId, actor);
        if (!List.of("passed", "failed", "pending").contains(status)) {
            throw ApiException.conflict("BAD_STATUS", "상태 값이 올바르지 않습니다.");
        }
        requireTc(projectId, tcId);
        tcMapper.updateStatus(tcId, status, actualResult);
    }

    // --- 내부 ---

    private TestCase toTestCase(Long projectId, String code, String title, String phase,
                                String priority, String precondition, String steps, String expected) {
        if (title == null || title.isBlank()) {
            throw ApiException.conflict("TITLE_REQUIRED", "제목은 필수입니다.");
        }
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setCode(code);
        tc.setTitle(title.trim());
        tc.setPhase(phase == null || phase.isBlank() ? "기타" : phase.trim());
        tc.setPriority(normalizePriority(priority));
        tc.setStatus("pending");
        tc.setPrecondition(blankToNull(precondition));
        tc.setSteps(blankToNull(steps));
        tc.setExpectedResult(blankToNull(expected));
        return tc;
    }

    private String code(int no) {
        return String.format("TC-%03d", no);
    }

    private String normalizePriority(String p) {
        if (p == null) {
            return "Medium";
        }
        String t = p.trim();
        if (t.equalsIgnoreCase("High") || t.equals("높음")) {
            return "High";
        }
        if (t.equalsIgnoreCase("Low") || t.equals("낮음")) {
            return "Low";
        }
        return "Medium";
    }

    /** RFC4180 CSV 파서 — 따옴표 필드(쉼표·개행·"" 이스케이프) 처리, BOM·빈 행·헤더 행 무시. */
    private List<String[]> parseCsv(String csv) {
        List<String[]> all = new ArrayList<>();
        if (csv == null) {
            return all;
        }
        String text = csv.replace("\r\n", "\n").replace("\r", "\n");
        if (text.startsWith("﻿")) {
            text = text.substring(1); // 엑셀 UTF-8 BOM 제거
        }
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++; // 이스케이프된 따옴표("")
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n') {
                fields.add(cur.toString());
                cur.setLength(0);
                all.add(fields.toArray(new String[0]));
                fields = new ArrayList<>();
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || !fields.isEmpty()) {
            fields.add(cur.toString());
            all.add(fields.toArray(new String[0]));
        }

        List<String[]> rows = new ArrayList<>();
        for (String[] r : all) {
            if (!isBlankRow(r)) {
                rows.add(r);
            }
        }
        if (!rows.isEmpty()) {
            String c0 = rows.get(0).length > 0 && rows.get(0)[0] != null ? rows.get(0)[0].trim() : "";
            if (c0.equalsIgnoreCase("title") || c0.equals("제목")) {
                rows.remove(0); // 헤더 행 스킵
            }
        }
        return rows;
    }

    private boolean isBlankRow(String[] r) {
        for (String f : r) {
            if (f != null && !f.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String col(String[] r, int i) {
        return i < r.length ? r[i].trim() : null;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void requireProject(Long projectId) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
    }

    private TestCase requireTc(Long projectId, Long tcId) {
        TestCase tc = tcMapper.findById(tcId);
        if (tc == null || !tc.getProjectId().equals(projectId)) {
            throw ApiException.notFound("테스트 케이스를 찾을 수 없습니다.");
        }
        return tc;
    }
}
