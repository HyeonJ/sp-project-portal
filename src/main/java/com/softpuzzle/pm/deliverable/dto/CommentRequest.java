package com.softpuzzle.pm.deliverable.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 코멘트 작성·수정 요청. */
public record CommentRequest(@NotBlank @Size(max = 5000) String body) {
}
