package tech.kayys.numou.warehouse.application;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseRecoveryAuditExportWorkerCrashWindowTest {
    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");

    @Test
    void retriesManifestPersistenceFailureWithoutAppendingArtifactTwice() throws Exception {
        InMemoryJobs jobs = new InMemoryJobs(requestedJob());
        InMemoryArtifacts artifacts = new InMemoryArtifacts();
        FailOnceManifests manifests = new FailOnceManifests();
        InMemoryAccess access = new InMemoryAccess();
        WarehouseRecoveryAuditExportWorker worker = worker(jobs, artifacts, manifests, access);
        assertThrows(WarehouseRecoveryAuditPersistenceException.class, () -> worker.runOne("worker-a"));
        assertEquals(WarehouseRecoveryAuditExportJob.Status.VERIFYING, jobs.current.status());
        assertEquals(1, artifacts.appendCalls);
        assertEquals(0, manifests.saved.size());
        var recovered = worker.runOne("worker-b").orElseThrow();
        assertEquals(WarehouseRecoveryAuditExportJob.Status.COMPLETED, recovered.status());
        assertEquals(1, artifacts.appendCalls, "retry must verify existing bytes instead of appending twice");
        assertEquals(1, manifests.saved.size());
        assertTrue(access.events.stream().anyMatch(e -> e.action() == WarehouseRecoveryAuditExportAccessEvent.Action.VERIFY
                && e.outcome() == WarehouseRecoveryAuditExportAccessEvent.Outcome.SUCCESS));
    }

    @Test
    void completionPersistenceFailureCanBeRecoveredFromSavedManifest() throws Exception {
        InMemoryJobs jobs = new InMemoryJobs(requestedJob());
        jobs.failNextUpdateForStatus = WarehouseRecoveryAuditExportJob.Status.COMPLETED;
        InMemoryArtifacts artifacts = new InMemoryArtifacts();
        InMemoryManifests manifests = new InMemoryManifests();
        WarehouseRecoveryAuditExportWorker worker = worker(jobs, artifacts, manifests, new InMemoryAccess());
        assertThrows(WarehouseRecoveryAuditPersistenceException.class, () -> worker.runOne("worker-a"));
        assertEquals(WarehouseRecoveryAuditExportJob.Status.VERIFYING, jobs.current.status());
        assertEquals(1, manifests.saved.size());
        var recovered = worker.runOne("worker-b").orElseThrow();
        assertEquals(WarehouseRecoveryAuditExportJob.Status.COMPLETED, recovered.status());
        assertEquals(1, artifacts.appendCalls);
        assertEquals(1, manifests.saved.size(), "retry must reuse the already committed manifest");
    }

    private static WarehouseRecoveryAuditExportWorker worker(InMemoryJobs jobs, InMemoryArtifacts artifacts,
            InMemoryManifests manifests, InMemoryAccess access) {
        return worker(jobs, artifacts, manifests, access, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static WarehouseRecoveryAuditExportWorker worker(InMemoryJobs jobs, InMemoryArtifacts artifacts,
            InMemoryManifests manifests, InMemoryAccess access, Clock clock) {
        WarehouseExecutionAttemptRepository repository = (WarehouseExecutionAttemptRepository)
                java.lang.reflect.Proxy.newProxyInstance(WarehouseExecutionAttemptRepository.class.getClassLoader(),
                new Class<?>[]{WarehouseExecutionAttemptRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("pageOperatorAuditEvents")) return new WarehouseRecoveryAuditPage(List.of(), null);
                    if (method.getName().equals("toString")) return "AuditPageTestRepository";
                    throw new UnsupportedOperationException(method.getName());
                });
        return new WarehouseRecoveryAuditExportWorker(new WarehouseRecoveryOperationsService(repository),
                jobs, access, manifests, artifacts, new WarehouseRecoveryAuditArchiveVerifier(),
                clock, Duration.ofMinutes(2), 100);
    }


    @Test
    void retriesDurableChunkWhenCheckpointPersistenceFails() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        InMemoryJobs jobs = new InMemoryJobs(requestedJob());
        jobs.failNextCheckpointUpdate = true;
        InMemoryArtifacts artifacts = new InMemoryArtifacts();
        WarehouseRecoveryAuditExportWorker worker = worker(jobs, artifacts,
                new InMemoryManifests(), new InMemoryAccess(), clock);

        assertThrows(WarehouseRecoveryAuditPersistenceException.class, () -> worker.runOne("worker-a"));
        assertEquals(WarehouseRecoveryAuditExportJob.Status.RUNNING, jobs.current.status());
        assertEquals(0, jobs.current.exportedByteCount(), "failed checkpoint must not advance durable progress");
        assertEquals(1, artifacts.physicalWrites, "first attempt durably writes the chunk once");

        // Simulate lease expiry and a fresh worker process claiming the unchanged checkpoint.
        clock.advance(Duration.ofMinutes(3));
        var recovered = worker.runOne("worker-b").orElseThrow();

        assertEquals(WarehouseRecoveryAuditExportJob.Status.COMPLETED, recovered.status());
        assertTrue(recovered.exportedByteCount() > 0);
        assertEquals(1, artifacts.physicalWrites, "retry must recognize durable bytes, not append them twice");
        assertEquals(1, artifacts.receiptCount, "recovery should leave one receipt for the retried logical chunk");
    }

    private static WarehouseRecoveryAuditExportJob requestedJob() {
        return new WarehouseRecoveryAuditExportJob("export-crash-test", "tenant-crash-test", "operator-test",
                WarehouseRecoveryAuditExportJob.Status.REQUESTED, null, 0, 0, null, null,
                NOW, NOW, null, null, 0);
    }

    private static final class InMemoryJobs implements WarehouseRecoveryAuditExportJobRepository {
        private WarehouseRecoveryAuditExportJob current;
        private WarehouseRecoveryAuditExportJob.Status failNextUpdateForStatus;\n        private boolean failNextCheckpointUpdate;
        private InMemoryJobs(WarehouseRecoveryAuditExportJob initial) { current = initial; }
        @Override public void insert(WarehouseRecoveryAuditExportJob job) { current = job; }
        @Override public Optional<WarehouseRecoveryAuditExportJob> find(String tenantId, String exportId) {
            return current.tenantId().equals(tenantId) && current.exportId().equals(exportId) ? Optional.of(current) : Optional.empty();
        }
        @Override public boolean update(WarehouseRecoveryAuditExportJob job, long expectedVersion) {
            if (current.version() != expectedVersion) return false;
            if (failNextCheckpointUpdate && job.status() == WarehouseRecoveryAuditExportJob.Status.RUNNING
                    && job.exportedByteCount() > current.exportedByteCount()) {
                failNextCheckpointUpdate = false;
                throw new WarehouseRecoveryAuditPersistenceException("Injected checkpoint outage",
                        new java.sql.SQLException("injected"));
            }
            if (failNextUpdateForStatus == job.status()) {
                failNextUpdateForStatus = null;
                throw new WarehouseRecoveryAuditPersistenceException("Injected completion outage", new java.sql.SQLException("injected"));
            }
            current = job;
            return true;
        }
        @Override public Optional<WarehouseRecoveryAuditExportJob> claimNext(String workerId, Instant now, Instant leaseUntil) {
            if (current.status() != WarehouseRecoveryAuditExportJob.Status.REQUESTED
                    && current.status() != WarehouseRecoveryAuditExportJob.Status.VERIFYING
                    && !(current.status() == WarehouseRecoveryAuditExportJob.Status.RUNNING
                    && current.leaseUntil() != null && !current.leaseUntil().isAfter(now))) return Optional.empty();
            current = current.claim(workerId, now, leaseUntil);
            return Optional.of(current);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    private static final class InMemoryArtifacts implements WarehouseRecoveryAuditArtifactStore {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int appendCalls;
        private int physicalWrites;
        private int receiptCount;
        private String lastChunkId;
        @Override public ArtifactSnapshot appendChunk(String tenantId, String exportId, String chunkId, long expectedOffset, byte[] chunk) throws IOException {
            if (lastChunkId != null && lastChunkId.equals(chunkId)) return snapshot();
            if (expectedOffset > bytes.size()) throw new IOException("unexpected offset");
            byte[] currentBytes = bytes.toByteArray();
            if (currentBytes.length >= expectedOffset + chunk.length) {
                for (int i = 0; i < chunk.length; i++) {
                    if (currentBytes[Math.toIntExact(expectedOffset) + i] != chunk[i])
                        throw new IOException("durable bytes conflict with retried chunk");
                }
                lastChunkId = chunkId;
                receiptCount++;
                return snapshot();
            }
            if (expectedOffset != bytes.size()) throw new IOException("unexpected offset");
            bytes.write(chunk); lastChunkId = chunkId; appendCalls++; physicalWrites++; receiptCount++;
            return snapshot();
        }
        @Override public ArtifactSnapshot inspect(String tenantId, String exportId) throws IOException { return snapshot(); }
        @Override public InputStream open(String tenantId, String exportId) { return new ByteArrayInputStream(bytes.toByteArray()); }
        private ArtifactSnapshot snapshot() throws IOException {
            try {
                byte[] content = bytes.toByteArray();
                return new ArtifactSnapshot("memory://tenant/export.csv", content.length,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
    }

    private static class InMemoryManifests implements WarehouseRecoveryAuditArchiveManifestRepository {
        private final List<WarehouseRecoveryAuditArchiveManifest> saved = new ArrayList<>();
        @Override public void insert(WarehouseRecoveryAuditArchiveManifest manifest) {
            if (saved.stream().noneMatch(m -> m.tenantId().equals(manifest.tenantId()) && m.exportId().equals(manifest.exportId()))) saved.add(manifest);
        }
        @Override public Optional<WarehouseRecoveryAuditArchiveManifest> find(String tenantId, String exportId) {
            return saved.stream().filter(m -> m.tenantId().equals(tenantId) && m.exportId().equals(exportId)).findFirst();
        }
    }

    private static final class FailOnceManifests extends InMemoryManifests {
        private final AtomicBoolean fail = new AtomicBoolean(true);
        @Override public void insert(WarehouseRecoveryAuditArchiveManifest manifest) {
            if (fail.compareAndSet(true, false)) throw new WarehouseRecoveryAuditPersistenceException(
                    "Injected manifest outage", new java.sql.SQLException("injected"));
            super.insert(manifest);
        }
    }

    private static final class InMemoryAccess implements WarehouseRecoveryAuditExportAccessRepository {
        private final List<WarehouseRecoveryAuditExportAccessEvent> events = new ArrayList<>();
        @Override public void append(WarehouseRecoveryAuditExportAccessEvent event) { events.add(event); }
    }
}
