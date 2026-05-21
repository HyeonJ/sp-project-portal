package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectGate;
import com.softpuzzle.pm.project.ProjectGateMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검토 상태머신: draft → pending-review → confirmed/rejected, 회수(→draft).
 * 동시성 = 소유 슬롯·버전 행 FOR UPDATE. 컨펌 시 게이트 순차 진행.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** 슬롯 컨펌 → 통과 게이트. figma는 게이트 없음. */
    private static final Map<String, Short> SLOT_GATE = Map.of(
            "requirements", (short) 9, "ia", (short) 11, "design", (short) 13, "prototype", (short) 15);
    /** 게이트 통과 시 해제할 다음 게이트 (15 이후 22는 UAT에서 별도 해제). */
    private static final Map<Short, Short> NEXT_GATE = Map.of(
            (short) 9, (short) 11, (short) 11, (short) 13, (short) 13, (short) 15);
    /** 직속 선행 슬롯 (REQ-WF-005 선행 배지·스탬프용). */
    private static final Map<String, String> UPSTREAM = Map.of(
            "ia", "requirements", "design", "ia", "prototype", "design", "figma", "prototype");

    private final DeliverableSlotMapper slotMapper;
    private final SlotVersionMapper versionMapper;
    private final FileAssetMapper assetMapper;
    private final ProjectGateMapper gateMapper;
    private final ActivityEventMapper activityMapper;
    private final MembershipGuard guard;

    public ReviewService(DeliverableSlotMapper slotMapper, SlotVersionMapper versionMapper,
                         FileAssetMapper assetMapper, ProjectGateMapper gateMapper,
                         ActivityEventMapper activityMapper, MembershipGuard guard) {
        this.slotMapper = slotMapper;
        this.versionMapper = versionMapper;
        this.assetMapper = assetMapper;
        this.gateMapper = gateMapper;
        this.activityMapper = activityMapper;
        this.guard = guard;
    }

    @Transactional
    public void requestReview(Long projectId, String slotType, Account actor) {
        guard.assertCanRequestReview(projectId, actor);
        DeliverableSlot slot = lockSlot(projectId, slotType);
        SlotVersion version = lockCurrentVersion(slot);
        if (!"draft".equals(version.getStatus())) {
            throw ApiException.conflict("INVALID_STATE", "초안 상태에서만 검토를 요청할 수 있습니다.");
        }
        if (assetMapper.findByVersion(version.getId()).isEmpty()) {
            throw ApiException.conflict("NO_FILES", "검토 요청 전 파일을 1개 이상 등록해 주세요.");
        }
        versionMapper.markPendingReview(version.getId(), actor.getId());
        slotMapper.updateStatus(slot.getId(), "pending-review");
        activity(slot.getId(), "review_requested", version.getId(), actor.getId(), null);
        log.info("[requestReview] project={} slot={} v={}", projectId, slotType, version.getVersionNo());
    }

    @Transactional
    public void recall(Long projectId, String slotType, Account actor) {
        guard.assertCanRequestReview(projectId, actor);
        DeliverableSlot slot = lockSlot(projectId, slotType);
        SlotVersion version = lockCurrentVersion(slot);
        if (!"pending-review".equals(version.getStatus())) {
            throw ApiException.conflict("INVALID_STATE", "검토 중인 버전만 회수할 수 있습니다.");
        }
        versionMapper.markRecalledToDraft(version.getId());
        slotMapper.updateStatus(slot.getId(), "draft");
        activity(slot.getId(), "review_recalled", version.getId(), actor.getId(), null);
    }

    @Transactional
    public void confirm(Long projectId, String slotType, Account actor) {
        guard.assertCanReview(projectId, actor);
        DeliverableSlot slot = lockSlot(projectId, slotType);
        SlotVersion version = lockCurrentVersion(slot);
        if (!"pending-review".equals(version.getStatus())) {
            throw ApiException.conflict("INVALID_STATE", "검토 요청된 버전만 컨펌할 수 있습니다.");
        }
        passGate(projectId, slotType, actor.getId());
        Long upstreamVersionId = upstreamConfirmedVersionId(projectId, slotType);
        versionMapper.markConfirmed(version.getId(), actor.getId(), upstreamVersionId);
        slotMapper.updateStatus(slot.getId(), "confirmed");
        activity(slot.getId(), "confirmed", version.getId(), actor.getId(), null);
        log.info("[confirm] project={} slot={} v={}", projectId, slotType, version.getVersionNo());
    }

    @Transactional
    public void reject(Long projectId, String slotType, String reason, Account actor) {
        guard.assertCanReview(projectId, actor);
        if (reason == null || reason.isBlank()) {
            throw ApiException.conflict("REASON_REQUIRED", "반려 사유는 필수입니다.");
        }
        DeliverableSlot slot = lockSlot(projectId, slotType);
        SlotVersion version = lockCurrentVersion(slot);
        if (!"pending-review".equals(version.getStatus())) {
            throw ApiException.conflict("INVALID_STATE", "검토 요청된 버전만 반려할 수 있습니다.");
        }
        versionMapper.markRejected(version.getId(), actor.getId());
        slotMapper.updateStatus(slot.getId(), "rejected");
        activity(slot.getId(), "rejected", version.getId(), actor.getId(), reason.trim());
    }

    // --- 내부 ---

    private void passGate(Long projectId, String slotType, Long actorId) {
        Short stage = SLOT_GATE.get(slotType);
        if (stage == null) {
            return; // figma 등 게이트 없음
        }
        ProjectGate gate = gateMapper.findByProjectAndStageForUpdate(projectId, stage);
        if (gate == null) {
            return;
        }
        if ("lock".equals(gate.getStatus())) {
            throw ApiException.conflict("GATE_OUT_OF_ORDER",
                    "이전 게이트(Gate " + previousStage(stage) + ") 미통과로 컨펌할 수 없습니다.");
        }
        gateMapper.markPass(gate.getId(), actorId);
        Short next = NEXT_GATE.get(stage);
        if (next != null) {
            ProjectGate nextGate = gateMapper.findByProjectAndStageForUpdate(projectId, next);
            if (nextGate != null) {
                gateMapper.unlockToWait(nextGate.getId());
            }
        }
    }

    private short previousStage(short stage) {
        return switch (stage) {
            case 11 -> 9;
            case 13 -> 11;
            case 15 -> 13;
            default -> stage;
        };
    }

    private Long upstreamConfirmedVersionId(Long projectId, String slotType) {
        String upstreamType = UPSTREAM.get(slotType);
        if (upstreamType == null) {
            return null;
        }
        DeliverableSlot upstream = slotMapper.findByProjectAndType(projectId, upstreamType);
        if (upstream != null && "confirmed".equals(upstream.getStatus())) {
            return upstream.getCurrentVersionId();
        }
        return null;
    }

    private DeliverableSlot lockSlot(Long projectId, String slotType) {
        DeliverableSlot slot = slotMapper.findByProjectAndType(projectId, slotType);
        if (slot == null) {
            throw ApiException.notFound("산출물 슬롯을 찾을 수 없습니다.");
        }
        return slotMapper.findByIdForUpdate(slot.getId());
    }

    private SlotVersion lockCurrentVersion(DeliverableSlot slot) {
        if (slot.getCurrentVersionId() == null) {
            throw ApiException.conflict("NO_VERSION", "버전이 없습니다.");
        }
        return versionMapper.findByIdForUpdate(slot.getCurrentVersionId());
    }

    private void activity(Long slotId, String type, Long versionId, Long actorId, String body) {
        ActivityEvent e = new ActivityEvent();
        e.setSlotId(slotId);
        e.setEventType(type);
        e.setSlotVersionId(versionId);
        e.setActorId(actorId);
        e.setBody(body);
        activityMapper.insert(e);
    }
}
