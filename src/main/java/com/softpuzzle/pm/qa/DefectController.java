package com.softpuzzle.pm.qa;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.qa.dto.CreateDefectRequest;
import com.softpuzzle.pm.qa.dto.DefectDetail;
import com.softpuzzle.pm.qa.dto.Requests;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/defects")
public class DefectController {

    private final DefectService defectService;
    private final CurrentUser currentUser;

    public DefectController(DefectService defectService, CurrentUser currentUser) {
        this.defectService = defectService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<Defect>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(defectService.list(projectId, currentUser.require()));
    }

    @GetMapping("/{defectId}")
    public ApiResponse<DefectDetail> detail(@PathVariable Long projectId, @PathVariable Long defectId) {
        return ApiResponse.ok(defectService.detail(projectId, defectId, currentUser.require()));
    }

    @PostMapping
    public ApiResponse<Defect> create(@PathVariable Long projectId,
                                      @Valid @RequestBody CreateDefectRequest req) {
        return ApiResponse.ok(defectService.create(projectId, req, currentUser.require()));
    }

    @PatchMapping("/{defectId}")
    public ApiResponse<Void> update(@PathVariable Long projectId, @PathVariable Long defectId,
                                    @Valid @RequestBody CreateDefectRequest req) {
        defectService.update(projectId, defectId, req, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/{defectId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long projectId, @PathVariable Long defectId,
                                          @Valid @RequestBody Requests.DefectStatus req) {
        defectService.updateStatus(projectId, defectId, req.status(), currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{defectId}/test-cases/{tcId}")
    public ApiResponse<Void> link(@PathVariable Long projectId, @PathVariable Long defectId,
                                  @PathVariable Long tcId) {
        defectService.linkTestCase(projectId, defectId, tcId, currentUser.require());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{defectId}/test-cases/{tcId}")
    public ApiResponse<Void> unlink(@PathVariable Long projectId, @PathVariable Long defectId,
                                    @PathVariable Long tcId) {
        defectService.unlinkTestCase(projectId, defectId, tcId, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{defectId}/attachments")
    public ApiResponse<DefectAttachment> upload(@PathVariable Long projectId, @PathVariable Long defectId,
                                                @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.conflict("EMPTY_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        Account me = currentUser.require();
        String name = sanitizeName(file.getOriginalFilename());
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try {
            return ApiResponse.ok(defectService.addAttachment(projectId, defectId, name, contentType,
                    file.getSize(), file.getInputStream(), me));
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 스트림 읽기 실패", e);
        }
    }

    @DeleteMapping("/{defectId}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long projectId, @PathVariable Long defectId,
                                              @PathVariable Long attachmentId) {
        defectService.deleteAttachment(projectId, defectId, attachmentId, currentUser.require());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{defectId}/attachments/{attachmentId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long projectId,
                                                        @PathVariable Long defectId,
                                                        @PathVariable Long attachmentId) {
        DefectAttachment a = defectService.attachmentForDownload(projectId, defectId, attachmentId,
                currentUser.require());
        MediaType mediaType = a.getContentType() != null
                ? MediaType.parseMediaType(a.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(a.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition.toString())
                .body(new InputStreamResource(defectService.openAttachment(a.getStorageKey())));
    }

    private String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replaceAll(".*[/\\\\]", "").trim();
        return name.isBlank() ? "file" : name;
    }
}
