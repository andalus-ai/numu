package tech.kayys.numou.warehouse.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Processes one bounded audit page per call and checkpoints only after durable chunk writes. */
public final class WarehouseRecoveryAuditExportWorker {
    private static final String HEADER = "tenant_id,command_key,action,actor_id,reason,prior_version,resulting_version,correlation_id,occurred_at\n";
    private final WarehouseRecoveryOperationsService operations;
    private final WarehouseRecoveryAuditExportJobRepository jobs;
    private final WarehouseRecoveryAuditExportAccessRepository access;
    private final WarehouseRecoveryAuditArchiveManifestRepository manifests;
    private final WarehouseRecoveryAuditArtifactStore artifacts;
    private final WarehouseRecoveryAuditArchiveVerifier verifier;
    private final Clock clock;
    private final Duration lease;
    private final int pageSize;

    public WarehouseRecoveryAuditExportWorker(WarehouseRecoveryOperationsService operations,
            WarehouseRecoveryAuditExportJobRepository jobs, WarehouseRecoveryAuditExportAccessRepository access,
            WarehouseRecoveryAuditArchiveManifestRepository manifests, WarehouseRecoveryAuditArtifactStore artifacts,
            WarehouseRecoveryAuditArchiveVerifier verifier, Clock clock, Duration lease, int pageSize) {
        this.operations=Objects.requireNonNull(operations,"operations");
        this.jobs=Objects.requireNonNull(jobs,"jobs");
        this.access=Objects.requireNonNull(access,"access");
        this.manifests=Objects.requireNonNull(manifests,"manifests");
        this.artifacts=Objects.requireNonNull(artifacts,"artifacts");
        this.verifier=Objects.requireNonNull(verifier,"verifier");
        this.clock=Objects.requireNonNull(clock,"clock");
        if(lease==null||lease.isZero()||lease.isNegative()||lease.compareTo(Duration.ofHours(1))>0)
            throw new IllegalArgumentException("lease must be positive and no longer than one hour");
        if(pageSize<1||pageSize>500) throw new IllegalArgumentException("pageSize must be between 1 and 500");
        this.lease=lease; this.pageSize=pageSize;
    }

