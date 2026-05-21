package com.softpuzzle.pm.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClientOrgMapper {

    ClientOrg findById(@Param("id") Long id);

    ClientOrg findByName(@Param("name") String name);

    void insert(ClientOrg clientOrg);
}
