package com.softpuzzle.pm.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.softpuzzle.pm.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AccountMapperIT {

    @Autowired
    AccountMapper accountMapper;

    @Test
    void insert_thenFindByEmailIgnoringCase_andFindById() {
        Account a = new Account();
        a.setEmail("it-user@example.com");
        a.setName("통합테스트");
        a.setTier("team");
        a.setJob("pm");
        a.setPasswordHash("hash");
        a.setStatus("active");

        accountMapper.insert(a);
        assertThat(a.getId()).isNotNull();

        Account byEmail = accountMapper.findByEmail("IT-User@Example.COM");
        assertThat(byEmail).isNotNull();
        assertThat(byEmail.getName()).isEqualTo("통합테스트");
        assertThat(byEmail.getTier()).isEqualTo("team");

        Account byId = accountMapper.findById(a.getId());
        assertThat(byId).isNotNull();
        assertThat(byId.getEmail()).isEqualTo("it-user@example.com");
    }
}
