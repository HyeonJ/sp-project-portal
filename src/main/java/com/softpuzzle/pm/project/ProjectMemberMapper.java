package com.softpuzzle.pm.project;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMemberMapper {

    /** 활성 멤버 1건 (없으면 null). */
    ProjectMember findActive(@Param("projectId") Long projectId, @Param("accountId") Long accountId);

    /** 제외 포함 1쌍 1행 조회 (reactivate 판단용). */
    ProjectMember findAny(@Param("projectId") Long projectId, @Param("accountId") Long accountId);

    /** 프로젝트 활성 멤버 목록 (계정 정보 조인). */
    List<ProjectMember> findActiveMembers(@Param("projectId") Long projectId);

    boolean existsActive(@Param("projectId") Long projectId, @Param("accountId") Long accountId);

    void insert(ProjectMember member);

    /** 같은 1쌍 행 재활성화 (left_at=NULL, joined_at 갱신). */
    int reactivate(@Param("projectId") Long projectId, @Param("accountId") Long accountId,
                   @Param("invitedBy") Long invitedBy);

    /** 소프트 제외 (left_at=now). */
    int softRemove(@Param("projectId") Long projectId, @Param("accountId") Long accountId);
}
