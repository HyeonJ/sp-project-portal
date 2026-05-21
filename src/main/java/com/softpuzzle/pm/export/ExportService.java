package com.softpuzzle.pm.export;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.deliverable.DeliverableSlotMapper;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectGateMapper;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.qa.DefectMapper;
import com.softpuzzle.pm.qa.TestCaseMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 산출물·이력 Export (JSON 요약). */
@Service
public class ExportService {

    private final ProjectMapper projectMapper;
    private final DeliverableSlotMapper slotMapper;
    private final ProjectGateMapper gateMapper;
    private final TestCaseMapper tcMapper;
    private final DefectMapper defectMapper;
    private final MembershipGuard guard;

    public ExportService(ProjectMapper projectMapper, DeliverableSlotMapper slotMapper,
                         ProjectGateMapper gateMapper, TestCaseMapper tcMapper,
                         DefectMapper defectMapper, MembershipGuard guard) {
        this.projectMapper = projectMapper;
        this.slotMapper = slotMapper;
        this.gateMapper = gateMapper;
        this.tcMapper = tcMapper;
        this.defectMapper = defectMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(Long projectId, Account actor) {
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, actor);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", OffsetDateTime.now().toString());
        out.put("project", Map.of(
                "id", project.getId(), "name", project.getName(),
                "client", project.getClientOrgName(), "type", String.valueOf(project.getType()),
                "status", project.getStatus(), "currentStage", project.getCurrentStage()));
        out.put("gates", gateMapper.findByProject(projectId).stream()
                .map(g -> Map.of("stage", g.getGateStage(), "status", g.getStatus()))
                .collect(Collectors.toList()));
        out.put("deliverables", slotMapper.findByProject(projectId).stream()
                .map(s -> mapOfNullable("slotType", s.getSlotType(), "status", s.getStatus(),
                        "versionNo", s.getCurrentVersionNo()))
                .collect(Collectors.toList()));
        out.put("testCases", tcMapper.findByProject(projectId).stream()
                .map(t -> Map.of("code", t.getCode(), "title", t.getTitle(), "status", t.getStatus()))
                .collect(Collectors.toList()));
        out.put("defects", defectMapper.findByProject(projectId).stream()
                .map(d -> Map.of("code", d.getCode(), "title", d.getTitle(),
                        "severity", d.getSeverity(), "status", d.getStatus()))
                .collect(Collectors.toList()));
        return out;
    }

    private Map<String, Object> mapOfNullable(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
