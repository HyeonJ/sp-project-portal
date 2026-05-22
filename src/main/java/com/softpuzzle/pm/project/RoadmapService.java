package com.softpuzzle.pm.project;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진행 현황(로드맵, SCR-RDM-001) 뷰모델.
 * 20단계를 6페이즈로 묶은 구조(flow-diagram.md)는 고정, 상태는 현재 단계·게이트로 계산.
 */
@Service
public class RoadmapService {

    private final ProjectMapper projectMapper;
    private final ProjectGateMapper gateMapper;
    private final MembershipGuard guard;

    public RoadmapService(ProjectMapper projectMapper, ProjectGateMapper gateMapper, MembershipGuard guard) {
        this.projectMapper = projectMapper;
        this.gateMapper = gateMapper;
        this.guard = guard;
    }

    /** 단계 정의: lo, hi, 이름, 상세(nullable), gate, major, 산출물 슬러그(nullable). */
    private record StageDef(int lo, int hi, String name, String detail, boolean gate, boolean major, String deliv) {
    }

    private record PhaseDef(String label, boolean muted, String note, List<StageDef> stages) {
    }

    private static final List<PhaseDef> PHASES = List.of(
            new PhaseDef("프로젝트 착수", false, null, List.of(
                    new StageDef(1, 1, "프로젝트 생성·워크스페이스 초기화", null, false, false, null))),
            new PhaseDef("요구사항 협의", false, null, List.of(
                    new StageDef(2, 2, "요구사항 등록 (팀 대행 입력)", null, false, false, "requirements"),
                    new StageDef(3, 4, "검토·수정 왕복 ↻", null, false, false, null),
                    new StageDef(5, 5, "최종 컨펌", null, true, true, null))),
            new PhaseDef("설계 협의", false, null, List.of(
                    new StageDef(6, 6, "IA 등록", null, false, false, "ia"),
                    new StageDef(7, 7, "IA 컨펌", null, true, false, null),
                    new StageDef(8, 8, "디자인 시안 등록", null, false, false, "design"),
                    new StageDef(9, 9, "시안 컨펌", null, true, false, null),
                    new StageDef(10, 10, "프로토타입(HTML) 등록", null, false, false, "prototype"),
                    new StageDef(11, 11, "프로토타입 컨펌", null, true, false, null),
                    new StageDef(12, 12, "Figma 핸드오프 등록", null, false, false, "figma"),
                    new StageDef(13, 13, "Figma 컨펌", null, true, false, null))),
            new PhaseDef("개발", false, null, List.of(
                    new StageDef(14, 14, "개발", "Gates 5·7·9·11·13 모두 통과 시 트리거", false, false, null))),
            new PhaseDef("테스트", false, null, List.of(
                    new StageDef(15, 15, "TC 목록 관리 + 결함 추적", null, false, false, null),
                    new StageDef(16, 17, "UAT · 수정 반복 ↻", null, false, false, null),
                    new StageDef(18, 18, "최종 검수 컨펌", "산출물 확정", true, true, null))),
            new PhaseDef("이행·운영", false, null, List.of(
                    new StageDef(19, 19, "산출물 Export", null, false, false, null),
                    new StageDef(20, 20, "서비스 오픈·운영 전환", null, false, false, null))));

    private record MilestoneDef(String code, int gate, String label, boolean major) {
    }

    private static final List<MilestoneDef> MILESTONES = List.of(
            new MilestoneDef("M1", 5, "요구사항 확정", false),
            new MilestoneDef("M2", 7, "IA 확정", false),
            new MilestoneDef("M3", 9, "시안 확정", false),
            new MilestoneDef("M4", 11, "프로토타입 확정", false),
            new MilestoneDef("M5", 13, "Figma 확정", false),
            new MilestoneDef("M6", 14, "개발 완료", false),
            new MilestoneDef("M7", 18, "UAT 통과", true),
            new MilestoneDef("M8", 20, "서비스 오픈", false));

    private static final int[] MAJOR_GATES = {5, 18};

