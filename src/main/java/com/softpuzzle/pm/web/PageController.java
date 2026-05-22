package com.softpuzzle.pm.web;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.project.Project;
import com.softpuzzle.pm.project.ProjectService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** SSR 페이지 라우트 (Thymeleaf 렌더). */
@Controller
public class PageController {

    private final CurrentUser currentUser;
    private final ProjectService projectService;

    public PageController(CurrentUser currentUser, ProjectService projectService) {
        this.currentUser = currentUser;
        this.projectService = projectService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/invite/accept")
    public String inviteAccept() {
        return "auth/invite-accept";
    }

    @GetMapping("/password/reset")
    public String passwordResetRequest() {
        return "auth/password-reset-request";
    }

    @GetMapping("/password/reset/confirm")
    public String passwordResetConfirm() {
        return "auth/password-reset-confirm";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        addNavAttributes(model);
        return "dashboard";
    }

    @GetMapping("/projects/new")
    public String projectNew(Model model) {
        Account me = currentUser.require();
        if (!"team".equals(me.getTier()) && !"admin".equals(me.getTier())) {
            return "redirect:/";
        }
        addNavAttributes(model);
        return "project-new";
    }

    @GetMapping("/account")
    public String account(Model model) {
        Account me = currentUser.require();
        addNavAttributes(model);
        model.addAttribute("acctName", me.getName());
        model.addAttribute("acctEmail", me.getEmail());
        model.addAttribute("acctTier", me.getTier());
        model.addAttribute("acctJob", me.getJob());
        model.addAttribute("myProjects", projectService.myProjects(me));
        return "account";
    }

    @GetMapping("/search")
    public String search(Model model) {
        addNavAttributes(model);
        return "search";
    }

    @GetMapping("/admin")
    public String admin() {
        return "redirect:/admin/teams";
    }

    @GetMapping("/admin/teams")
    public String adminTeams(Model model) {
        return adminPage(model, "teams", "admin/teams");
    }

    @GetMapping("/admin/clients")
    public String adminClients(Model model) {
        return adminPage(model, "clients", "admin/clients");
    }

    @GetMapping("/admin/audit")
    public String adminAudit(Model model) {
        return adminPage(model, "audit", "admin/audit");
    }

    private String adminPage(Model model, String active, String view) {
        Account me = currentUser.require();
        if (!"admin".equals(me.getTier())) {
            return "redirect:/";
        }
        addNavAttributes(model);
        model.addAttribute("adminActive", active);
        return view;
    }

    /** 레이아웃 셸(사이드바·상단바)이 쓰는 현재 사용자 속성. */
    private void addNavAttributes(Model model) {
        Account me = currentUser.require();
        model.addAttribute("navUserName", me.getName());
        model.addAttribute("navUserInitial", me.getName().isBlank() ? "?" : me.getName().substring(0, 1));
        model.addAttribute("navTier", me.getTier());
        model.addAttribute("navCanCreate", "team".equals(me.getTier()) || "admin".equals(me.getTier()));

        List<Project> mine = projectService.myProjects(me);
        model.addAttribute("navProjectCount", mine.size());
        model.addAttribute("navScopeLabel", switch (me.getTier()) {
            case "admin" -> "전체";
            case "client" -> "참여";
            default -> "담당";
        });
    }
}
