package com.softpuzzle.pm.deliverable;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.storage.FileStorage;
import com.softpuzzle.pm.deliverable.dto.SlotDetail;
import com.softpuzzle.pm.project.MembershipGuard;
import com.softpuzzle.pm.project.ProjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 산출물 슬롯·버전·파일. 업로드는 스토리지 먼저 → 짧은 tx(잠금·draft 검증·메타 insert) → 커밋,
 * 실패 시 best-effort 스토리지 삭제(고아 보상).
 */
@Service
public class SlotService {

    private static final Logger log = LoggerFactory.getLogger(SlotService.class);
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private final DeliverableSlotMapper slotMapper;
    private final SlotVersionMapper versionMapper;
    private final FileAssetMapper assetMapper;
    private final ProjectMapper projectMapper;
    private final FileStorage storage;
    private final MembershipGuard guard;
    private final TransactionTemplate tx;

    public SlotService(DeliverableSlotMapper slotMapper, SlotVersionMapper versionMapper,
                       FileAssetMapper assetMapper, ProjectMapper projectMapper,
                       FileStorage storage, MembershipGuard guard,
                       PlatformTransactionManager txManager) {
        this.slotMapper = slotMapper;
        this.versionMapper = versionMapper;
        this.assetMapper = assetMapper;
        this.projectMapper = projectMapper;
        this.storage = storage;
        this.guard = guard;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional(readOnly = true)
    public List<DeliverableSlot> listSlots(Long projectId, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        List<DeliverableSlot> slots = slotMapper.findByProject(projectId);
        computeUpstreamBadges(slots);
        return slots;
    }

    @Transactional(readOnly = true)
    public SlotDetail slotDetail(Long projectId, String slotType, Account actor) {
        requireProject(projectId);
        guard.assertCanView(projectId, actor);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        SlotVersion version = null;
        List<FileAsset> files = List.of();
        if (slot.getCurrentVersionId() != null) {
            version = versionMapper.findById(slot.getCurrentVersionId());
            files = assetMapper.findByVersion(slot.getCurrentVersionId());
        }
        slot.setUpstreamChanged(upstreamChanged(projectId, slot, version));
        return new SlotDetail(slot, version, files);
    }

    /** REQ-WF-005: 컨펌된 슬롯이 스탬프한 선행 버전 ≠ 선행 슬롯의 현재 컨펌 버전 → 배지. */
    private void computeUpstreamBadges(List<DeliverableSlot> slots) {
        Map<String, DeliverableSlot> byType =
                slots.stream().collect(Collectors.toMap(DeliverableSlot::getSlotType, s -> s));
        for (DeliverableSlot slot : slots) {
            String upstreamType = SlotTypes.UPSTREAM.get(slot.getSlotType());
            if (upstreamType == null || !"confirmed".equals(slot.getStatus()) || slot.getCurrentVersionId() == null) {
                continue;
            }
            DeliverableSlot upstream = byType.get(upstreamType);
            if (isStaleUpstream(slot, upstream)) {
                slot.setUpstreamChanged(true);
            }
        }
    }

    private boolean upstreamChanged(Long projectId, DeliverableSlot slot, SlotVersion version) {
        String upstreamType = SlotTypes.UPSTREAM.get(slot.getSlotType());
        if (upstreamType == null || !"confirmed".equals(slot.getStatus()) || version == null) {
            return false;
        }
        DeliverableSlot upstream = slotMapper.findByProjectAndType(projectId, upstreamType);
        if (upstream == null || !"confirmed".equals(upstream.getStatus()) || upstream.getCurrentVersionId() == null) {
            return false;
        }
        return !Objects.equals(version.getConfirmedUpstreamVersionId(), upstream.getCurrentVersionId());
    }

    private boolean isStaleUpstream(DeliverableSlot slot, DeliverableSlot upstream) {
        if (upstream == null || !"confirmed".equals(upstream.getStatus()) || upstream.getCurrentVersionId() == null) {
            return false;
        }
        SlotVersion dv = versionMapper.findById(slot.getCurrentVersionId());
        return dv != null && !Objects.equals(dv.getConfirmedUpstreamVersionId(), upstream.getCurrentVersionId());
    }

    /** 파일 업로드 (스토리지 먼저 → 짧은 tx). draft 버전이 없으면 v1 생성. */
    public FileAsset uploadFile(Long projectId, String slotType, String originalName,
                                String contentType, long size, InputStream content, Account actor) {
        guard.assertMember(projectId, actor);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        if (size > MAX_FILE_BYTES) {
            throw ApiException.conflict("FILE_TOO_LARGE", "파일은 50MB 이하만 업로드할 수 있습니다.");
        }
        log.info("[uploadFile] project={} slot={} name={} by={}", projectId, slotType, originalName, actor.getEmail());

        Long versionId = tx.execute(s -> ensureDraftVersionLocked(slot.getId(), actor.getId()));

        String storageKey = storage.put(originalName, contentType, size, content);
        try {
            return tx.execute(s -> attachFile(versionId, originalName, contentType, size, storageKey, actor.getId()));
        } catch (RuntimeException e) {
            storage.deleteQuietly(storageKey);
            throw e;
        }
    }

    /** 외부 링크 자산 추가 (Figma 등). */
    @Transactional
    public FileAsset addUrl(Long projectId, String slotType, String label, String url, Account actor) {
        guard.assertMember(projectId, actor);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        Long versionId = ensureDraftVersionLocked(slot.getId(), actor.getId());
        SlotVersion v = versionMapper.findByIdForUpdate(versionId);
        assertDraft(v);
        FileAsset asset = new FileAsset();
        asset.setSlotVersionId(versionId);
        asset.setAssetKind("url");
        asset.setLogicalKey(uniqueLogicalKey(versionId, label));
        asset.setPosition(assetMapper.maxPosition(versionId) + 1);
        asset.setOriginalName(label);
        asset.setExternalUrl(url);
        asset.setUploadedBy(actor.getId());
        assetMapper.insert(asset);
        return asset;
    }

    public void deleteAsset(Long projectId, String slotType, Long assetId, Account actor) {
        guard.assertMember(projectId, actor);
        DeliverableSlot slot = requireSlot(projectId, slotType);
        String storageKey = tx.execute(s -> {
            SlotVersion v = versionMapper.findByIdForUpdate(slot.getCurrentVersionId());
            if (v == null) {
                throw ApiException.notFound("버전이 없습니다.");
            }
            assertDraft(v);
            FileAsset asset = assetMapper.findById(assetId);
            if (asset == null || !asset.getSlotVersionId().equals(v.getId())) {
                throw ApiException.notFound("파일을 찾을 수 없습니다.");
            }
            assetMapper.delete(assetId);
            return asset.getStorageKey();
        });
        if (storageKey != null) {
            storage.deleteQuietly(storageKey);
        }
    }

    // --- 내부 ---

    private Long ensureDraftVersionLocked(Long slotId, Long actorId) {
        DeliverableSlot slot = slotMapper.findByIdForUpdate(slotId);
        String status = slot.getStatus();
        if ("draft".equals(status) && slot.getCurrentVersionId() != null) {
            return slot.getCurrentVersionId();
        }
        if ("empty".equals(status)) {
            SlotVersion v = new SlotVersion();
            v.setSlotId(slotId);
            v.setVersionNo(versionMapper.nextVersionNo(slotId));
            v.setStatus("draft");
            v.setCreatedBy(actorId);
            versionMapper.insert(v);
            slotMapper.updateCurrentVersion(slotId, v.getId(), "draft");
            return v.getId();
        }
        throw ApiException.conflict("SLOT_LOCKED",
                "검토 중·컨펌·반려된 버전은 수정할 수 없습니다. 새 버전을 만들어 주세요.");
    }

    private FileAsset attachFile(Long versionId, String originalName, String contentType,
                                 long size, String storageKey, Long actorId) {
        SlotVersion v = versionMapper.findByIdForUpdate(versionId);
        assertDraft(v);
        FileAsset asset = new FileAsset();
        asset.setSlotVersionId(versionId);
        asset.setAssetKind("file");
        asset.setLogicalKey(uniqueLogicalKey(versionId, originalName));
        asset.setPosition(assetMapper.maxPosition(versionId) + 1);
        asset.setOriginalName(originalName);
        asset.setContentType(contentType);
        asset.setSizeBytes(size);
        asset.setStorageKey(storageKey);
        asset.setUploadedBy(actorId);
        assetMapper.insert(asset);
        return asset;
    }

    private void assertDraft(SlotVersion v) {
        if (v == null || !"draft".equals(v.getStatus())) {
            throw ApiException.conflict("VERSION_LOCKED", "초안 상태에서만 파일을 변경할 수 있습니다.");
        }
    }

    private String uniqueLogicalKey(Long versionId, String base) {
        if (!assetMapper.existsLogicalKey(versionId, base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + " (" + i + ")";
            if (!assetMapper.existsLogicalKey(versionId, candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("DUPLICATE_KEY", "동일 이름 파일이 너무 많습니다.");
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
