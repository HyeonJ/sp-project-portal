package com.softpuzzle.pm.project;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectGateMapper {

    List<ProjectGate> findByProject(@Param("projectId") Long projectId);

    void insert(ProjectGate gate);
}
