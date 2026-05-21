package com.softpuzzle.pm.common;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** dev 시드 계정 (local/dev 프로필만). 멱등 — 이미 있으면 건너뜀. */
@Component
@Profile({"local", "dev"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_PASSWORD = "Passw0rd!";

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AccountMapper accountMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("admin@softpuzzle.com", "관리자", "admin", null);
        seed("pm@softpuzzle.com", "박PM", "team", "pm");
        seed("client@acme.com", "고객담당", "client", null);
        log.info("[DataSeeder] 시드 계정 준비 완료 (비밀번호: {})", SEED_PASSWORD);
    }

    private void seed(String email, String name, String tier, String job) {
        if (accountMapper.findByEmail(email) != null) {
            return;
        }
        Account a = new Account();
        a.setEmail(email);
        a.setName(name);
        a.setTier(tier);
        a.setJob(job);
        a.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
        a.setStatus("active");
        accountMapper.insert(a);
        log.info("[DataSeeder] 계정 생성 email={} tier={}", email, tier);
    }
}
