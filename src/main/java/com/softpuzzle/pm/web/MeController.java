package com.softpuzzle.pm.web;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.ApiResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** jQuery AJAX용 JSON 엔드포인트 (인증·CSRF 검증). walking-skeleton 증명용. */
@RestController
public class MeController {

    private final AccountMapper accountMapper;

    public MeController(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @GetMapping("/api/me")
    public ApiResponse<Map<String, Object>> me(Principal principal) {
        Account a = accountMapper.findByEmail(principal.getName());
        if (a == null) {
            throw ApiException.notFound("계정 없음");
        }
        return ApiResponse.ok(Map.of(
                "id", a.getId(),
                "email", a.getEmail(),
                "name", a.getName(),
                "tier", a.getTier()));
    }

    /** 인증 + CSRF 검증되는 상태 변경 AJAX POST (walking-skeleton 증명). */
    @PostMapping("/api/echo")
    public ApiResponse<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> body,
                                                 Principal principal) {
        return ApiResponse.ok(Map.of(
                "echo", body == null ? Map.of() : body,
                "by", principal.getName()));
    }
}
