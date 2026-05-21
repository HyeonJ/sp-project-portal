package com.softpuzzle.pm.deliverable;

import java.time.OffsetDateTime;

/** 코멘트 (슬롯 버전 귀속). 본인 수정·삭제 + 관리자 강제 삭제. */
public class Comment {
    private Long id;
    private Long slotVersionId;
    private Long defectId;
    private Long authorId;
    private String body;
    private OffsetDateTime createdAt;
    private OffsetDateTime editedAt;
    private OffsetDateTime deletedAt;

    // 조인 조회용
    private String authorName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSlotVersionId() { return slotVersionId; }
    public void setSlotVersionId(Long slotVersionId) { this.slotVersionId = slotVersionId; }

    public Long getDefectId() { return defectId; }
    public void setDefectId(Long defectId) { this.defectId = defectId; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getEditedAt() { return editedAt; }
    public void setEditedAt(OffsetDateTime editedAt) { this.editedAt = editedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
}
