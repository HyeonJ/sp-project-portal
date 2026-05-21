package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.storage.FileStorage;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.softpuzzle.pm.project.MembershipGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 버전 생성(이전 파일 묶음 사본·이전 컨펌 무효화)과 선행 변경 확인(ackUpstream).
 * 과거 버전은 immutable로 보존(history), 슬롯 포인터만 새 draft로 이동.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    private final DeliverableSlotMapper slotMapper;
    private final SlotVersionMapper versionMapper;
    private final FileAssetMapper assetMapper;
    private final ActivityEventMapper activityMapper;
    private final FileStorage storage;
    private final MembershipGuard guard;

    public VersionService(DeliverableSlotMapper slotMapper, SlotVersionMapper versionMapper,
                          FileAssetMapper assetMapper, ActivityEventMapper activityMapper,
                          FileStorage storage, MembershipGuard guard) {
        this.slotMapper = slotMapper;
        this.versionMapper = versionMapper;
        this.assetMapper = assetMapper;
        this.activityMapper = activityMapper;
        this.storage = storage;
        this.guard = guard;
    }

    @Transactional
    public SlotVersion createNewVersion(Long projectId, String slotType, String changeSummary, Account actor) {
        guard.assertCanRequestReview(projectId, actor);
        DeliverableSlot slot = lockSlot(projectId, slotType);
        if (slot.getCurrentVersionId() == null) {
            throw ApiException.conflict("NO_VERSION", "빈 슬롯은 파일 업로드로 시작하세요.");
        }
        SlotVersion current = versionMapper.findByIdForUpdate(slot.getCurrentVersionId());
        if ("draft".equals(current.getStatus())) {
            throw ApiException.conflict("DRAFT_EXISTS", "이미 작성 중인 버전이 있습니다.");
        }
        if ("pending-review".equals(current.getStatus())) {
            throw ApiException.conflict("UNDER_REVIEW", "검토 중인 버전입니다. 회수 후 진행하세요.");
        }
        if (changeSummary == null || changeSummary.isBlank()) {
            throw ApiException.conflict("SUMMARY_REQUIRED", "변경 요약은 필수입니다.");
        }

        SlotVersion next = new SlotVersion();
        next.setSlotId(slot.getId());
        next.setVersionNo(versionMapper.nextVersionNo(slot.getId()));
        next.setStatus("draft");
        next.setChangeSummary(changeSummary.trim());
        next.setCreatedBy(actor.getId());
        versionMapper.insert(next);

        List<String> copiedKeys = new ArrayList<>();
        try {
            for (FileAsset src : assetMapper.findByVersion(current.getId())) {
                FileAsset copy = new FileAsset();
                copy.setSlotVersionId(next.getId());
                copy.setAssetKind(src.getAssetKind());
                copy.setLogicalKey(src.getLogicalKey());
                copy.setPosition(src.getPosition());
                copy.setOriginalName(src.getOriginalName());
                copy.setDescription(src.getDescription());
                copy.setContentType(src.getContentType());
                copy.setSizeBytes(src.getSizeBytes());
                copy.setExternalUrl(src.getExternalUrl());
                copy.setUploadedBy(actor.getId());
                if ("file".equals(src.getAssetKind())) {
                    String newKey = storage.copy(src.getStorageKey());
                    copiedKeys.add(newKey);
                    copy.setStorageKey(newKey);
                }
                assetMapper.insert(copy);
            }
            slotMapper.updateCurrentVersion(slot.getId(), next.getId(), "draft");
            if ("confirmed".equals(current.getStatus())) {
                activity(slot.getId(), "invalidated", current.getId(), actor.getId(),
                        "새 버전(v" + next.getVersionNo() + ") 생성으로 이전 컨펌(v" + current.getVersionNo() + ") 무효화");
            }
            activity(slot.getId(), "version_created", next.getId(), actor.getId(), changeSummary.trim());
            log.info("[createNewVersion] project={} slot={} v={}", projectId, slotType, next.getVersionNo());
            return next;
        } catch (RuntimeException e) {
            copiedKeys.forEach(storage::deleteQuietly);
            throw e;
        }
    }

    /** 선행 변경 검토 완료(영향 없음) — 컨펌 버전의 선행 스탬프를 현재 선행 컨펌 버전으로 갱신, 배지 해제. */
    @Transactional
    public void ackUpstream(Long projectId, String slotType, Account actor) {
        guard.assertCanRequestReview(projectId, actor);
        String upstreamType = SlotTypes.UPSTREAM.get(slotType);
        if (upstreamType == null) {
            throw ApiException.conflict("NO_UPSTREAM", "선행 산출물이 없습니다.");
        }
        DeliverableSlot slot = lockSlot(projectId, slotType);
        if (!"confirmed".equals(slot.getStatus())) {
            throw ApiException.conflict("NOT_CONFIRMED", "컨펌된 산출물만 선행 검토 완료로 처리할 수 있습니다.");
        }
        DeliverableSlot upstream = slotMapper.findByProjectAndType(projectId, upstreamType);
        if (upstream == null || !"confirmed".equals(upstream.getStatus())) {
            throw ApiException.conflict("UPSTREAM_NOT_CONFIRMED", "선행 산출물이 컨펌 상태가 아닙니다.");
        }
        versionMapper.updateUpstreamStamp(slot.getCurrentVersionId(), upstream.getCurrentVersionId());
        activity(slot.getId(), "upstream_reviewed", slot.getCurrentVersionId(), actor.getId(), null);
    }

    private DeliverableSlot lockSlot(Long projectId, String slotType) {
        DeliverableSlot slot = slotMapper.findByProjectAndType(projectId, slotType);
        if (slot == null) {
            throw ApiException.notFound("산출물 슬롯을 찾을 수 없습니다.");
        }
        return slotMapper.findByIdForUpdate(slot.getId());
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
