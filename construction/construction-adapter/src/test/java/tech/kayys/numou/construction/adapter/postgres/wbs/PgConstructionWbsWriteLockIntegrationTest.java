package tech.kayys.numou.construction.adapter.postgres.wbs;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.pgclient.PgPool;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.scope.adapter.postgres.PgAdvisoryBreakdownStructureWriteLock;
import tech.kayys.numou.scope.domain.BreakdownStructureId;

import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database-backed proof that independently-created lock adapters serialize
 * mutations for one structure across PostgreSQL sessions.
 *
 * Run with the construction adapter's -Ppg-integration profile and a migrated,
 * reachable PostgreSQL test database.
 */
@QuarkusTest
class PgConstructionWbsWriteLockIntegrationTest {
    @Inject
    PgPool pool;

    @Test
    void serializesSameStructureAcrossIndependentAdapterInstances() throws Exception {
        var structureId = new BreakdownStructureId(UUID.randomUUID());
        var firstLock = new PgAdvisoryBreakdownStructureWriteLock(pool);
        var secondLock = new PgAdvisoryBreakdownStructureWriteLock(pool);
        var firstEntered = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var releaseFirst = new CompletableFuture<Void>();

        var first = firstLock.withLock(structureId, () -> {
            firstEntered.countDown();
            return releaseFirst.thenApply(ignored -> "first");
        }).toCompletableFuture();

        assertTrue(firstEntered.await(5, TimeUnit.SECONDS),
                "first operation should acquire the advisory lock");

        var second = secondLock.withLock(structureId, () -> {
            secondEntered.countDown();
            return CompletableFuture.completedFuture("second");
        }).toCompletableFuture();

        try {
            assertFalse(secondEntered.await(250, TimeUnit.MILLISECONDS),
                    "second operation must wait while the first owns the structure lock");
        } finally {
            releaseFirst.complete(null);
        }

        assertEquals("first", first.get(5, TimeUnit.SECONDS));
        assertEquals("second", second.get(5, TimeUnit.SECONDS));
        assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
    }

    @Test
    void allowsWritesToDifferentStructuresToProceedIndependently() throws Exception {
        var firstLock = new PgAdvisoryBreakdownStructureWriteLock(pool);
        var secondLock = new PgAdvisoryBreakdownStructureWriteLock(pool);
        var firstStructure = new BreakdownStructureId(UUID.randomUUID());
        var secondStructure = new BreakdownStructureId(UUID.randomUUID());
        var firstEntered = new CountDownLatch(1);
        var releaseFirst = new CompletableFuture<Void>();

        var first = firstLock.withLock(firstStructure, () -> {
            firstEntered.countDown();
            return releaseFirst.thenApply(ignored -> "first");
        }).toCompletableFuture();

        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
        try {
            var second = secondLock.withLock(secondStructure,
                    () -> CompletableFuture.completedFuture("second")).toCompletableFuture();
            assertEquals("second", second.get(2, TimeUnit.SECONDS),
                    "unrelated structures must not block one another");
        } finally {
            releaseFirst.complete(null);
        }
        assertEquals("first", first.get(5, TimeUnit.SECONDS));
    }
}
