package com.softpuzzle.pm.project;

import java.time.OffsetDateTime;

/** 프로젝트 참여(N:M). left_at NULL=현재 참여, 값=소프트 제외. */
public class ProjectMember {
    private Long id;
    private Long projectId;
    private Long accountId;
    private Long invitedBy;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;

    // 조인 조회용
    private String accountName;
    private String accountEmail;
    private String accountTier;
    private String accountJob;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public Long getInvitedBy() { return invitedBy; }
    public void setInvitedBy(Long invitedBy) { this.invitedBy = invitedBy; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(OffsetDateTime joinedAt) { this.joinedAt = joinedAt; }

    public OffsetDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(OffsetDateTime leftAt) { this.leftAt = leftAt; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountEmail() { return accountEmail; }
    public void setAccountEmail(String accountEmail) { this.accountEmail = accountEmail; }

    public String getAccountTier() { return accountTier; }
    public void setAccountTier(String accountTier) { this.accountTier = accountTier; }

    public String getAccountJob() { return accountJob; }
    public void setAccountJob(String accountJob) { this.accountJob = accountJob; }
}
