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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        properties.setEmail("admin@flowdesk.test");
        properties.setPassword("StrongAdminPassword123!");
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
    @DisplayName("Should invoke bootstrap and trigger clean exit 0 on CLI option (OCI CLI workflow)")
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
        assertNull(properties.getPassword(), "In-memory password must be scrubbed");
        assertFalse(properties.isEnabled(), "In-memory enabled flag must be false");
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
        assertNull(properties.getPassword(), "In-memory password must be scrubbed");
        assertFalse(properties.isEnabled(), "In-memory enabled flag must be false");
    }

    @Test
    @DisplayName("Should provision admin and NOT exit when enabled via property on clean database (Render Web Service workflow)")
    void inServiceBootstrapProvisionsAdminAndDoesNotExitWhenDatabaseEmpty() {
        properties.setEnabled(true);
        when(bootstrapService.hasExistingUsers()).thenReturn(false);

        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        runner.run(args);

        verify(bootstrapService).provisionFirstAdmin(properties);
        assertFalse(runner.exitInitiated, "Exit must NOT be initiated on Web Service startup");
        assertNull(properties.getPassword(), "In-memory password must be scrubbed after provisioning");
        assertFalse(properties.isEnabled(), "In-memory enabled flag must be turned off");
    }

    @Test
    @DisplayName("Should be idempotent and skip bootstrap without exiting when users already exist (Render Web Service workflow)")
    void inServiceBootstrapIsIdempotentAndSkipsWhenUsersAlreadyExist() {
        properties.setEnabled(true);
        when(bootstrapService.hasExistingUsers()).thenReturn(true);

        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        runner.run(args);

        verify(bootstrapService, never()).provisionFirstAdmin(any());
        assertFalse(runner.exitInitiated, "Exit must NOT be initiated on Web Service startup");
        assertNull(properties.getPassword(), "In-memory password must be scrubbed");
        assertFalse(properties.isEnabled(), "In-memory enabled flag must be turned off");
    }

    @Test
    @DisplayName("Should fail fast with IllegalStateException without calling exit when in-service bootstrap fails")
    void inServiceBootstrapFailsFastOnInvalidCredentialsWithoutCallingExit() {
        properties.setEnabled(true);
        when(bootstrapService.hasExistingUsers()).thenReturn(false);
        doThrow(new AdminBootstrapException("Password too short"))
                .when(bootstrapService).provisionFirstAdmin(properties);

        TestableAdminBootstrapRunner runner = new TestableAdminBootstrapRunner(
                properties,
                bootstrapService,
                applicationContext
        );

        DefaultApplicationArguments args = new DefaultApplicationArguments(new String[]{});

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run(args)
        );

        assertTrue(ex.getMessage().contains("Password too short"));
        assertFalse(runner.exitInitiated, "System.exit must NOT be called in Web Service mode");
        assertNull(properties.getPassword(), "In-memory password must be scrubbed even on failure");
        assertFalse(properties.isEnabled(), "In-memory enabled flag must be turned off");
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
