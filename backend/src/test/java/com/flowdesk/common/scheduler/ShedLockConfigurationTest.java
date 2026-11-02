package com.flowdesk.common.scheduler;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ShedLockConfigurationTest {

    @Autowired
    private LockProvider lockProvider;

    @Test
    @DisplayName("ShedLockConfig provides a PostgreSQL-backed JdbcTemplateLockProvider")
    void testLockProviderBeanConfigured() {
        assertThat(lockProvider).isNotNull();
        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }
}

