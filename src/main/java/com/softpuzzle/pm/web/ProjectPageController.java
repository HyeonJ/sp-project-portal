package com.softpuzzle.pm.web;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 프로젝트 범위 SSR 페이지 (산출물 등). */
@Controller
public class ProjectPageController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectPageController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects/{id}")
    public String deliverables(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);   // 멤버십/조회 권한 검증
        addProjectModel(model, me, project);
        model.addAttribute("canEdit", isMemberCanEdit(me));
        return "project/deliverables";
    }

    @GetMapping("/projects/{id}/dev")
    public String dev(@PathVariable Long id, Model model) {
        Account me = currentUser.require();
        Project project = projectService.view(id, me);
        addProjectModel(model, me, project);
        return "project/dev";
    }

    private void addProjectModel(Model model, Account me, Project project) {
        addNav(model, me);
        model.addAttribute("projId", project.getId());
        model.addAttribute("projName", project.getName());
        model.addAttribute("projClient", project.getClientOrgName());
        model.addAttribute("projType", project.getType());
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
