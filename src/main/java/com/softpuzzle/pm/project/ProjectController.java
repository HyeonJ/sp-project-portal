package com.softpuzzle.pm.project;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.project.dto.CreateProjectRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<Project>> myProjects() {
        Account me = currentUser.require();
        return ApiResponse.ok(projectService.myProjects(me));
    }

    @PostMapping
    public ApiResponse<Project> create(@Valid @RequestBody CreateProjectRequest req) {
        Account me = currentUser.require();
        return ApiResponse.ok(projectService.create(req, me));
    }

    @GetMapping("/{id}")
    public ApiResponse<Project> detail(@PathVariable Long id) {
        Account me = currentUser.require();
        return ApiResponse.ok(projectService.view(id, me));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMember>> members(@PathVariable Long id) {
        Account me = currentUser.require();
        return ApiResponse.ok(projectService.members(id, me));
    }

    @GetMapping("/{id}/gates")
    public ApiResponse<List<ProjectGate>> gates(@PathVariable Long id) {
        Account me = currentUser.require();
        return ApiResponse.ok(projectService.gates(id, me));
    }

    @PostMapping("/{id}/uat-approve")
    public ApiResponse<Void> uatApprove(@PathVariable Long id) {
        projectService.uatApprove(id, currentUser.require());
        return ApiResponse.ok(null);
    }
}
