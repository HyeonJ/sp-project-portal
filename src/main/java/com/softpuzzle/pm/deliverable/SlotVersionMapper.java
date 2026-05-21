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
}
