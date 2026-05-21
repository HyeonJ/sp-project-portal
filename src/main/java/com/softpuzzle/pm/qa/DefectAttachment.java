package com.softpuzzle.pm.qa;

import java.time.OffsetDateTime;

/** 결함 증거 첨부 (스크린샷·로그 등). */
public class DefectAttachment {
    private Long id;
    private Long defectId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;
    private Long uploadedBy;
    private OffsetDateTime uploadedAt;

    private String uploadedByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDefectId() { return defectId; }
    public void setDefectId(Long defectId) { this.defectId = defectId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
}
