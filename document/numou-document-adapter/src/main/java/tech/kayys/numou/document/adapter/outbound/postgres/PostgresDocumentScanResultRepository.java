package tech.kayys.numou.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.domain.document.DocumentScanResult;
import tech.kayys.numou.accounting.domain.document.DocumentScanStatus;
import tech.kayys.numou.document.application.port.DocumentScanResultRepository;

import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentScanResultRepository implements DocumentScanResultRepository {
    @Override
    public Uni<Boolean> record(String tenantId, DocumentScanResult result) {
        var tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        var documentUuid = PostgresDocumentRepository.parseUuid(result.documentId().value(), "documentId");
        var versionUuid = PostgresDocumentRepository.parseUuid(result.versionId().value(), "documentVersionId");
        var key = new DocumentScanResultKey(tenantUuid, documentUuid, versionUuid);

        return DocumentScanResultEntity.<DocumentScanResultEntity>findById(key)
                .chain(existing -> {
                    if (existing != null) {
                        if (!canReplace(existing, result)) {
                            return Uni.createFrom().item(false);
                        }
                        apply(existing, result);
                        return Uni.createFrom().item(true);
                    }

                    var created = new DocumentScanResultEntity();
                    created.tenantId = tenantUuid;
                    created.documentId = documentUuid;
                    created.documentVersionId = versionUuid;
                    apply(created, result);
                    return Panache.getSession()
                            .chain(session -> session.persist(created))
                            .replaceWith(true);
                });
    }

    private static boolean canReplace(DocumentScanResultEntity existing, DocumentScanResult incoming) {
        var current = DocumentScanStatus.valueOf(existing.scanStatus);
        if (current == DocumentScanStatus.CLEAN || current == DocumentScanStatus.INFECTED) {
            return false;
        }
        if (current == DocumentScanStatus.PENDING) {
            return true;
        }
        // FAILED is retryable, but replaying the same or an older failure is a no-op.
        return incoming.scannedAt().isAfter(existing.scannedAt);
    }

    private static void apply(DocumentScanResultEntity target, DocumentScanResult result) {
        target.scanStatus = result.status().name();
        target.scanner = result.scanner();
        target.signatureVersion = result.signatureVersion();
        target.failureCode = result.failureCode();
        target.scannedAt = result.scannedAt();
        target.updatedAt = result.scannedAt();
    }
}
