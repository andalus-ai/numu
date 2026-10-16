package tech.kayys.numou.foundation.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Base persistence entity for Panache Reactive entities.
 */
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    public UUID id;

    @Column(name = "active", nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Version
    @Column(name = "version")
    public Long version;
}
