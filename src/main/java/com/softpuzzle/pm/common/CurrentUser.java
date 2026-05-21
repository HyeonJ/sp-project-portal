package com.softpuzzle.pm.common;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** SecurityContext의 인증 주체(email)를 도메인 Account로 해석. */
@Component
public class CurrentUser {

    private final AccountMapper accountMapper;

    public CurrentUser(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public Account require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw ApiException.forbidden("인증이 필요합니다.");
        }
        Account account = accountMapper.findByEmail(auth.getName());
        if (account == null) {
            throw ApiException.notFound("계정을 찾을 수 없습니다.");
        }
        return account;
    }
}
