package com.softpuzzle.pm.dashboard;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.deliverable.ActivityEvent;
import com.softpuzzle.pm.deliverable.ActivityEventMapper;
import com.softpuzzle.pm.deliverable.DeliverableSlot;
import com.softpuzzle.pm.deliverable.DeliverableSlotMapper;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectGate;
import com.softpuzzle.pm.project.ProjectGateMapper;
import com.softpuzzle.pm.project.ProjectMapper;
import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.DefectMapper;
import com.softpuzzle.pm.qa.TestCase;
import com.softpuzzle.pm.qa.TestCaseMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 대시보드 집계 (SCR-PRJ-001). */
@Service
public class DashboardService {

    private final ProjectMapper projectMapper;
    private final ProjectGateMapper gateMapper;
    private final DeliverableSlotMapper slotMapper;
    private final DefectMapper defectMapper;
    private final TestCaseMapper tcMapper;
    private final ActivityEventMapper activityMapper;
    private final MembershipGuard guard;

    public DashboardService(ProjectMapper projectMapper, ProjectGateMapper gateMapper,
                            DeliverableSlotMapper slotMapper, DefectMapper defectMapper,
                            TestCaseMapper tcMapper, ActivityEventMapper activityMapper,
                            MembershipGuard guard) {
        this.projectMapper = projectMapper;
        this.gateMapper = gateMapper;
        this.slotMapper = slotMapper;
        this.defectMapper = defectMapper;
        this.tcMapper = tcMapper;
        this.activityMapper = activityMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Long projectId, Account actor) {
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, actor);

        List<ProjectGate> gates = gateMapper.findByProject(projectId);
        List<DeliverableSlot> slots = slotMapper.findByProject(projectId);
        List<Defect> defects = defectMapper.findByProject(projectId);
        List<TestCase> tcs = tcMapper.findByProject(projectId);
        List<ActivityEvent> recent = activityMapper.findByProject(projectId, 5);

        long gatesPassed = gates.stream().filter(g -> "pass".equals(g.getStatus())).count();
        long pendingReviews = slots.stream().filter(s -> "pending-review".equals(s.getStatus())).count();
        long confirmed = slots.stream().filter(s -> "confirmed".equals(s.getStatus())).count();
        long openDefects = defects.stream()
                .filter(d -> "open".equals(d.getStatus()) || "in_progress".equals(d.getStatus())).count();
        long passedTc = tcs.stream().filter(t -> "passed".equals(t.getStatus())).count();

        return Map.of(
                "project", Map.of(
                        "id", project.getId(), "name", project.getName(),
                        "client", project.getClientOrgName(), "status", project.getStatus(),
                        "currentStage", project.getCurrentStage()),
                "progress", Map.of(
                        "gatesPassed", gatesPassed, "gatesTotal", gates.size(),
                        "stagePercent", Math.round(project.getCurrentStage() * 100.0 / 20)),
                "counts", Map.of(
                        "pendingReviews", pendingReviews, "confirmedSlots", confirmed,
                        "openDefects", openDefects, "totalTc", tcs.size(), "passedTc", passedTc),
                "gates", gates.stream().map(g -> Map.of("stage", g.getGateStage(), "status", g.getStatus())).toList(),
                "deliverables", slots.stream().map(s -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("slotType", s.getSlotType());
                    m.put("status", s.getStatus());
                    m.put("versionNo", s.getCurrentVersionNo());
                    return m;
                }).toList(),
                "recentActivity", recent.stream().map(e -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("eventType", e.getEventType());
                    m.put("slotType", e.getSlotType());
                    m.put("actorName", e.getActorName());
                    m.put("body", e.getBody());
                    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
                    return m;
                }).toList());
    }
}
