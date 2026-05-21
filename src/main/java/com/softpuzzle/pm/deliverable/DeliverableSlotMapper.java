package com.softpuzzle.pm.deliverable;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeliverableSlotMapper {

    List<DeliverableSlot> findByProject(@Param("projectId") Long projectId);

    DeliverableSlot findByProjectAndType(@Param("projectId") Long projectId,
                                         @Param("slotType") String slotType);

    DeliverableSlot findById(@Param("id") Long id);

    /** 동시성 제어용 행 잠금. */
    DeliverableSlot findByIdForUpdate(@Param("id") Long id);

    void insert(DeliverableSlot slot);

    /** 최신 버전 포인터 + 상태 동기화. */
    int updateCurrentVersion(@Param("id") Long id,
                             @Param("currentVersionId") Long currentVersionId,
                             @Param("status") String status);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
