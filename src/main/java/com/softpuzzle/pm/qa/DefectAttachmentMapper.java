package com.softpuzzle.pm.qa;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DefectAttachmentMapper {

    List<DefectAttachment> findByDefect(@Param("defectId") Long defectId);

    DefectAttachment findById(@Param("id") Long id);

    void insert(DefectAttachment attachment);

    int delete(@Param("id") Long id);
}
