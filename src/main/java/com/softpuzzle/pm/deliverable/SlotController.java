package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.ApiResponse;
import com.softpuzzle.pm.common.CurrentUser;
import com.softpuzzle.pm.deliverable.dto.AddUrlRequest;
import com.softpuzzle.pm.deliverable.dto.NewVersionRequest;
import com.softpuzzle.pm.deliverable.dto.RejectRequest;
import com.softpuzzle.pm.deliverable.dto.SlotDetail;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/slots")
public class SlotController {

    private final SlotService slotService;
    private final ReviewService reviewService;
    private final VersionService versionService;
    private final CurrentUser currentUser;

    public SlotController(SlotService slotService, ReviewService reviewService,
                          VersionService versionService, CurrentUser currentUser) {
        this.slotService = slotService;
        this.reviewService = reviewService;
        this.versionService = versionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<DeliverableSlot>> slots(@PathVariable Long projectId) {
        return ApiResponse.ok(slotService.listSlots(projectId, currentUser.require()));
    }

    @GetMapping("/{slotType}")
    public ApiResponse<SlotDetail> detail(@PathVariable Long projectId, @PathVariable String slotType) {
        return ApiResponse.ok(slotService.slotDetail(projectId, slotType, currentUser.require()));
    }

    @PostMapping("/{slotType}/files")
    public ApiResponse<FileAsset> upload(@PathVariable Long projectId, @PathVariable String slotType,
                                         @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.conflict("EMPTY_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        Account me = currentUser.require();
        String name = sanitizeName(file.getOriginalFilename());
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try {
            FileAsset asset = slotService.uploadFile(projectId, slotType, name, contentType,
                    file.getSize(), file.getInputStream(), me);
            return ApiResponse.ok(asset);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 스트림 읽기 실패", e);
        }
    }

    @PostMapping("/{slotType}/urls")
    public ApiResponse<FileAsset> addUrl(@PathVariable Long projectId, @PathVariable String slotType,
                                         @Valid @RequestBody AddUrlRequest req) {
        return ApiResponse.ok(slotService.addUrl(projectId, slotType, req.label(), req.url(), currentUser.require()));
    }

    @DeleteMapping("/{slotType}/files/{assetId}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long projectId, @PathVariable String slotType,
                                         @PathVariable Long assetId) {
        slotService.deleteAsset(projectId, slotType, assetId, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slotType}/review-request")
    public ApiResponse<Void> requestReview(@PathVariable Long projectId, @PathVariable String slotType) {
        reviewService.requestReview(projectId, slotType, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slotType}/review-recall")
    public ApiResponse<Void> recall(@PathVariable Long projectId, @PathVariable String slotType) {
        reviewService.recall(projectId, slotType, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slotType}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long projectId, @PathVariable String slotType) {
        reviewService.confirm(projectId, slotType, currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slotType}/reject")
    public ApiResponse<Void> reject(@PathVariable Long projectId, @PathVariable String slotType,
                                    @Valid @RequestBody RejectRequest req) {
        reviewService.reject(projectId, slotType, req.reason(), currentUser.require());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slotType}/versions")
    public ApiResponse<SlotVersion> newVersion(@PathVariable Long projectId, @PathVariable String slotType,
                                               @Valid @RequestBody NewVersionRequest req) {
        return ApiResponse.ok(versionService.createNewVersion(projectId, slotType, req.changeSummary(),
                currentUser.require()));
    }

    @PostMapping("/{slotType}/ack-upstream")
    public ApiResponse<Void> ackUpstream(@PathVariable Long projectId, @PathVariable String slotType) {
        versionService.ackUpstream(projectId, slotType, currentUser.require());
        return ApiResponse.ok(null);
    }

    private String sanitizeName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replaceAll(".*[/\\\\]", "").trim();
        return name.isBlank() ? "file" : name;
    }
}