    @Transactional(readOnly = true)
    public Map<String, Object> roadmap(Long projectId, Account actor) {
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
        guard.assertCanView(projectId, actor);

        int cs = project.getCurrentStage() == null ? 1 : project.getCurrentStage();
        Set<Integer> passedGates = gateMapper.findByProject(projectId).stream()
                .filter(g -> "pass".equals(g.getStatus()))
                .map(g -> g.getGateStage().intValue())
                .collect(Collectors.toSet());

        List<Map<String, Object>> phases = new ArrayList<>();
        int totalStages = 0;
        int doneStages = 0;
        int totalGates = 0;
        int passedGateCount = 0;
        int phaseIndex = 0;

        for (PhaseDef ph : PHASES) {
            phaseIndex++;
            List<Map<String, Object>> stages = new ArrayList<>();
            for (StageDef s : ph.stages()) {
                totalStages++;
                String status = stageStatus(s, ph, cs, passedGates);
                if ("done".equals(status) || "pass".equals(status) || "skip".equals(status)) {
                    doneStages++;
                }
                if (s.gate()) {
                    totalGates++;
                    if ("pass".equals(status)) {
                        passedGateCount++;
                    }
                }
                Map<String, Object> stage = new LinkedHashMap<>();
                stage.put("n", s.lo() == s.hi() ? String.valueOf(s.lo()) : s.lo() + "~" + s.hi());
                stage.put("name", s.name());
                stage.put("detail", s.detail());
                stage.put("gate", s.gate());
                stage.put("major", s.major());
                stage.put("status", status);
                stage.put("badge", badge(status));
                stage.put("deliv", s.deliv());
                stages.add(stage);
            }
            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("index", phaseIndex);
            phase.put("label", ph.label());
            phase.put("muted", ph.muted());
            phase.put("note", ph.note());
            phase.put("count", ph.stages().size());
            phase.put("stages", stages);
            phases.add(phase);
        }

        List<Map<String, Object>> milestones = new ArrayList<>();
        boolean currentAssigned = false;
        for (MilestoneDef m : MILESTONES) {
            String status;
            if (cs >= m.gate()) {
                status = "done";
            } else if (!currentAssigned) {
                status = "current";
                currentAssigned = true;
            } else {
                status = "lock";
            }
            Map<String, Object> milestone = new LinkedHashMap<>();
            milestone.put("code", m.code());
            milestone.put("gate", m.gate());
            milestone.put("label", m.label());
            milestone.put("major", m.major());
            milestone.put("status", status);
            milestone.put("when", "done".equals(status) ? "통과" : "current".equals(status) ? "진행 중" : "예정");
            milestones.add(milestone);
        }

        MilestoneDef nextMajor = MILESTONES.stream()
                .filter(m -> m.major() && cs < m.gate())
                .findFirst().orElse(null);
        int majorPassed = 0;
        for (int g : MAJOR_GATES) {
            if (passedGates.contains(g)) {
                majorPassed++;
            }
        }

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("stage", cs);
        kpi.put("total", Stages.TOTAL);
        kpi.put("percent", Math.round(cs * 100.0 / Stages.TOTAL));
        kpi.put("currentStageName", Stages.name(cs));
        kpi.put("passedGates", passedGateCount);
        kpi.put("totalGates", totalGates);
        kpi.put("majorPassed", majorPassed);
        kpi.put("majorTotal", MAJOR_GATES.length);
        kpi.put("nextMajorCode", nextMajor == null ? "—" : nextMajor.code());
        kpi.put("nextMajorLabel", nextMajor == null ? "" : nextMajor.label());
        kpi.put("nextMajorWhen", nextMajor == null ? "전체 완료" : "Gate " + nextMajor.gate());
        kpi.put("gateBadge", gateBadge(cs));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kpi", kpi);
        result.put("milestones", milestones);
        result.put("phases", phases);
        return result;
    }

    private String stageStatus(StageDef s, PhaseDef ph, int cs, Set<Integer> passedGates) {
        if (ph.muted()) {
            return "skip";
        }
        if (s.hi() < cs) {
            if (s.gate()) {
                return passedGates.contains(s.lo()) ? "pass" : "done";
            }
            return "done";
        }
        if (s.lo() <= cs && cs <= s.hi()) {
            if (s.gate()) {
                return passedGates.contains(s.lo()) ? "pass" : "pending";
            }
            return "current";
        }
        return "lock";
    }

    private String gateBadge(int stage) {
        for (int gate : MILESTONE_GATE_CONFIRM) {
            if (stage < gate) {
                return "Gate " + gate + " 예정";
            }
        }
        return "전체 게이트 통과";
    }

    private static final int[] MILESTONE_GATE_CONFIRM = {5, 7, 9, 11, 13, 18};

    private String badge(String status) {
        return switch (status) {
            case "done" -> "완료";
            case "pass" -> "통과";
            case "current" -> "진행 중";
            case "pending" -> "검토 중";
            case "skip" -> "스킵";
            default -> "잠금";
        };
    }
}
