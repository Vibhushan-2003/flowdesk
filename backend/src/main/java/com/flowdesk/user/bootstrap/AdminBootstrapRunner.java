package com.flowdesk.user.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapProperties properties;
    private final AdminBootstrapService bootstrapService;
    private final ApplicationContext applicationContext;

    public AdminBootstrapRunner(
            AdminBootstrapProperties properties,
            AdminBootstrapService bootstrapService,
            ApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.bootstrapService = bootstrapService;
        this.applicationContext = applicationContext;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean requestedViaCli = AdminBootstrapActivation.isBootstrapRequested(args.getSourceArgs());
        boolean enabledViaProperty = properties.isEnabled();

        // If neither is true, bootstrap is completely dormant.
        // Normal web application startup continues uninterrupted.
        if (!requestedViaCli && !enabledViaProperty) {
            return;
        }

        log.info("Explicit FlowDesk initial administrator bootstrap requested (cli={}, property={}).",
                requestedViaCli, enabledViaProperty);

        try {
            if (requestedViaCli) {
                // Existing OCI CLI bootstrap behavior: one-shot command that terminates the JVM
                try {
                    bootstrapService.provisionFirstAdmin(properties);
                    log.info("Administrator provisioning completed successfully.");
                    log.info("Terminating application process after successful bootstrap (exit code 0).");
                    initiateExit(0);
                } catch (Exception e) {
                    log.error("Administrator bootstrap failed: {}", e.getMessage());
                    log.info("Terminating application process after bootstrap failure (exit code 1).");
                    initiateExit(1);
                }
            } else {
                // In-service web service mode (Render / long-running service):
                // Idempotent and does NOT terminate the running web application.
                if (bootstrapService.hasExistingUsers()) {
                    log.info("Database already contains existing user(s). Initial administrator bootstrap skipped; continuing web application startup.");
                } else {
                    try {
                        bootstrapService.provisionFirstAdmin(properties);
                        log.info("Administrator provisioning completed successfully via web service startup. Continuing web application startup.");
                    } catch (AdminBootstrapException e) {
                        log.error("In-service administrator bootstrap failed: {}", e.getMessage());
                        throw new IllegalStateException("Initial administrator bootstrap failed: " + e.getMessage(), e);
                    }
                }
            }
        } finally {
            clearCredentials();
        }
    }

    private void clearCredentials() {
        properties.setPassword(null);
        properties.setEnabled(false);
    }

    void initiateExit(int exitCode) {
        int code = SpringApplication.exit(applicationContext, (ExitCodeGenerator) () -> exitCode);
        terminateJvm(code);
    }

    void terminateJvm(int status) {
        System.exit(status);
    }
}
