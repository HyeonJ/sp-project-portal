package com.softpuzzle.pm.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTestCaseRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 30) String phase,
        @Pattern(regexp = "High|Medium|Low") String priority,
        @Size(max = 4000) String precondition,
        @Size(max = 4000) String steps,
        @Size(max = 4000) String expectedResult,
        Long assigneeId) {

    /** 담당자 미지정 호환 생성자. */
    public CreateTestCaseRequest(String title, String phase, String priority,
                                 String precondition, String steps, String expectedResult) {
        this(title, phase, priority, precondition, steps, expectedResult, null);
    }
}
