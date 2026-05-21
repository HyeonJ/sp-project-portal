package com.softpuzzle.pm.project;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectGateMapper {

    List<ProjectGate> findByProject(@Param("projectId") Long projectId);

    ProjectGate findByProjectAndStageForUpdate(@Param("projectId") Long projectId,
                                               @Param("gateStage") short gateStage);

    void insert(ProjectGate gate);

    int markPass(@Param("id") Long id, @Param("passedBy") Long passedBy);

    /** lock → wait (다음 게이트 해제). */
    int unlockToWait(@Param("id") Long id);
}
