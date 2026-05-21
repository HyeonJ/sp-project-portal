package com.softpuzzle.pm.member;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvitationMapper {

    void insert(Invitation invitation);

    Invitation findByTokenHash(@Param("tokenHash") String tokenHash);

    int markAccepted(@Param("id") Long id);

    /** 활성(pending) 초대 만료 처리 (재발송·중복 방지). */
    int expirePending(@Param("projectId") Long projectId, @Param("email") String email,
                      @Param("inviteType") String inviteType);
}
