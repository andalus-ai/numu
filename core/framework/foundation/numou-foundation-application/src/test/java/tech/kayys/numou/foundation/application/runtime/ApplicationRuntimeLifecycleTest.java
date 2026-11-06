package tech.kayys.numou.foundation.application.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.availability.LivenessStatus;
import tech.kayys.numou.foundation.application.availability.ReadinessStatus;
import tech.kayys.numou.foundation.application.startup.StartupCheck;
import tech.kayys.numou.foundation.application.startup.StartupCheckPhase;
import tech.kayys.numou.foundation.application.startup.StartupCheckResult;
import tech.kayys.numou.foundation.application.startup.StartupCheckSeverity;
import tech.kayys.numou.foundation.application.startup.StartupValidationContext;
import tech.kayys.numou.foundation.application.startup.StartupValidationException;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Application Runtime Lifecycle Tests")
class ApplicationRuntimeLifecycleTest {

    @Test
    @DisplayName("Should successfully start, run, and stop runtime")
    void shouldSuccessfullyStartAndStop() {
        AtomicBoolean componentStarted = new AtomicBoolean(false);
        AtomicBoolean componentStopped = new AtomicBoolean(false);

        RuntimeComponent testComponent = new RuntimeComponent() {
            @Override
            public String id() { return "test-comp"; }
            @Override
            public Uni<Void> start() {
                componentStarted.set(true);
                return Uni.createFrom().voidItem();
            }
            @Override
            public Uni<Void> stop() {
                componentStopped.set(true);
                return Uni.createFrom().voidItem();
            }
        };

        ApplicationRuntime runtime = ApplicationRuntimeBuilder.create()
                .component(testComponent)
                .build();

        assertEquals(ApplicationRuntimeState.NEW, runtime.state());
        assertFalse(runtime.isRunning());

        runtime.start().await().indefinitely();

        assertEquals(ApplicationRuntimeState.RUNNING, runtime.state());
        assertTrue(runtime.isRunning());
        assertTrue(componentStarted.get());

        // Check availability
        DefaultApplicationRuntime defaultRuntime = (DefaultApplicationRuntime) runtime;
        assertEquals(LivenessStatus.ALIVE, defaultRuntime.livenessStatus());
        assertEquals(ReadinessStatus.READY, defaultRuntime.readinessStatus());
        assertTrue(defaultRuntime.isReady());

        runtime.stop().await().indefinitely();

        assertEquals(ApplicationRuntimeState.STOPPED, runtime.state());
        assertFalse(runtime.isRunning());
        assertTrue(componentStopped.get());
        assertEquals(ReadinessStatus.NOT_READY, defaultRuntime.readinessStatus());
    }

    @Test
    @DisplayName("Should abort startup and transition to FAILED if required check fails")
    void shouldFailStartupWhenCheckFails() {
        StartupCheck failingCheck = new StartupCheck() {
            @Override
            public String id() { return "fail-check"; }
            @Override
            public StartupCheckPhase phase() { return StartupCheckPhase.INFRASTRUCTURE; }
            @Override
            public StartupCheckResult check(StartupValidationContext context) {
                return StartupCheckResult.failed("fail-check", StartupCheckSeverity.REQUIRED, "Database connection missing");
            }
        };

        ApplicationRuntime runtime = ApplicationRuntimeBuilder.create()
                .startupCheck(failingCheck)
                .build();

        StartupValidationException ex = assertThrows(StartupValidationException.class, () ->
                runtime.start().await().indefinitely());
        assertTrue(ex.getMessage().contains("Database connection missing"));

        assertEquals(ApplicationRuntimeState.FAILED, runtime.state());
        DefaultApplicationRuntime defaultRuntime = (DefaultApplicationRuntime) runtime;
        assertEquals(LivenessStatus.DEAD, defaultRuntime.livenessStatus());
        assertEquals(ReadinessStatus.NOT_READY, defaultRuntime.readinessStatus());
        assertFalse(defaultRuntime.reasons().isEmpty());
    }

