package com.softpuzzle.pm.deliverable;

import java.time.OffsetDateTime;

/** 버전 묶음 내 파일 또는 외부 링크(Figma 등). */
public class FileAsset {
    private Long id;
    private Long slotVersionId;
    private String assetKind;       // file | url
    private String logicalKey;
    private Integer position;
    private String originalName;
    private String description;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;
    private String externalUrl;
    private Long uploadedBy;
    private OffsetDateTime uploadedAt;

    // 조인 조회용
    private String uploadedByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSlotVersionId() { return slotVersionId; }
    public void setSlotVersionId(Long slotVersionId) { this.slotVersionId = slotVersionId; }

    public String getAssetKind() { return assetKind; }
    public void setAssetKind(String assetKind) { this.assetKind = assetKind; }

    public String getLogicalKey() { return logicalKey; }
    public void setLogicalKey(String logicalKey) { this.logicalKey = logicalKey; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
}
