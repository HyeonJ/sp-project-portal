package com.softpuzzle.pm.deliverable;

import java.time.OffsetDateTime;

/** 슬롯 버전 스냅샷. 한 버전 = 파일 묶음 전체. 잠긴 버전(검토중·컨펌·반려)은 immutable. */
public class SlotVersion {
    private Long id;
    private Long slotId;
    private Short versionNo;
    private String status;          // draft | pending-review | confirmed | rejected
    private String changeSummary;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long reviewRequestedBy;
    private OffsetDateTime reviewRequestedAt;
    private Long reviewedBy;
    private OffsetDateTime reviewedAt;
    private Long confirmedUpstreamVersionId;

    // 조인 조회용
    private String createdByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public Short getVersionNo() { return versionNo; }
    public void setVersionNo(Short versionNo) { this.versionNo = versionNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Long getReviewRequestedBy() { return reviewRequestedBy; }
    public void setReviewRequestedBy(Long reviewRequestedBy) { this.reviewRequestedBy = reviewRequestedBy; }

    public OffsetDateTime getReviewRequestedAt() { return reviewRequestedAt; }
    public void setReviewRequestedAt(OffsetDateTime reviewRequestedAt) { this.reviewRequestedAt = reviewRequestedAt; }

    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getConfirmedUpstreamVersionId() { return confirmedUpstreamVersionId; }
    public void setConfirmedUpstreamVersionId(Long id) { this.confirmedUpstreamVersionId = id; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
}
