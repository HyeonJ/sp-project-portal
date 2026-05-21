package com.softpuzzle.pm.deliverable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 외부 링크 자산 추가 (Figma 등). */
public record AddUrlRequest(
        @NotBlank @Size(max = 255) String label,
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+", message = "http(s) URL이어야 합니다.") String url) {
}
