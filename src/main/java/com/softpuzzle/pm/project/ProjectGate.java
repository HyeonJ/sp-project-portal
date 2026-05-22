package com.softpuzzle.pm.project;

import java.time.OffsetDateTime;

/** 게이트 상태 (5·7·9·11·13·18). status pass/wait/lock — 컨펌 순서 검증용(소프트 게이트). */
public class ProjectGate {
    private Long id;
    private Long projectId;
    private Short gateStage;
    private String status;
    private OffsetDateTime passedAt;
    private Long passedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Short getGateStage() { return gateStage; }
    public void setGateStage(Short gateStage) { this.gateStage = gateStage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getPassedAt() { return passedAt; }
    public void setPassedAt(OffsetDateTime passedAt) { this.passedAt = passedAt; }

    public Long getPassedBy() { return passedBy; }
    public void setPassedBy(Long passedBy) { this.passedBy = passedBy; }
}
