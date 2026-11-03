package com.flowdesk;

import com.flowdesk.user.bootstrap.AdminBootstrapActivation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

    private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackendApplication.class);

        // If bootstrap is explicitly requested via CLI arguments (e.g. OCI one-shot container),
        // configure WebApplicationType.NONE so no embedded HTTP or WebSocket server is started.
        if (AdminBootstrapActivation.isBootstrapRequested(args)) {
            log.info("Starting FlowDesk in non-web bootstrap mode (WebApplicationType.NONE).");
            app.setWebApplicationType(WebApplicationType.NONE);
        }

        app.run(args);
    }
}
