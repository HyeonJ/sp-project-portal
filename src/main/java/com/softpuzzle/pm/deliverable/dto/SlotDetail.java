package com.softpuzzle.pm.deliverable.dto;

import com.softpuzzle.pm.deliverable.DeliverableSlot;
import com.softpuzzle.pm.deliverable.FileAsset;
import com.softpuzzle.pm.deliverable.SlotVersion;
import java.util.List;

/** 슬롯 상세 = 슬롯 + 현재 버전 + 파일 묶음. */
public record SlotDetail(DeliverableSlot slot, SlotVersion version, List<FileAsset> files) {
}
