package com.softpuzzle.pm.account;

import java.time.OffsetDateTime;

/** 고객사(발주사) 조직. */
public class ClientOrg {
    private Long id;
    private String name;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
