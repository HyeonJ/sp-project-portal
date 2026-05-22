package com.softpuzzle.pm.web;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.dashboard.DashboardService;
import com.softpuzzle.pm.deliverable.Comment;
import com.softpuzzle.pm.deliverable.CommentService;
import com.softpuzzle.pm.deliverable.FileAsset;
import com.softpuzzle.pm.deliverable.SlotService;
import com.softpuzzle.pm.deliverable.SlotVersion;
import com.softpuzzle.pm.deliverable.dto.SlotDetail;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import com.softpuzzle.pm.project.RoadmapService;
import com.softpuzzle.pm.project.Stages;
import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.DefectService;
import com.softpuzzle.pm.qa.TestCase;
import com.softpuzzle.pm.qa.TestCaseService;
import com.softpuzzle.pm.qa.dto.DefectDetail;
import com.softpuzzle.pm.qa.dto.TestCaseDetail;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 프로젝트 범위 SSR 페이지 (대시보드·산출물 등). */
@Controller
public class ProjectPageController {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int[] GATE_STAGES = {5, 7, 9, 11, 13, 18};
    private static final Set<String> DELIVERABLE_SLUGS = Set.of("ia", "design", "prototype", "figma");
    private static final Map<String, String> SLOT_TITLE = Map.of(
            "requirements", "요구사항 정의서", "ia", "IA", "design", "디자인 시안",
            "prototype", "프로토타입", "figma", "Figma");

    private final ProjectService projectService;
    private final DashboardService dashboardService;
    private final RoadmapService roadmapService;
    private final SlotService slotService;
    private final CommentService commentService;
    private final TestCaseService testCaseService;
    private final DefectService defectService;
    private final CurrentUser currentUser;

