package com.softpuzzle.pm.dashboard;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/projects/{id}/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@PathVariable Long id) {
        return ApiResponse.ok(dashboardService.dashboard(id, currentUser.require()));
    }
}
