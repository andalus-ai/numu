package tech.kayys.numou.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_links")
public class DocumentLinkEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @Column(name = "document_version_id")
    public UUID documentVersionId;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "relationship", nullable = false)
    public String relationship;

    @Column(name = "linked_by")
    public UUID linkedBy;

    @Column(name = "linked_at", nullable = false)
    public Instant linkedAt;
}
