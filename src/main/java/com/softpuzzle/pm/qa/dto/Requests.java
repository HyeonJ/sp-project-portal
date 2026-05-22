package com.softpuzzle.pm.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** QA 소형 요청 DTO 모음. */
public final class Requests {

    public record CsvImport(@NotBlank @Size(max = 1_000_000) String csv) {
    }

    public record TcStatus(@NotBlank String status, @Size(max = 4000) String actualResult) {
    }

    public record DefectStatus(@NotBlank String status) {
    }

    /** TC 담당자 일괄 지정 — assigneeId가 null이면 미지정으로 해제. */
    public record BulkAssign(@NotEmpty List<Long> testCaseIds, Long assigneeId) {
    }

    private Requests() {
    }
}
