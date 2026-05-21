package com.softpuzzle.pm.deliverable;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityEventMapper {

    void insert(ActivityEvent event);

    /** 슬롯 활동 타임라인 (최신순). */
    List<ActivityEvent> findBySlot(@Param("slotId") Long slotId);

    /** 프로젝트 전체 최근 활동 (대시보드). */
    List<ActivityEvent> findByProject(@Param("projectId") Long projectId, @Param("limit") int limit);
}
