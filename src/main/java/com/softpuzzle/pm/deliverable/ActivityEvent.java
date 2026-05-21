package com.softpuzzle.pm.deliverable;

import java.time.OffsetDateTime;

/** 활동 이력 (append-only). version_created·review_requested·confirmed·rejected 등. */
public class ActivityEvent {
    private Long id;
    private Long slotId;
    private String eventType;
    private Long slotVersionId;
    private Long actorId;
    private String body;
    private OffsetDateTime createdAt;

    // 조인 조회용
    private String actorName;
    private Short versionNo;
    private String slotType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getSlotVersionId() { return slotVersionId; }
    public void setSlotVersionId(Long slotVersionId) { this.slotVersionId = slotVersionId; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public Short getVersionNo() { return versionNo; }
    public void setVersionNo(Short versionNo) { this.versionNo = versionNo; }

    public String getSlotType() { return slotType; }
    public void setSlotType(String slotType) { this.slotType = slotType; }
}
