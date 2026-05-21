package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 슬롯 버전 코멘트. 멤버만 작성, 본인 수정·삭제 + 관리자 강제 삭제. */
@Service
public class CommentService {

    private final DeliverableSlotMapper slotMapper;
    private final SlotVersionMapper versionMapper;
    private final CommentMapper commentMapper;
    private final ProjectMapper projectMapper;
    private final MembershipGuard guard;

    public CommentService(DeliverableSlotMapper slotMapper, SlotVersionMapper versionMapper,
                          CommentMapper commentMapper, ProjectMapper projectMapper, MembershipGuard guard) {
        this.slotMapper = slotMapper;
        this.versionMapper = versionMapper;
        this.commentMapper = commentMapper;
        this.projectMapper = projectMapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<Comment> list(Long projectId, String slotType, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        if (slot.getCurrentVersionId() == null) {
            return List.of();
        }
        return commentMapper.findByVersion(slot.getCurrentVersionId());
    }

    @Transactional
    public Comment add(Long projectId, String slotType, String body, Account actor) {
        requireProject(projectId);
        guard.assertMember(projectId, actor);
        requireBody(body);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        if (slot.getCurrentVersionId() == null) {
            throw ApiException.conflict("NO_VERSION", "버전이 없어 코멘트를 달 수 없습니다.");
        }
        Comment c = new Comment();
        c.setSlotVersionId(slot.getCurrentVersionId());
        c.setAuthorId(actor.getId());
        c.setBody(body.trim());
        commentMapper.insert(c);
        c.setAuthorName(actor.getName());
        return c;
    }

    @Transactional
    public void edit(Long projectId, Long commentId, String body, Account actor) {
        requireProject(projectId);
        requireBody(body);
        Comment c = requireCommentInProject(projectId, commentId);
        if (!c.getAuthorId().equals(actor.getId())) {
            throw ApiException.forbidden("본인 코멘트만 수정할 수 있습니다.");
        }
        commentMapper.updateBody(commentId, body.trim());
    }

    @Transactional
    public void delete(Long projectId, Long commentId, Account actor) {
        requireProject(projectId);
        Comment c = requireCommentInProject(projectId, commentId);
        boolean isAuthor = c.getAuthorId().equals(actor.getId());
        if (!isAuthor && !guard.isAdmin(actor)) {
            throw ApiException.forbidden("본인 코멘트 또는 관리자만 삭제할 수 있습니다.");
        }
        commentMapper.softDelete(commentId);
    }

    private Comment requireCommentInProject(Long projectId, Long commentId) {
        Comment c = commentMapper.findById(commentId);
        if (c == null || c.getDeletedAt() != null || c.getSlotVersionId() == null) {
            throw ApiException.notFound("코멘트를 찾을 수 없습니다.");
        }
        SlotVersion v = versionMapper.findById(c.getSlotVersionId());
        DeliverableSlot slot = v != null ? slotMapper.findById(v.getSlotId()) : null;
        if (slot == null || !slot.getProjectId().equals(projectId)) {
            throw ApiException.notFound("코멘트를 찾을 수 없습니다.");
        }
        return c;
    }

    private void requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw ApiException.conflict("EMPTY_BODY", "내용을 입력해 주세요.");
        }
    }

    private void requireProject(Long projectId) {
        if (projectMapper.findById(projectId) == null) {
            throw ApiException.notFound("프로젝트를 찾을 수 없습니다.");
        }
    }

    private DeliverableSlot requireSlot(Long projectId, String slotType) {
        DeliverableSlot slot = slotMapper.findByProjectAndType(projectId, slotType);
        if (slot == null) {
            throw ApiException.notFound("산출물 슬롯을 찾을 수 없습니다.");
        }
        return slot;
    }
}
