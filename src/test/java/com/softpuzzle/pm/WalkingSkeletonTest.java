package com.softpuzzle.pm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.softpuzzle.pm.account.Account;
import com.softpuzzle.pm.account.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 전 스택 통합 증명: 세션 인증(폼 로그인) + 보호 AJAX + CSRF + MyBatis 조회. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class WalkingSkeletonTest {

    private static final String EMAIL = "skeleton@example.com";
    private static final String RAW = "Passw0rd!";

    @Autowired
    MockMvc mvc;
    @Autowired
    AccountMapper accountMapper;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        if (accountMapper.findByEmail(EMAIL) == null) {
            Account a = new Account();
            a.setEmail(EMAIL);
            a.setName("스켈레톤");
            a.setTier("team");
            a.setJob("pm");
            a.setPasswordHash(passwordEncoder.encode(RAW));
            a.setStatus("active");
            accountMapper.insert(a);
        }
    }

    @Test
    void unauthenticated_api_redirectsToLogin() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void formLogin_succeeds_withSeededUser() throws Exception {
        mvc.perform(formLogin("/login").user(EMAIL).password(RAW))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void formLogin_fails_withWrongPassword() throws Exception {
        mvc.perform(formLogin("/login").user(EMAIL).password("wrong-password"))
                .andExpect(unauthenticated());
    }

    @Test
    void authenticated_me_returnsEnvelope() throws Exception {
        mvc.perform(get("/api/me").with(user(EMAIL).roles("TEAM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void echoPost_requiresCsrf() throws Exception {
        mvc.perform(post("/api/echo").with(user(EMAIL).roles("TEAM"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/echo").with(user(EMAIL).roles("TEAM")).with(csrf())
                        .contentType("application/json").content("{\"ping\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.by").value(EMAIL));
    }
}
