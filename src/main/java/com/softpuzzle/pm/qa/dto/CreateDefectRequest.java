package com.softpuzzle.pm.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDefectRequest(
        @NotBlank @Size(max = 200) String title,
        @Pattern(regexp = "High|Medium|Low") String severity,
        @Size(max = 4000) String reproSteps,
        @Size(max = 200) String environment,
        Long testCaseId,
        Long assigneeId) {

    /** 담당자 미지정 호환 생성자. */
    public CreateDefectRequest(String title, String severity, String reproSteps,
                               String environment, Long testCaseId) {
        this(title, severity, reproSteps, environment, testCaseId, null);
    }
}
