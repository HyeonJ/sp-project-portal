package com.softpuzzle.pm.dev;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/dev-runs")
public class DevRunController {

    private final DevRunService devRunService;
    private final CurrentUser currentUser;

    public DevRunController(DevRunService devRunService, CurrentUser currentUser) {
        this.devRunService = devRunService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<DevRun>> history(@PathVariable Long projectId) {
        return ApiResponse.ok(devRunService.history(projectId, currentUser.require()));
    }

    @PostMapping
    public ApiResponse<DevRun> trigger(@PathVariable Long projectId) {
        return ApiResponse.ok(devRunService.trigger(projectId, currentUser.require()));
    }
}
