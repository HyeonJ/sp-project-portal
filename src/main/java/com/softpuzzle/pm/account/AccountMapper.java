package com.softpuzzle.pm.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account findByEmail(@Param("email") String email);

    Account findById(@Param("id") Long id);

    int insert(Account account);
}
