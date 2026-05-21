package com.softpuzzle.pm.account;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 이메일(=username)로 계정을 로드. 권한 = ROLE_<TIER>. */
@Service
public class PmUserDetailsService implements UserDetailsService {

    private final AccountMapper accountMapper;

    public PmUserDetailsService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Account account = accountMapper.findByEmail(email);
        if (account == null || account.getPasswordHash() == null) {
            throw new UsernameNotFoundException("계정을 찾을 수 없습니다: " + email);
        }
        boolean active = "active".equals(account.getStatus());
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + account.getTier().toUpperCase())))
                .disabled(!active)
                .build();
    }
}
