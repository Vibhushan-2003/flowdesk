package com.flowdesk.user.bootstrap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private AdminBootstrapService bootstrapService;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    private AdminBootstrapProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AdminBootstrapProperties();
    }

    @Test
    @DisplayName("Should be completely dormant on normal application startup")
    void dormantOnNormalStartup() {
        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        runner.run(args);

        verify(bootstrapService, never()).provisionFirstAdmin(any());
        assertFalse(runner.exitInitiated, "Exit must not be initiated on normal startup");
    }

    @Test
    @DisplayName("Should invoke bootstrap and trigger clean exit 0 on CLI option")
    void invokeAndExitZeroOnCliOption() {
        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(
                new String[]{"--" + AdminBootstrapActivation.BOOTSTRAP_OPTION}
        );

        runner.run(args);

        verify(bootstrapService).provisionFirstAdmin(properties);
        assertTrue(runner.exitInitiated);
        assertEquals(0, runner.exitCode);
    }

    @Test
    @DisplayName("Should invoke bootstrap and trigger exit 1 on failure during CLI invocation")
    void failAndExitOneOnCliInvocation() {
        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        doThrow(new AdminBootstrapException("Validation failed"))
                .when(bootstrapService).provisionFirstAdmin(properties);

        DefaultApplicationArguments args = new DefaultApplicationArguments(
                new String[]{"--" + AdminBootstrapActivation.BOOTSTRAP_OPTION}
        );

        runner.run(args);

        verify(bootstrapService).provisionFirstAdmin(properties);
        assertTrue(runner.exitInitiated);
        assertEquals(1, runner.exitCode);
    }

    @Test
    @DisplayName("Should invoke bootstrap and exit 0 when app.bootstrap.admin.enabled is true")
    void invokeAndExitZeroWhenPropertyEnabled() {
        properties.setEnabled(true);

        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        runner.run(args);

        verify(bootstrapService).provisionFirstAdmin(properties);
        assertTrue(runner.exitInitiated);
        assertEquals(0, runner.exitCode);
    }

    @Test
    @DisplayName("Should invoke bootstrap and exit 1 when app.bootstrap.admin.enabled fails")
    void failAndExitOneWhenPropertyEnabled() {
        properties.setEnabled(true);

        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        doThrow(new AdminBootstrapException("Initial state check failed"))
                .when(bootstrapService).provisionFirstAdmin(properties);

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        runner.run(args);

        verify(bootstrapService).provisionFirstAdmin(properties);
        assertTrue(runner.exitInitiated);
        assertEquals(1, runner.exitCode);
    }

    private static class TestableAdminBootstrapRunner extends AdminBootstrapRunner {
        boolean exitInitiated = false;
        int exitCode = -1;

        TestableAdminBootstrapRunner(
                AdminBootstrapProperties properties,
                AdminBootstrapService bootstrapService,
                ConfigurableApplicationContext applicationContext
        ) {
            super(properties, bootstrapService, applicationContext);
        }

        @Override
        void initiateExit(int exitCode) {
            this.exitInitiated = true;
            this.exitCode = exitCode;
        }

        @Override
        void terminateJvm(int status) {
            // No-op in test to prevent stopping JUnit runner
        }
    }
}
