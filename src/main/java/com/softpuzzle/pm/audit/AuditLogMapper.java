package com.softpuzzle.pm.audit;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

    void insert(AuditLog log);

    List<AuditLog> findRecent(@Param("limit") int limit);
}