    public ProjectPageController(ProjectService projectService, DashboardService dashboardService,
                                 RoadmapService roadmapService, SlotService slotService,
                                 CommentService commentService, TestCaseService testCaseService,
                                 DefectService defectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.dashboardService = dashboardService;
        this.roadmapService = roadmapService;
        this.slotService = slotService;
        this.commentService = commentService;
        this.testCaseService = testCaseService;
        this.defectService = defectService;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{id}")
    public String projectDashboard(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);   // 멤버십/조회 권한 검증
        addProjectModel(model, me, project);

        Map<String, Object> dash = dashboardService.dashboard(id, me);
        int stage = project.getCurrentStage() == null ? 1 : project.getCurrentStage();
        model.addAttribute("dash", dash);
        model.addAttribute("dashStageLabel", Stages.label(stage));
        model.addAttribute("dashGateBadge", gateBadge(stage));
        model.addAttribute("summaryDeliverables", buildSummary(dash));
        return "project/dashboard";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSummary(Map<String, Object> dash) {
        Map<String, Map<String, Object>> bySlot = new HashMap<>();
        for (Map<String, Object> slot : (List<Map<String, Object>>) dash.get("deliverables")) {
            bySlot.put((String) slot.get("slotType"), slot);
        }
        String[][] defs = {
                {"ia", "IA", "단계 6~7"},
                {"design", "디자인 시안", "단계 8~9"},
                {"prototype", "프로토타입", "단계 10~11"},
                {"figma", "Figma", "단계 12~13"}
        };
        List<Map<String, Object>> summary = new ArrayList<>();
        for (String[] def : defs) {
            Map<String, Object> slot = bySlot.get(def[0]);
            String status = slot == null ? null : (String) slot.get("status");
            Object ver = slot == null ? null : slot.get("versionNo");
            int verNo = ver instanceof Number n ? n.intValue() : 0;
            String pillClass;
            String pillText;
            if ("confirmed".equals(status)) {
                pillClass = "ok";
                pillText = "✓ v" + verNo + " 컨펌";
            } else if ("pending-review".equals(status)) {
                pillClass = "warn";
                pillText = "v" + verNo + " 검토중";
            } else if (verNo > 0) {
                pillClass = "muted";
                pillText = "v" + verNo + " 작성중";
            } else {
                pillClass = "muted";
                pillText = "대기";
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("slug", def[0]);
            card.put("name", def[1]);
            card.put("stageRange", def[2]);
            card.put("pillClass", pillClass);
            card.put("pillText", pillText);
            summary.add(card);
        }
        return summary;
    }

    @GetMapping("/projects/{id}/requirements")
    public String requirements(@PathVariable Long id,
                               @RequestParam(name = "v", required = false) Integer v, Model model) {
        return slotPage(id, "requirements", false, "requirements", v, model);
    }

    @GetMapping("/projects/{id}/deliverables")
    public String deliverables(@PathVariable Long id) {
        return "redirect:/projects/" + id + "/deliverables/ia";
    }

    @GetMapping("/projects/{id}/deliverables/{slug}")
    public String deliverableSlot(@PathVariable Long id, @PathVariable String slug,
                                  @RequestParam(name = "v", required = false) Integer v, Model model) {
        String slot = DELIVERABLE_SLUGS.contains(slug) ? slug : "ia";
        return slotPage(id, slot, true, "deliverables", v, model);
    }

    private String slotPage(Long id, String slotType, boolean withTabs, String activeNav,
                            Integer v, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);

        SlotDetail detail = slotService.slotDetail(id, slotType, me);
        List<SlotVersion> versions = slotService.versions(id, slotType, me);
        List<Comment> comments = commentService.list(id, slotType, me);

        int currentNo = detail.slot().getCurrentVersionNo() == null ? 0 : detail.slot().getCurrentVersionNo();
        boolean viewingOld = v != null && currentNo > 0 && v != currentNo;
        int viewNo = currentNo;
        List<FileAsset> files = detail.files();
        if (viewingOld) {
            SlotVersion target = versions.stream()
                    .filter(sv -> sv.getVersionNo() != null && sv.getVersionNo().intValue() == v)
                    .findFirst().orElse(null);
            if (target != null) {
                files = slotService.versionFiles(id, slotType, target.getId(), me);
                viewNo = v;
            } else {
                viewingOld = false;
            }
        }

        model.addAttribute("slot", detail.slot());
        model.addAttribute("version", detail.version());
        model.addAttribute("files", files);
        model.addAttribute("versions", versions);
        model.addAttribute("viewVersionNo", viewNo);
        model.addAttribute("currentVersionNo", currentNo);
        model.addAttribute("viewingOld", viewingOld);
        model.addAttribute("activity", slotService.activity(id, slotType, me));
        model.addAttribute("comments", comments);
        model.addAttribute("slotType", slotType);
        model.addAttribute("slotTitle", SLOT_TITLE.getOrDefault(slotType, slotType));
        model.addAttribute("withTabs", withTabs);
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("canEdit", isMemberCanEdit(me));
        model.addAttribute("myId", me.getId());
        return "project/slot";
    }

    @GetMapping("/projects/{id}/dev")
    public String dev(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        return "project/dev";
    }

    @GetMapping("/projects/{id}/test-cases")
    public String testCases(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        List<TestCase> tcs = testCaseService.list(id, me);
        model.addAttribute("testCases", tcs);
        model.addAttribute("tcPending", tcs.stream().filter(t -> "pending".equals(t.getStatus())).count());
        model.addAttribute("tcPassed", tcs.stream().filter(t -> "passed".equals(t.getStatus())).count());
        model.addAttribute("tcFailed", tcs.stream().filter(t -> "failed".equals(t.getStatus())).count());
        return "project/tests";
    }

    @GetMapping("/projects/{id}/test-cases/{tcId}")
    public String testCaseDetail(@PathVariable Long id, @PathVariable Long tcId, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        TestCaseDetail detail = testCaseService.detail(id, tcId, me);
        model.addAttribute("tc", detail.testCase());
        model.addAttribute("linkedDefects", detail.linkedDefects());
        return "project/tc-detail";
    }

    @GetMapping("/projects/{id}/defects")
    public String defects(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        List<Defect> defects = defectService.list(id, me);
        model.addAttribute("defects", defects);
        model.addAttribute("defOpen", defects.stream().filter(d -> "open".equals(d.getStatus())).count());
        model.addAttribute("defProgress", defects.stream().filter(d -> "in_progress".equals(d.getStatus())).count());
        model.addAttribute("defResolved", defects.stream().filter(d -> "resolved".equals(d.getStatus())).count());
        return "project/defects";
    }

    @GetMapping("/projects/{id}/defects/{defectId}")
    public String defectDetail(@PathVariable Long id, @PathVariable Long defectId, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        DefectDetail detail = defectService.detail(id, defectId, me);
        model.addAttribute("defect", detail.defect());
        model.addAttribute("linkedTestCases", detail.linkedTestCases());
        model.addAttribute("attachments", detail.attachments());
        return "project/defect-detail";
    }

    @GetMapping("/projects/{id}/roadmap")
    public String roadmap(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        model.addAttribute("roadmap", roadmapService.roadmap(id, me));
        return "project/roadmap";
    }

    @GetMapping("/projects/{id}/settings")
    public String settings(@PathVariable Long id, Model model) {
        return settingsView(id, model, "info");
    }

    @GetMapping("/projects/{id}/settings/members")
    public String settingsMembers(@PathVariable Long id, Model model) {
        return settingsView(id, model, "members");
    }

    @GetMapping("/projects/{id}/settings/danger")
    public String settingsDanger(@PathVariable Long id, Model model) {
        return settingsView(id, model, "danger");
    }

    private String settingsView(Long id, Model model, String sub) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        model.addAttribute("settingSub", sub);
        model.addAttribute("projDescription", project.getDescription());
        model.addAttribute("projStartDate", project.getStartDate());
        model.addAttribute("projEndDate", project.getEndDate());
        return "project/settings";
    }

