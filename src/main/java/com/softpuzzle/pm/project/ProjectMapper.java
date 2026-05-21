package com.softpuzzle.pm.project;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMapper {

    Project findById(@Param("id") Long id);

    /** 계정이 활성 참여 중인 프로젝트 (대시보드). */
    List<Project> findActiveByMember(@Param("accountId") Long accountId);

    /** 관리자: 전체 프로젝트. */
    List<Project> findAll();

    void insert(Project project);

    /** 단계 전진 (뒤로 가지 않음). */
    int bumpStage(@Param("id") Long id, @Param("stage") int stage);

    /** 완료 처리 (status·stage 동시). */
    int markCompleted(@Param("id") Long id);

    /** 기본 정보 편집 (SCR-SET-001). */
    int updateInfo(@Param("id") Long id, @Param("name") String name, @Param("type") String type,
                   @Param("description") String description,
                   @Param("startDate") java.time.LocalDate startDate,
                   @Param("endDate") java.time.LocalDate endDate);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
