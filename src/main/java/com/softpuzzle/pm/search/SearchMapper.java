package com.softpuzzle.pm.search;

import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.TestCase;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchMapper {

    List<TestCase> searchTestCases(@Param("projectIds") List<Long> projectIds, @Param("q") String q);

    List<Defect> searchDefects(@Param("projectIds") List<Long> projectIds, @Param("q") String q);
}
