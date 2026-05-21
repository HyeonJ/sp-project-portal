package com.softpuzzle.pm.deliverable;

import java.time.OffsetDateTime;

/** 산출물 슬롯 (프로젝트당 5종: requirements·ia·design·prototype·figma). */
public class DeliverableSlot {
    private Long id;
    private Long projectId;
    private String slotType;
    private Long currentVersionId;
    private String status;          // empty | draft | pending-review | confirmed | rejected
    private OffsetDateTime createdAt;

    // 조인 조회용
    private Short currentVersionNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }

    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public Short getCurrentVersionNo() { return currentVersionNo; }
    public void setCurrentVersionNo(Short currentVersionNo) { this.currentVersionNo = currentVersionNo; }
}