    private void addProjectModel(Model model, Account me, Project project) {
        addNav(model, me);
        model.addAttribute("navProjects", projectService.myProjects(me));
        model.addAttribute("projId", project.getId());
        model.addAttribute("projName", project.getName());
        model.addAttribute("projClient", project.getClientOrgName());
        model.addAttribute("projType", project.getType());
        model.addAttribute("projWeek", weeksSinceStart(project.getStartDate()));
        model.addAttribute("projStart", project.getStartDate() == null ? "-" : project.getStartDate().format(YM));
        model.addAttribute("projEnd", project.getEndDate() == null ? "-" : project.getEndDate().format(YM));
    }

    private int weeksSinceStart(LocalDate start) {
        if (start == null) {
            return 1;
        }
        long weeks = ChronoUnit.WEEKS.between(start, LocalDate.now());
        return (int) Math.max(1, weeks + 1);
    }

    private String gateBadge(int stage) {
        // current_stage는 직전 통과 게이트(또는 1) → 다음 미통과 게이트를 가리킴
        for (int gate : GATE_STAGES) {
            if (stage < gate) {
                return "Gate " + gate + " 예정";
            }
        }
        return "전체 게이트 통과";
    }

    private boolean isMemberCanEdit(Account me) {
        // admin은 읽기 전용. team/client 멤버는 편집 가능(업로드).
        return !"admin".equals(me.getTier());
    }

    private void addNav(Model model, Account me) {
        model.addAttribute("navUserId", me.getId());
        model.addAttribute("navUserName", me.getName());
        model.addAttribute("navUserInitial", me.getName().isBlank() ? "?" : me.getName().substring(0, 1));
        model.addAttribute("navTier", me.getTier());
        model.addAttribute("navCanCreate", "team".equals(me.getTier()) || "admin".equals(me.getTier()));
    }
}
