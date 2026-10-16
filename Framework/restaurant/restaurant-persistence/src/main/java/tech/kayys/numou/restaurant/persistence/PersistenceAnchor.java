package tech.kayys.numou.restaurant.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Inert entity that keeps the Hibernate Reactive extension enabled.
 *
 * <p>Quarkus switches Hibernate off when it finds no {@code @Entity}, and then {@code Panache.getSession()} (used by
 * {@link TenantTransaction} and the document repositories) has no session factory. The restaurant aggregates are
 * stored as JSON documents through native SQL, so there is nothing to map; this class exists only so the persistence
 * unit boots. It is never read or written ({@code quarkus.hibernate-orm.database.generation=none}).
 */
@Entity
@Table(name = "hibernate_anchor_unused")
public class PersistenceAnchor {

    @Id
    public UUID id;
}
