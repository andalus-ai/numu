package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;

/** Persistence row for a client idempotency key (ASSET-28 §47). */
@Entity
@Table(name = "asset_idempotency_key")
public class IdempotencyEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    public String idempotencyKey;

    @Column(name = "operation", nullable = false, length = 100)
    public String operation;

    @Column(name = "request_fingerprint", length = 512)
    public String requestFingerprint;

    @Column(name = "stored_at", nullable = false)
    public Instant storedAt;
}