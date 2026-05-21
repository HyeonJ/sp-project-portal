package com.softpuzzle.pm.deliverable;

import java.util.List;
import java.util.Map;

/** 산출물 슬롯 종류·순서·선행 관계·게이트 매핑 (단일 정의처). */
public final class SlotTypes {

    public static final List<String> ORDER =
            List.of("requirements", "ia", "design", "prototype", "figma");

    /** 직속 선행 슬롯 (REQ-WF-005 배지·스탬프). requirements는 선행 없음. */
    public static final Map<String, String> UPSTREAM =
            Map.of("ia", "requirements", "design", "ia", "prototype", "design", "figma", "prototype");

    /** 슬롯 컨펌 → 통과 게이트. figma는 게이트 없음. */
    public static final Map<String, Short> GATE =
            Map.of("requirements", (short) 9, "ia", (short) 11, "design", (short) 13, "prototype", (short) 15);

    private SlotTypes() {
    }
}
