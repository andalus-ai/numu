package tech.kayys.numou.workspace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSchemaMigrationJobTest {
    private final Instant now = Instant.parse("2026-10-25T00:00:00Z");

    private DatabaseSchemaMigrationJob job(DatabaseSchemaMigrationJob.Status status, long total, long staged,
                                           String failureCode, Instant completedAt) {
        return new DatabaseSchemaMigrationJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 3, 4, "Tasks v2", "[]", status, 100,
                total, staged, null, 1, failureCode, null, now, now, completedAt);
    }

    @Test
    void readinessRequiresReadyStatusAndCompleteStaging() {
        assertTrue(job(DatabaseSchemaMigrationJob.Status.READY, 20, 20, null, null).readyToApply());
        assertFalse(job(DatabaseSchemaMigrationJob.Status.RUNNING, 20, 20, null, null).readyToApply());
        assertFalse(job(DatabaseSchemaMigrationJob.Status.READY, 20, 19, null, null).readyToApply());
    }

    @Test
    void terminalStatesAndAppliedCompletionAreExplicit() {
        assertTrue(job(DatabaseSchemaMigrationJob.Status.APPLIED, 0, 0, null, now).terminal());
        assertTrue(job(DatabaseSchemaMigrationJob.Status.FAILED, 2, 1, "TRANSFORM_INVALID", null).terminal());
        assertTrue(job(DatabaseSchemaMigrationJob.Status.CANCELLED, 2, 1, null, null).terminal());
        assertFalse(job(DatabaseSchemaMigrationJob.Status.RUNNING, 2, 1, null, null).terminal());
        assertThrows(IllegalArgumentException.class,
                () -> job(DatabaseSchemaMigrationJob.Status.APPLIED, 0, 0, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> job(DatabaseSchemaMigrationJob.Status.FAILED, 1, 0, null, null));
    }

    @Test
    void rejectsInvalidVersionProgressAndBatchBounds() {
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSchemaMigrationJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, 5, "Tasks", "[]", DatabaseSchemaMigrationJob.Status.PENDING, 10,
                0, 0, null, 1, null, null, now, now, null));
        assertThrows(IllegalArgumentException.class, () -> job(DatabaseSchemaMigrationJob.Status.PENDING, 1, 2, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DatabaseSchemaMigrationJob(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                3, 4, "Tasks", "[]", DatabaseSchemaMigrationJob.Status.PENDING, 501,
                0, 0, null, 1, null, null, now, now, null));
    }
}
