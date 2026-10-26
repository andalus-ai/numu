package tech.kayys.numou.mobility.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.aggregate.persistence.TenantTransaction;
import tech.kayys.numou.mobility.wallet.domain.LedgerEntry;
import tech.kayys.numou.mobility.wallet.domain.WalletId;
import tech.kayys.numou.mobility.wallet.spi.LedgerRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Wallet ledger on the Hibernate session, so the append joins the wallet change's transaction. A replayed entry collides on the
 * deterministic entry id and is ignored ({@code ON CONFLICT DO NOTHING}); the table rejects UPDATE/DELETE/TRUNCATE. NOT COMPILED in sandbox.
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
    public Uni<Void> append(UUID tenantId, LedgerEntry e) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> s.createNativeQuery(INSERT)
                .setParameter(1, tenantId).setParameter(2, e.id()).setParameter(3, e.walletId()).setParameter(4, e.kind().name())
                .setParameter(5, e.amount()).setParameter(6, e.balanceAfter()).setParameter(7, e.ref()).setParameter(8, e.memo())
                .setParameter(9, e.at().atOffset(ZoneOffset.UTC)).setParameter(10, e.seq()).setParameter(11, e.requestFingerprint()).executeUpdate().replaceWithVoid()));
    }

    @Override
    public Uni<java.util.Optional<LedgerEntry>> findByRef(UUID tenantId, WalletId wallet, String ref) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> s.createNativeQuery(BY_REF, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, ref).getResultList()
                .map(rows -> rows.stream().findFirst().map(this::parse))));
    }

    @Override
    public Uni<List<LedgerEntry>> history(UUID tenantId, WalletId wallet, int limit) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> s.createNativeQuery(HISTORY, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, limit).getResultList()
                .map(rows -> rows.stream().map(this::parse).toList())));
    }

    @Override
    public Uni<List<LedgerEntry>> range(UUID tenantId, WalletId wallet, Instant from, Instant to, int limit) {
        return TenantTransaction.run(tenantId, () -> Panache.getSession().flatMap(s -> s.createNativeQuery(RANGE, String.class)
                .setParameter(1, tenantId).setParameter(2, wallet.value()).setParameter(3, from.atOffset(ZoneOffset.UTC))
                .setParameter(4, to.atOffset(ZoneOffset.UTC)).setParameter(5, limit).getResultList()
                .map(rows -> rows.stream().map(this::parse).toList())));
    }

    private LedgerEntry parse(String json) {
        try {
            final JsonNode n = mapper.readTree(json);
            return new LedgerEntry(UUID.fromString(n.get("entry_id").asText()), UUID.fromString(n.get("wallet_id").asText()),
                    LedgerEntry.Kind.valueOf(n.get("kind").asText()), new BigDecimal(n.get("amount").asText()), new BigDecimal(n.get("balance_after").asText()),
                    n.get("ref").asText(), n.get("memo").asText(), OffsetDateTime.parse(n.get("at").asText()).toInstant(), n.get("seq").asLong(),
                    n.hasNonNull("request_fingerprint") ? n.get("request_fingerprint").asText() : "");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt ledger row", e);
        }
    }
}
