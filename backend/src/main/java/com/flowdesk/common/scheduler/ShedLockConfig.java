package com.flowdesk.common.scheduler;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Multi-Instance Distributed Scheduler Locking Configuration.
 * <p>
 * Ensures that in a multi-instance FlowDesk deployment, scheduled tasks (such as SLA breach
 * monitoring and token/invitation cleanup) execute exclusively on exactly one instance at a time.
 * Uses PostgreSQL-backed lock synchronization via the 'shedlock' table with database server time
 * to prevent clock skew discrepancies across instances.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class ShedLockConfig {

    private static final Logger log = LoggerFactory.getLogger(ShedLockConfig.class);

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        log.info("Configuring PostgreSQL-backed distributed LockProvider (ShedLock)");
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}

