package com.softpuzzle.pm.export;

import com.softpuzzle.pm.common.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final ExportService exportService;
    private final CurrentUser currentUser;

    public ExportController(ExportService exportService, CurrentUser currentUser) {
        this.exportService = exportService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/projects/{id}/export")
    public ResponseEntity<Map<String, Object>> export(@PathVariable Long id) {
        Map<String, Object> data = exportService.export(id, currentUser.require());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("project-" + id + "-export.json", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", disposition.toString())
                .body(data);
    }
}