    /** Claims and advances at most one job page. Empty means no eligible job was available. */
    public Optional<WarehouseRecoveryAuditExportJob> runOne(String workerId) throws IOException {
        Instant now=clock.instant();
        Optional<WarehouseRecoveryAuditExportJob> claimed=jobs.claimNext(workerId,now,now.plus(lease));
        if(claimed.isEmpty()) return Optional.empty();
        WarehouseRecoveryAuditExportJob job=claimed.get();
        record(job,workerId,job.exportedEventCount()==0&&job.continuationCursor()==null
                ?WarehouseRecoveryAuditExportAccessEvent.Action.CLAIM
                :WarehouseRecoveryAuditExportAccessEvent.Action.RESUME,
                WarehouseRecoveryAuditExportAccessEvent.Outcome.SUCCESS,null);
        try {
            // A null cursor with nonzero bytes means the final page was checkpointed already.
            // This also resumes a crash during verification without appending the first page twice.
            if(job.continuationCursor()==null && job.exportedByteCount()>0)
                return Optional.of(verifyAndComplete(job,workerId));
            WarehouseRecoveryAuditPage page=operations.auditPage(job.tenantId(),pageSize,job.continuationCursor());
            if(page.events().size()>pageSize) throw new IllegalStateException("Audit repository exceeded requested page size");
            for(WarehouseRecoveryOperatorAuditEvent event:page.events()) {
                if(!job.tenantId().equals(event.tenantId()))
                    throw new IllegalStateException("Audit repository returned an event outside the job tenant");
            }
            if(page.hasMore()&&page.events().isEmpty())
                throw new IllegalStateException("Audit pagination returned an empty page with a continuation cursor");
            if(page.hasMore()&&page.nextCursor().equals(job.continuationCursor()))
                throw new IllegalStateException("Audit pagination cursor did not advance");

            byte[] chunk=encode(page.events(),job.exportedEventCount()==0&&job.exportedByteCount()==0);
            String chunkId=job.exportId()+":"+job.exportedByteCount();
            WarehouseRecoveryAuditArtifactStore.ArtifactSnapshot written=artifacts.appendChunk(
                    job.tenantId(),job.exportId(),chunkId,job.exportedByteCount(),chunk);
            if(written.byteSize()!=job.exportedByteCount()+chunk.length)
                throw new IllegalStateException("Artifact store byte offset does not match the durable chunk");
            Instant checkpointAt=clock.instant();
            WarehouseRecoveryAuditExportJob checkpoint=job.checkpoint(workerId,page.nextCursor(),
                    Math.addExact(job.exportedEventCount(),page.events().size()),written.byteSize(),
                    checkpointAt,checkpointAt.plus(lease));
            if(!jobs.update(checkpoint,job.version()))
                throw new IllegalStateException("Export checkpoint lost optimistic version race");
            if(page.nextCursor()!=null) {
                WarehouseRecoveryAuditExportJob yielded=checkpoint.yieldForResume(workerId,clock.instant());
                if(!jobs.update(yielded,checkpoint.version()))
                    throw new IllegalStateException("Export yield lost optimistic version race");
                return Optional.of(yielded);
            }
            return Optional.of(verifyAndComplete(checkpoint,workerId));
        } catch(IOException|RuntimeException e) {
            // I/O and optimistic-lock failures may be transient. Leave the live lease to expire so
            // another worker can retry the same idempotent chunk from the last durable checkpoint.
            boolean transientFailure=isTransientFailure(e);
            if(!transientFailure) {
                try {
                    WarehouseRecoveryAuditExportJob latest=jobs.find(job.tenantId(),job.exportId()).orElse(job);
                    if(latest.status()!=WarehouseRecoveryAuditExportJob.Status.COMPLETED
                            &&latest.status()!=WarehouseRecoveryAuditExportJob.Status.FAILED) {
                        WarehouseRecoveryAuditExportJob failed=latest.fail(clock.instant(),failureCode(e));
                        jobs.update(failed,latest.version());
                        record(latest,workerId,job.exportedEventCount()==0&&job.continuationCursor()==null
                                        ?WarehouseRecoveryAuditExportAccessEvent.Action.CLAIM
                                        :WarehouseRecoveryAuditExportAccessEvent.Action.RESUME,
                                WarehouseRecoveryAuditExportAccessEvent.Outcome.FAILURE,failureCode(e));
                    }
                } catch (WarehouseRecoveryAuditPersistenceException persistenceFailure) {
                    // Preserve the original processing error and leave recovery to lease expiry when
                    // the database is unavailable while trying to record a terminal failure.
                    e.addSuppressed(persistenceFailure);
                }
            }
            throw e;
        }
    }

