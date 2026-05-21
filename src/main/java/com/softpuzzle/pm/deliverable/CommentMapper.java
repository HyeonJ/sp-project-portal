package com.softpuzzle.pm.deliverable;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper {

    List<Comment> findByVersion(@Param("versionId") Long versionId);

    Comment findById(@Param("id") Long id);

    void insert(Comment comment);

    int updateBody(@Param("id") Long id, @Param("body") String body);

    int softDelete(@Param("id") Long id);
}
