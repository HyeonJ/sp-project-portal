package com.softpuzzle.pm.qa.dto;

import com.softpuzzle.pm.qa.Defect;
import com.softpuzzle.pm.qa.DefectAttachment;
import com.softpuzzle.pm.qa.TestCase;
import java.util.List;

/** 결함 상세 = 결함 + 연결 TC + 첨부. */
public record DefectDetail(Defect defect, List<TestCase> linkedTestCases, List<DefectAttachment> attachments) {
}
