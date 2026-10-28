package tech.kayys.numou.wallet.adapter.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.aggregate.domain.ConcurrencyConflictException;
import tech.kayys.numou.aggregate.persistence.TenantTransaction;
import tech.kayys.numou.wallet.domain.LedgerEntry;
import tech.kayys.numou.wallet.domain.WalletId;
import tech.kayys.numou.wallet.spi.LedgerRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL append-only ledger adapter. Calls use the active Hibernate session so appends can
 * participate in the caller's transaction. A suppressed unique-key conflict is surfaced as a
 * concurrency conflict; it must not permit an aggregate update to commit without its ledger entry.
 */
@ApplicationScoped
public class PgLedgerRepository implements LedgerRepository {

    static final String INSERT = """
            INSERT INTO wallet_ledger (tenant_id, entry_id, wallet_id, kind, amount, balance_after, ref, memo, at, seq, request_fingerprint)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11) ON CONFLICT DO NOTHING""";
    static final String BY_REF = """
            SELECT CAST(row_to_json(l) AS text) FROM wallet_ledger l WHERE tenant_id = ?1 AND wallet_id = ?2 AND ref = ?3 LIMIT 1""";
    static final String HISTORY = """
            SELECT CAST(row_to_json(l) AS text) FROM wallet_ledger l WHERE tenant_id = ?1 AND wallet_id = ?2 ORDER BY seq DESC LIMIT ?3""";
    static final String RANGE = """
            SELECT CAST(row_to_json(l) AS text) FROM wallet_ledger l
            WHERE tenant_id = ?1 AND wallet_id = ?2 AND at >= ?3 AND at < ?4 ORDER BY seq LIMIT ?5""";

    @Inject
    ObjectMapper mapper;

    @Override
    public Uni<Void> append(UUID tenantId, LedgerEntry entry) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(session -> session.createNativeQuery(INSERT)
                .setParameter(1, tenantId).setParameter(2, entry.id()).setParameter(3, entry.walletId()).setParameter(4, entry.kind().name())
                .setParameter(5, entry.amount()).setParameter(6, entry.balanceAfter()).setParameter(7, entry.ref()).setParameter(8, entry.memo())
                .setParameter(9, entry.at().atOffset(ZoneOffset.UTC)).setParameter(10, entry.seq()).setParameter(11, entry.requestFingerprint())
                .executeUpdate().invoke(PgLedgerRepository::requireInserted).replaceWithVoid()));
    }

    static void requireInserted(int rows) {
        if (rows != 1) {
            throw new ConcurrencyConflictException("Wallet ledger append conflicted with an existing entry; retry the wallet operation");
        }
    }

    @Override
    public Uni<Optional<LedgerEntry>> findByRef(UUID tenantId, WalletId wallet, String ref) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(session -> session.createNativeQuery(BY_REF, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, ref).getResultList()
                .map(rows -> rows.stream().findFirst().map(this::parse))));
    }

    @Override
    public Uni<List<LedgerEntry>> history(UUID tenantId, WalletId wallet, int limit) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(session -> session.createNativeQuery(HISTORY, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, limit).getResultList()
                .map(rows -> rows.stream().map(this::parse).toList())));
    }

    @Override
    public Uni<List<LedgerEntry>> range(UUID tenantId, WalletId wallet, Instant from, Instant to, int limit) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(session -> session.createNativeQuery(RANGE, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, from.atOffset(ZoneOffset.UTC))
                .setParameter(4, to.atOffset(ZoneOffset.UTC)).setParameter(5, limit).getResultList()
                .map(rows -> rows.stream().map(this::parse).toList())));
    }

    LedgerEntry parse(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return new LedgerEntry(UUID.fromString(node.get("entry_id").asText()), UUID.fromString(node.get("wallet_id").asText()),
                    LedgerEntry.Kind.valueOf(node.get("kind").asText()), new BigDecimal(node.get("amount").asText()),
                    new BigDecimal(node.get("balance_after").asText()), node.get("ref").asText(), node.get("memo").asText(),
                    OffsetDateTime.parse(node.get("at").asText()).toInstant(), node.get("seq").asLong(),
                    node.hasNonNull("request_fingerprint") ? node.get("request_fingerprint").asText() : "");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt ledger row", e);
        }
    }
}