    @Test
    @DisplayName("Runtime health follows the application lifecycle")
    void runtimeHealthFollowsLifecycle() {
        ApplicationRuntime runtime = ApplicationRuntimeBuilder.create().build();

        assertFalse(runtime.registry().health().isHealthy());
        assertEquals("DOWN", runtime.registry().health().status());

        runtime.start().await().indefinitely();
        assertTrue(runtime.registry().health().isHealthy());
        assertEquals("UP", runtime.registry().health().status());

        runtime.stop().await().indefinitely();
        assertFalse(runtime.registry().health().isHealthy());
        assertEquals("DOWN", runtime.registry().health().status());
    }

    @Test
    @DisplayName("Duplicate component and module IDs are rejected")
    void duplicateCompositionIdsAreRejected() {
        RuntimeComponent first = component("duplicate", Uni.createFrom().voidItem(), Uni.createFrom().voidItem());
        RuntimeComponent second = component("duplicate", Uni.createFrom().voidItem(), Uni.createFrom().voidItem());

        assertThrows(IllegalStateException.class, () -> ApplicationRuntimeBuilder.create()
                .component(first)
                .component(second)
                .build());

        ApplicationModule firstModule = () -> "duplicate-module";
        ApplicationModule secondModule = () -> "duplicate-module";
        assertThrows(IllegalStateException.class, () -> ApplicationRuntimeBuilder.create()
                .module(firstModule)
                .module(secondModule)
                .build());
    }

    @Test
    @DisplayName("Startup rollback attempts cleanup of a partially started component and records failures")
    void startupRollbackRecordsCleanupFailures() {
        RuntimeComponent partiallyStarted = component(
                "partial",
                Uni.createFrom().voidItem(),
                Uni.createFrom().failure(new IllegalStateException("sensitive cleanup detail"))
        );
        RuntimeComponent failing = component(
                "failing",
                Uni.createFrom().failure(new IllegalStateException("startup failed")),
                Uni.createFrom().voidItem()
        );

        DefaultApplicationRuntime runtime = (DefaultApplicationRuntime) ApplicationRuntimeBuilder.create()
                .component(partiallyStarted)
                .component(failing)
                .build();

        assertThrows(RuntimeException.class, () -> runtime.start().await().indefinitely());
        assertEquals(ApplicationRuntimeState.FAILED, runtime.state());
        assertTrue(runtime.reasons().stream().anyMatch(reason ->
                reason.code().equals("STARTUP_ROLLBACK_FAILURE")));
        assertTrue(runtime.reasons().stream().noneMatch(reason ->
                reason.message().contains("sensitive cleanup detail")));
    }

    @Test
    @DisplayName("Shutdown continues cleanup and fails visibly when a component cannot stop")
    void shutdownCleanupFailuresAreVisible() {
        RuntimeComponent failingStop = component(
                "failing-stop",
                Uni.createFrom().voidItem(),
                Uni.createFrom().failure(new IllegalStateException("sensitive shutdown detail"))
        );
        DefaultApplicationRuntime runtime = (DefaultApplicationRuntime) ApplicationRuntimeBuilder.create()
                .component(failingStop)
                .build();

        runtime.start().await().indefinitely();
        assertThrows(ApplicationRuntimeException.class, () -> runtime.stop().await().indefinitely());

        assertEquals(ApplicationRuntimeState.FAILED, runtime.state());
        assertTrue(runtime.reasons().stream().anyMatch(reason ->
                reason.code().equals("SHUTDOWN_FAILURE")));
        assertTrue(runtime.reasons().stream().noneMatch(reason ->
                reason.message().contains("sensitive shutdown detail")));
    }

    private static RuntimeComponent component(String id, Uni<Void> start, Uni<Void> stop) {
        return new RuntimeComponent() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Uni<Void> start() {
                return start;
            }

            @Override
            public Uni<Void> stop() {
                return stop;
            }
        };
    }
}
