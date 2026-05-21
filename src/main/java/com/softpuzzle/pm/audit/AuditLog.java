package com.softpuzzle.pm.audit;

import java.time.OffsetDateTime;

/** 감사 로그 (append-only). */
public class AuditLog {
    private Long id;
    private Long actorId;
    private String actorRole;
    private String action;
    private String target;
    private String ip;
    private OffsetDateTime createdAt;

    private String actorName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
}
