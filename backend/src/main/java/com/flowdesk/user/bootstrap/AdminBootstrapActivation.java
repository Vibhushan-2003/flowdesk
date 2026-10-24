package com.flowdesk.user.bootstrap;

import org.springframework.core.env.Environment;

import java.util.Arrays;

public final class AdminBootstrapActivation {

    public static final String BOOTSTRAP_OPTION = "flowdesk.bootstrap.admin";
    public static final String BOOTSTRAP_PROPERTY = "app.bootstrap.admin.enabled";
    public static final String BOOTSTRAP_ENV_VAR = "FLOWDESK_BOOTSTRAP_ADMIN_ENABLED";

    private AdminBootstrapActivation() {
    }

    /**
     * Determines whether bootstrap is requested from raw command line arguments.
     * Checks for:
     * 1. --flowdesk.bootstrap.admin
     * 2. --flowdesk.bootstrap.admin=true
     * 3. --app.bootstrap.admin.enabled=true
     */
    public static boolean isBootstrapRequested(String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String trimmed = arg.trim();
            if (trimmed.equals("--" + BOOTSTRAP_OPTION)
                    || trimmed.equalsIgnoreCase("--" + BOOTSTRAP_OPTION + "=true")
                    || trimmed.equalsIgnoreCase("--" + BOOTSTRAP_PROPERTY + "=true")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether bootstrap is requested from raw args OR environment variables.
     * Used at main() to set WebApplicationType.NONE before creating the application context.
     */
    public static boolean isBootstrapRequested(String[] args, Environment environment) {
        if (isBootstrapRequested(args)) {
            return true;
        }

        if (environment != null) {
            String propValue = environment.getProperty(BOOTSTRAP_PROPERTY);
            if (Boolean.parseBoolean(propValue)) {
                return true;
            }
            String envValue = environment.getProperty(BOOTSTRAP_ENV_VAR);
            if (Boolean.parseBoolean(envValue)) {
                return true;
            }
        }

        // Fallback to checking System env / properties directly before environment bean exists
        String sysProp = System.getProperty(BOOTSTRAP_PROPERTY);
        if (Boolean.parseBoolean(sysProp)) {
            return true;
        }
        String sysEnv = System.getenv(BOOTSTRAP_ENV_VAR);
        return Boolean.parseBoolean(sysEnv);
    }
}
