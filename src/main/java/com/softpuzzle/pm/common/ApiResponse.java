package com.softpuzzle.pm.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 글로벌 응답 봉투: 성공 {success:true,data} / 에러 {success:false,code,message,fieldErrors?}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message,
        Map<String, String> fieldErrors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, code, message, null);
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, String> fieldErrors) {
        return new ApiResponse<>(false, null, code, message, fieldErrors);
    }
}
