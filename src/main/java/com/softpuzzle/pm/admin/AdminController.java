package com.softpuzzle.pm.admin;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.admin.dto.AdminRequests;
import com.softpuzzle.pm.audit.AuditLog;
import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.project.Project;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final CurrentUser currentUser;

    public AdminController(AdminService adminService, CurrentUser currentUser) {
        this.adminService = adminService;
        this.currentUser = currentUser;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<Account>> accounts(@RequestParam(name = "tier", required = false) String tier) {
        return ApiResponse.ok(adminService.listAccounts(tier, currentUser.require()));
    }

    @PostMapping("/accounts")
    public ApiResponse<Account> createAccount(@Valid @RequestBody AdminRequests.CreateAccount req) {
        return ApiResponse.ok(adminService.createAccount(req, currentUser.require()));
    }

    @PatchMapping("/accounts/{id}/status")
    public ApiResponse<Void> setStatus(@PathVariable Long id, @Valid @RequestBody AdminRequests.StatusChange req) {
        adminService.setStatus(id, req.status(), currentUser.require());
        return ApiResponse.ok(null);
    }

    @GetMapping("/projects")
    public ApiResponse<List<Project>> projects() {
        return ApiResponse.ok(adminService.allProjects(currentUser.require()));
    }

    @GetMapping("/audit")
    public ApiResponse<List<AuditLog>> audit() {
        return ApiResponse.ok(adminService.auditLog(currentUser.require()));
    }
}
