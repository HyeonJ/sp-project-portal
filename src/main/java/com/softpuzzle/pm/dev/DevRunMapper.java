package com.softpuzzle.pm.dev;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DevRunMapper {

    List<DevRun> findByProject(@Param("projectId") Long projectId);

    void insert(DevRun devRun);
}
