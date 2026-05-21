package com.softpuzzle.pm.qa;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodeSequenceMapper {

    /** count개 코드를 원자적으로 할당하고 시작 번호 반환 (upsert + RETURNING, 행 잠금 직렬화). */
    int allocate(@Param("projectId") Long projectId, @Param("entityType") String entityType,
                 @Param("count") int count);
}
