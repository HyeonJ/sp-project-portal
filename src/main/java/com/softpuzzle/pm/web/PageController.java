package com.softpuzzle.pm.web;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** SSR 페이지 라우트 (Thymeleaf 렌더). */
@Controller
public class PageController {

    private final CurrentUser currentUser;

    public PageController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        addNavAttributes(model);
        return "dashboard";
    }

    @GetMapping("/search")
    public String search(Model model) {
        addNavAttributes(model);
        return "search";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        Account me = currentUser.require();
        if (!"admin".equals(me.getTier())) {
            return "redirect:/";
        }
        addNavAttributes(model);
        return "admin";
    }

    /** 레이아웃 셸(사이드바·상단바)이 쓰는 현재 사용자 속성. */
    private void addNavAttributes(Model model) {
        Account me = currentUser.require();
        model.addAttribute("navUserName", me.getName());
        model.addAttribute("navUserInitial", me.getName().isBlank() ? "?" : me.getName().substring(0, 1));
        model.addAttribute("navTier", me.getTier());
        model.addAttribute("navCanCreate", "team".equals(me.getTier()) || "admin".equals(me.getTier()));
    }
}
