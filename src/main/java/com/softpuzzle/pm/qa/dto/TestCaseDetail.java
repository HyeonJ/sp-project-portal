package com.softpuzzle.pm.qa.dto;

import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.TestCase;
import java.util.List;

/** TC 상세 = TC + 연결된 결함. */
public record TestCaseDetail(TestCase testCase, List<Defect> linkedDefects) {
}
