package com.softpuzzle.pm.qa;

import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.qa.dto.CreateTestCaseRequest;
import com.softpuzzle.pm.qa.dto.Requests;
import com.softpuzzle.pm.qa.dto.TestCaseDetail;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/test-cases")
public class TestCaseController {

    private final TestCaseService testCaseService;
    private final CurrentUser currentUser;

    public TestCaseController(TestCaseService testCaseService, CurrentUser currentUser) {
        this.testCaseService = testCaseService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<TestCase>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(testCaseService.list(projectId, currentUser.require()));
    }

    @GetMapping("/{tcId}")
    public ApiResponse<TestCaseDetail> detail(@PathVariable Long projectId, @PathVariable Long tcId) {
        return ApiResponse.ok(testCaseService.detail(projectId, tcId, currentUser.require()));
    }

    @PostMapping
    public ApiResponse<TestCase> create(@PathVariable Long projectId,
                                        @Valid @RequestBody CreateTestCaseRequest req) {
        return ApiResponse.ok(testCaseService.create(projectId, req, currentUser.require()));
    }

    @PostMapping("/import")
    public ApiResponse<Map<String, Integer>> importCsv(@PathVariable Long projectId,
                                                       @Valid @RequestBody Requests.CsvImport req) {
        int count = testCaseService.importCsv(projectId, req.csv(), currentUser.require());
        return ApiResponse.ok(Map.of("imported", count));
    }

    @PatchMapping("/{tcId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long projectId, @PathVariable Long tcId,
                                          @Valid @RequestBody Requests.TcStatus req) {
        testCaseService.updateStatus(projectId, tcId, req.status(), req.actualResult(), currentUser.require());
        return ApiResponse.ok(null);
    }
}
