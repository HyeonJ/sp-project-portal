package com.softpuzzle.pm.account;

import java.time.OffsetDateTime;

/** 계정 (3-tier: admin/team/client). enum 값은 ASCII 머신 코드, UI 라벨은 표시 시 매핑. */
public class Account {
    private Long id;
    private String email;
    private String name;
    private String tier;          // admin | team | client
    private String job;           // pm | planner | designer | developer | qa (team 한정)
    private Long clientOrgId;     // client tier만
    private String passwordHash;
    private String status;        // active | pending | inactive
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getJob() { return job; }
    public void setJob(String job) { this.job = job; }

    public Long getClientOrgId() { return clientOrgId; }
    public void setClientOrgId(Long clientOrgId) { this.clientOrgId = clientOrgId; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
