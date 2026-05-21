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
        tcMapper.insert(tc);
        return tc;
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

    private List<String[]> parseCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        if (csv == null) {
            return rows;
        }
        String[] lines = csv.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        boolean first = true;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            String c0 = cells[0].trim();
            if (first && (c0.equalsIgnoreCase("title") || c0.equals("제목"))) {
                first = false;
                continue; // 헤더 스킵
            }
            first = false;
            rows.add(cells);
        }
        return rows;
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
