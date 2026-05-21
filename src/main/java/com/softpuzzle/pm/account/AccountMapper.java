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
}
