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
        @Size(max = 4000) String expectedResult) {
}