    private WarehouseRecoveryAuditExportJob verifyAndComplete(WarehouseRecoveryAuditExportJob checkpoint,
                                                               String workerId) throws IOException {
        Instant now=clock.instant();
        WarehouseRecoveryAuditArtifactStore.ArtifactSnapshot snapshot=artifacts.inspect(
                checkpoint.tenantId(),checkpoint.exportId());
        if(snapshot.byteSize()!=checkpoint.exportedByteCount())
            throw new IllegalStateException("Final artifact size differs from durable checkpoint");
        WarehouseRecoveryAuditExportJob verifying=checkpoint.beginVerification(workerId,now,snapshot.location());
        if(!jobs.update(verifying,checkpoint.version()))
            throw new IllegalStateException("Verification transition lost optimistic version race");
        WarehouseRecoveryAuditArchiveManifest manifest=new WarehouseRecoveryAuditArchiveManifest(
                verifying.exportId(),verifying.tenantId(),snapshot.location(),snapshot.sha256Hex(),
                snapshot.byteSize(),verifying.exportedEventCount(),now,verifying.requestedBy());
        WarehouseRecoveryAuditArchiveVerifier.VerificationResult result;
        try(InputStream input=artifacts.open(verifying.tenantId(),verifying.exportId())) {
            result=verifier.verify(manifest,input);
        }
        if(!result.verified()) {
            WarehouseRecoveryAuditExportJob failed=verifying.fail(clock.instant(),"ARCHIVE_CHECKSUM_MISMATCH");
            jobs.update(failed,verifying.version());
            record(verifying,workerId,WarehouseRecoveryAuditExportAccessEvent.Action.VERIFY_FAILED,
                    WarehouseRecoveryAuditExportAccessEvent.Outcome.FAILURE,"ARCHIVE_CHECKSUM_MISMATCH");
            throw new IllegalStateException("Recovery audit archive failed integrity verification");
        }
        Optional<WarehouseRecoveryAuditArchiveManifest> existing=manifests.find(verifying.tenantId(),verifying.exportId());
        if(existing.isPresent()) {
            WarehouseRecoveryAuditArchiveManifest saved=existing.get();
            if(!saved.tenantId().equals(manifest.tenantId())
                    ||!saved.exportId().equals(manifest.exportId())
                    ||!saved.artifactLocation().equals(manifest.artifactLocation())
                    ||!saved.sha256Hex().equals(manifest.sha256Hex())
                    ||saved.byteSize()!=manifest.byteSize()
                    ||saved.eventCount()!=manifest.eventCount()
                    ||!saved.createdBy().equals(manifest.createdBy()))
                throw new IllegalStateException("Existing archive manifest conflicts with verified artifact");
        } else {
            manifests.insert(manifest);
        }
        WarehouseRecoveryAuditExportJob completed=verifying.complete(clock.instant());
        if(!jobs.update(completed,verifying.version()))
            throw new IllegalStateException("Completion transition lost optimistic version race");
        record(completed,workerId,WarehouseRecoveryAuditExportAccessEvent.Action.VERIFY,
                WarehouseRecoveryAuditExportAccessEvent.Outcome.SUCCESS,null);
        return completed;
    }

    private void record(WarehouseRecoveryAuditExportJob job,String actor,
            WarehouseRecoveryAuditExportAccessEvent.Action action,
            WarehouseRecoveryAuditExportAccessEvent.Outcome outcome,String reason) {
        access.append(new WarehouseRecoveryAuditExportAccessEvent(UUID.randomUUID().toString(),
                job.tenantId(),job.exportId(),actor,action,outcome,reason,clock.instant()));
    }
    static boolean isTransientFailure(Exception e) {
        return e instanceof IOException
                || e instanceof WarehouseRecoveryAuditPersistenceException
                || (e.getMessage()!=null && e.getMessage().contains("version"));
    }

    private static String failureCode(Exception e) {
        if(e instanceof IllegalArgumentException) return "INVALID_EXPORT_PAGE";
        if(e instanceof IOException) return "ARTIFACT_IO_FAILURE";
        if(e.getMessage()!=null&&e.getMessage().contains("tenant")) return "TENANT_SCOPE_VIOLATION";
        if(e.getMessage()!=null&&e.getMessage().contains("version")) return "CONCURRENT_UPDATE";
        return "EXPORT_PROCESSING_FAILURE";
    }
    private static byte[] encode(java.util.List<WarehouseRecoveryOperatorAuditEvent> events,boolean includeHeader) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(includeHeader) out.writeBytes(HEADER.getBytes(StandardCharsets.UTF_8));
        for(WarehouseRecoveryOperatorAuditEvent e:events) {
            String row=csv(e.tenantId())+","+csv(e.commandKey())+","+csv(e.action())+","
                    +csv(e.actorId())+","+csv(e.reason())+","+e.priorVersion()+","+e.resultingVersion()+","
                    +csv(e.correlationId())+","+csv(e.occurredAt().toString())+"\n";
            out.writeBytes(row.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
    private static String csv(String value) {
        if(value==null) return "";
        String safe=value;
        if(!safe.isEmpty()&&(safe.charAt(0)=='='||safe.charAt(0)=='+'||safe.charAt(0)=='-'
                ||safe.charAt(0)=='@'||safe.charAt(0)=='\t'||safe.charAt(0)=='\r')) safe="'"+safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
