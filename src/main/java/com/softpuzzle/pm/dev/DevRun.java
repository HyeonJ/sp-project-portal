package com.softpuzzle.pm.dev;

import java.time.OffsetDateTime;

/** 개발(코드 자동 생성) 트리거 실행 이력. */
public class DevRun {
    private Long id;
    private Long projectId;
    private String result;        // success | fail
    private String reason;
    private Long triggeredBy;
    private OffsetDateTime createdAt;

    private String triggeredByName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(Long triggeredBy) { this.triggeredBy = triggeredBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getTriggeredByName() { return triggeredByName; }
    public void setTriggeredByName(String triggeredByName) { this.triggeredByName = triggeredByName; }
}
