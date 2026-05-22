package com.softpuzzle.pm.qa;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DefectMapper {

    List<Defect> findByProject(@Param("projectId") Long projectId);

    Defect findById(@Param("id") Long id);

    void insert(Defect defect);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateAssignee(@Param("id") Long id, @Param("assigneeId") Long assigneeId);

    int updateFields(Defect defect);

    // TC↔결함 링크
    void insertLink(@Param("testCaseId") Long testCaseId, @Param("defectId") Long defectId);

    int deleteLink(@Param("testCaseId") Long testCaseId, @Param("defectId") Long defectId);

    /** 결함에 연결된 TC. */
    List<TestCase> findLinkedTestCases(@Param("defectId") Long defectId);

    /** TC에 연결된 결함. */
    List<Defect> findLinkedDefects(@Param("testCaseId") Long testCaseId);
}
