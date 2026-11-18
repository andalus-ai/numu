package tech.kayys.numou.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version_scan_results")
@IdClass(DocumentScanResultKey.class)
public class DocumentScanResultEntity extends PanacheEntityBase {
    @Id
    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Id
    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @Id
    @Column(name = "document_version_id", nullable = false)
    public UUID documentVersionId;

    @Column(name = "scan_status", nullable = false, length = 20)
    public String scanStatus;

    @Column(name = "scanner", length = 200)
    public String scanner;

    @Column(name = "signature_version", length = 200)
    public String signatureVersion;

    @Column(name = "failure_code", length = 200)
    public String failureCode;

    @Column(name = "scanned_at")
    public Instant scannedAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
