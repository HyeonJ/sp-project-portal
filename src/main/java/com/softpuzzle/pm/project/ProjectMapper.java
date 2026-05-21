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
}
