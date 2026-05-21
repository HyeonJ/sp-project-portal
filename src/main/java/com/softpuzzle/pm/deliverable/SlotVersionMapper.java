package com.softpuzzle.pm.deliverable;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SlotVersionMapper {

    SlotVersion findById(@Param("id") Long id);

    SlotVersion findByIdForUpdate(@Param("id") Long id);

    List<SlotVersion> findBySlot(@Param("slotId") Long slotId);

    /** 슬롯의 다음 버전 번호 (없으면 1). */
    short nextVersionNo(@Param("slotId") Long slotId);

    void insert(SlotVersion version);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** draft → pending-review (review_requested_* 설정). */
    int markPendingReview(@Param("id") Long id, @Param("requestedBy") Long requestedBy);

    /** pending-review → confirmed (reviewed_* 설정, 선행 스탬프). */
    int markConfirmed(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy,
                      @Param("upstreamVersionId") Long upstreamVersionId);

    /** pending-review → rejected (reviewed_* 설정). */
    int markRejected(@Param("id") Long id, @Param("reviewedBy") Long reviewedBy);

    /** pending-review → draft (review_* 초기화). */
    int markRecalledToDraft(@Param("id") Long id);
}
