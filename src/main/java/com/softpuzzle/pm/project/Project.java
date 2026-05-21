package com.softpuzzle.pm.project;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 프로젝트. current_stage 1~24, status 진행/완료 등. */
public class Project {
    private Long id;
    private String name;
    private Long clientOrgId;
    private String type;          // SaaS | 웹사이트 | 모바일
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Short currentStage;
    private String status;
    private OffsetDateTime createdAt;

    // 조인 조회용(저장 안 함)
    private String clientOrgName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getClientOrgId() { return clientOrgId; }
    public void setClientOrgId(Long clientOrgId) { this.clientOrgId = clientOrgId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Short getCurrentStage() { return currentStage; }
    public void setCurrentStage(Short currentStage) { this.currentStage = currentStage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getClientOrgName() { return clientOrgName; }
    public void setClientOrgName(String clientOrgName) { this.clientOrgName = clientOrgName; }
}
