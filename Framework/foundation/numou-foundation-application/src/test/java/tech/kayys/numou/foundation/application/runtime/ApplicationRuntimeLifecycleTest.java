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
}
