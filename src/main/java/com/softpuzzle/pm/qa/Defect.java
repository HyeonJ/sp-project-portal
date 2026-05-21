package com.softpuzzle.pm.qa;

import java.time.OffsetDateTime;

/** 결함 (프로젝트별 DEF-NNN 채번). */
public class Defect {
    private Long id;
    private Long projectId;
    private String code;
    private String title;
    private String severity;       // High | Medium | Low
    private String status;         // open | in_progress | resolved | cannot_reproduce
    private String reproSteps;
    private String environment;
    private Long reporterId;
    private Long assigneeId;
    private OffsetDateTime createdAt;

    private String reporterName;
    private String assigneeName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReproSteps() { return reproSteps; }
    public void setReproSteps(String reproSteps) { this.reproSteps = reproSteps; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
}
