package com.softpuzzle.pm.qa;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TestCaseMapper {

    List<TestCase> findByProject(@Param("projectId") Long projectId);

    TestCase findById(@Param("id") Long id);

    void insert(TestCase tc);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("actualResult") String actualResult);

    int updateFields(TestCase tc);

    int updateAssignee(@Param("id") Long id, @Param("assigneeId") Long assigneeId);
}
