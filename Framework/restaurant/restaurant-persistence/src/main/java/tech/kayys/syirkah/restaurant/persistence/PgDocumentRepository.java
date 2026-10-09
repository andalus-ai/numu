package tech.kayys.syirkah.restaurant.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import tech.kayys.syirkah.restaurant.kernel.domain.ConcurrencyConflictException;
import tech.kayys.syirkah.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.syirkah.restaurant.kernel.spi.VersionedRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Generic JSONB document repository: optimistic locking via {@code WHERE version = expected} and tenant isolation
 * via row-level security (see V200). The SQL shapes are the ones verified on PostgreSQL 16.
 *
 * <p>Uses the Hibernate Reactive session (not a second pool) so every statement runs in the caller's
 * unit-of-work transaction, which is what makes aggregate + outbox atomic. NOT COMPILED in sandbox: check the
 * native-query parameter binding for your Hibernate Reactive version.
 *
 * @param <S> snapshot record (plain types only; JavaTimeModule must be registered on the ObjectMapper)
 */
public abstract class PgDocumentRepository<A extends RestaurantAggregate<ID>, ID extends DomainId<UUID>, S>
        implements VersionedRepository<A, ID> {

    /** Columns lifted out of the document for querying. Nulls are fine. */
    public record Index(UUID venueId, UUID refId, Instant startsAt, String status) {
        public static final Index NONE = new Index(null, null, null, null);
    }

    private static final String INSERT = """
            INSERT INTO restaurant_document (tenant_id, kind, id, version, venue_id, ref_id, starts_at, status, doc)
            VALUES (?1, ?2, ?3, 1, ?4, ?5, ?6, ?7, CAST(?8 AS jsonb))
            ON CONFLICT DO NOTHING""";
    private static final String UPDATE = """
            UPDATE restaurant_document
            SET version = version + 1, venue_id = ?4, ref_id = ?5, starts_at = ?6, status = ?7, doc = CAST(?8 AS jsonb), updated_at = now()
            WHERE tenant_id = ?1 AND kind = ?2 AND id = ?3 AND version = ?9""";
    private static final String SELECT = """
            SELECT CAST(version AS text) || ':' || CAST(doc AS text) FROM restaurant_document
            WHERE tenant_id = ?1 AND kind = ?2 AND id = ?3""";

    private final ObjectMapper mapper;
    private final String kind;
    private final Class<S> snapshotType;
    private final Function<A, S> toSnapshot;
    private final Function<S, A> fromSnapshot;
    private final Function<A, Index> indexer;

    protected PgDocumentRepository(ObjectMapper mapper, String kind, Class<S> snapshotType, Function<A, S> toSnapshot,
                                   Function<S, A> fromSnapshot, Function<A, Index> indexer) {
        this.mapper = mapper;
        this.kind = kind;
        this.snapshotType = snapshotType;
        this.toSnapshot = toSnapshot;
        this.fromSnapshot = fromSnapshot;
        this.indexer = indexer;
    }

    @Override
    public Uni<Optional<A>> find(UUID tenantId, ID id) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> s.createNativeQuery(SELECT, String.class)
                .setParameter(1, tenantId).setParameter(2, kind).setParameter(3, id.value())
                .getResultList().map(rows -> rows.stream().findFirst().map(this::read))));
    }

    @Override
    public Uni<Void> save(UUID tenantId, A aggregate) {
        final long expected = aggregate.storedVersion();
        final Index ix = indexer.apply(aggregate);
        final String json = write(toSnapshot.apply(aggregate));
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> {
            final var q = s.createNativeQuery(expected == 0 ? INSERT : UPDATE)
                    .setParameter(1, tenantId).setParameter(2, kind).setParameter(3, aggregate.id().value())
                    .setParameter(4, ix.venueId()).setParameter(5, ix.refId()).setParameter(6, utc(ix.startsAt()))
                    .setParameter(7, ix.status()).setParameter(8, json);
            if (expected != 0) {
                q.setParameter(9, expected);
            }
            return q.executeUpdate().flatMap(rows -> {
                if (rows != 1) { // 0 rows: someone else changed (or created) it first
                    return Uni.createFrom().failure(new ConcurrencyConflictException(
                            "Aggregate " + kind + " " + aggregate.id() + (expected == 0 ? " already exists" : " was modified concurrently")));
                }
                aggregate.markStored(expected + 1);
                return Uni.createFrom().voidItem();
            });
        }));
    }

    /** For concrete repositories' query methods; {@code where} may use ?3, ?4, ... for {@code params}. */
    protected final Uni<List<A>> query(UUID tenantId, String where, String orderBy, Object... params) {
        final String sql = "SELECT CAST(version AS text) || ':' || CAST(doc AS text) FROM restaurant_document "
                + "WHERE tenant_id = ?1 AND kind = ?2 AND (" + where + ") ORDER BY " + orderBy;
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> {
            final var q = s.createNativeQuery(sql, String.class).setParameter(1, tenantId).setParameter(2, kind);
            for (int i = 0; i < params.length; i++) {
                q.setParameter(i + 3, params[i] instanceof Instant t ? utc(t) : params[i]);
            }
            return q.getResultList().map(rows -> rows.stream().map(this::read).toList());
        }));
    }

    private A read(String versionAndDoc) {
        final int sep = versionAndDoc.indexOf(':');
        try {
            final A aggregate = fromSnapshot.apply(mapper.readValue(versionAndDoc.substring(sep + 1), snapshotType));
            aggregate.markStored(Long.parseLong(versionAndDoc.substring(0, sep)));
            return aggregate;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt " + kind + " document", e);
        }
    }

    private String write(S snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + kind, e);
        }
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }
}
