package com.softpuzzle.pm.account;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account findByEmail(@Param("email") String email);

    Account findById(@Param("id") Long id);

    int insert(Account account);

    /** 관리자: tier별 계정 목록 (tier=null이면 전체). */
    List<Account> findByTier(@Param("tier") String tier);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 비밀번호 설정 + active 활성화 (초대 수락·재설정). */
    int updatePasswordAndActivate(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
