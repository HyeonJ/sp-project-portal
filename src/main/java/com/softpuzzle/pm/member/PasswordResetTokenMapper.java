package com.softpuzzle.pm.member;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordResetTokenMapper {

    void insert(PasswordResetToken token);

    PasswordResetToken findByTokenHash(@Param("tokenHash") String tokenHash);

    int markUsed(@Param("id") Long id);
}
