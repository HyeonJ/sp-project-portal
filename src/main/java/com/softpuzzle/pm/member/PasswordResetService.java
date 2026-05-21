package com.softpuzzle.pm.member;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import com.softpuzzle.pm.common.ApiException;
import com.softpuzzle.pm.common.Tokens;
import java.time.OffsetDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호 재설정 — 단명 토큰. 이메일 미발송 환경이라 링크를 응답으로 반환(MVP). */
@Service
public class PasswordResetService {

    private final AccountMapper accountMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(AccountMapper accountMapper, PasswordResetTokenMapper tokenMapper,
                                PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.tokenMapper = tokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 토큰 발급 → 재설정 링크 반환(계정 없으면 null — 호출부는 노출 여부 결정). */
    @Transactional
    public String requestReset(String email) {
        Account account = accountMapper.findByEmail(email.trim());
        if (account == null) {
            return null;
        }
        String raw = Tokens.generate();
        PasswordResetToken token = new PasswordResetToken();
        token.setAccountId(account.getId());
        token.setTokenHash(Tokens.hash(raw));
        token.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        tokenMapper.insert(token);
        return "/password/reset/confirm?token=" + raw;
    }

    @Transactional
    public void reset(String rawToken, String password) {
        PasswordResetToken token = tokenMapper.findByTokenHash(Tokens.hash(rawToken));
        if (token == null || token.getUsedAt() != null) {
            throw ApiException.conflict("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
        }
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw ApiException.conflict("EXPIRED", "만료된 토큰입니다.");
        }
        if (password == null || password.length() < 8
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw ApiException.conflict("WEAK_PASSWORD", "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.");
        }
        accountMapper.updatePasswordAndActivate(token.getAccountId(), passwordEncoder.encode(password));
        tokenMapper.markUsed(token.getId());
    }
}
