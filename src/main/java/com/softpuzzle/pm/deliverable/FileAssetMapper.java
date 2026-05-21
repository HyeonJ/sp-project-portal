package com.softpuzzle.pm.deliverable;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileAssetMapper {

    List<FileAsset> findByVersion(@Param("versionId") Long versionId);

    FileAsset findById(@Param("id") Long id);

    boolean existsLogicalKey(@Param("versionId") Long versionId, @Param("logicalKey") String logicalKey);

    int maxPosition(@Param("versionId") Long versionId);

    void insert(FileAsset asset);

    int delete(@Param("id") Long id);
}
