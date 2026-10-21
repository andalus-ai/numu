package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numoudomain.*;

import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * PostgreSQL repository for the syirkah ledger (schema:
 * db/migration/V300__syirkah.sql).
 *
 * <ul>
 * <li>Tenant isolation: every statement runs after
 * {@code set_config('app.tenant_id', ..., true)}; the RLS
 * policies then hide other tenants' rows, and a wrong tenant simply finds
 * nothing.</li>
 * <li>Optimistic locking: {@code UPDATE ... WHERE version = expected}; the
 * update also takes the row lock, so
 * concurrent savers of one syirkah serialise before appending.</li>
 * <li>Append-only: only contracts, movements and closes not yet stored are
 * inserted; the only update to an
 * existing child row is a close going ACTIVE to REVERSED. The DB triggers
 * enforce both.</li>
 * <li>Load re-derives everything through {@link Syirkah#restore}, which
 * re-verifies every hash chain.</li>
 * </ul>
 */
public class JdbcSyirkahRepository implements SyirkahRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final JdbcUnitOfWork uow;

    public JdbcSyirkahRepository(JdbcUnitOfWork uow) {
        this.uow = Objects.requireNonNull(uow, "uow");
    }

    // ---------------------------------------------------------------- find

    @Override
    public Optional<Syirkah> find(TenantId tenant, SyirkahId id) {
        return inConnection(tenant, c -> {
            Syirkah.Snapshot snap = load(c, tenant, id);
            return snap == null ? Optional.<Syirkah>empty() : Optional.of(Syirkah.restore(snap));
        });
    }

    private Syirkah.Snapshot load(Connection c, TenantId tenant, SyirkahId id) throws SQLException {
        UUID sid = id.value();
        SyirkahStatus status;
        int scale;
        boolean closedSinceRevision;
        String liquidationJson;
        int version;
        Instant createdAt;
        Instant updatedAt;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT amount_scale, status, closed_since_revision, liquidation::text, version, created_at, updated_at"
                        + " FROM syirkah_ledger WHERE syirkah_id = ? AND tenant_id = ?")) {
            ps.setObject(1, sid);
            ps.setObject(2, tenant.value());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                scale = rs.getInt(1);
                status = SyirkahStatus.valueOf(rs.getString(2));
                closedSinceRevision = rs.getBoolean(3);
                liquidationJson = rs.getString(4);
                version = rs.getInt(5);
                createdAt = rs.getObject(6, OffsetDateTime.class).toInstant();
                updatedAt = rs.getObject(7, OffsetDateTime.class).toInstant();
            }
        }
        List<SyirkahContract> contracts = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT contract::text FROM syirkah_ledger_contract WHERE syirkah_id = ? ORDER BY version")) {
            ps.setObject(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contracts.add(SyirkahJsonCodec.decodeContract(rs.getString(1)));
                }
            }
        }
        List<PartnerMovement> movements = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT movement_id, sequence, partner_id, type, amount, currency, close_id, reference,"
                        + " occurred_at, prev_hash, hash FROM syirkah_ledger_movement"
                        + " WHERE syirkah_id = ? ORDER BY sequence")) {
            ps.setObject(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID closeId = rs.getObject(7, UUID.class);
                    movements.add(new PartnerMovement(rs.getObject(1, UUID.class), rs.getLong(2),
                            new PartnerId(rs.getObject(3, UUID.class)), MovementType.valueOf(rs.getString(4)),
                            new Money(rs.getBigDecimal(5), Currency.of(rs.getString(6).trim())),
                            closeId == null ? null : new CloseId(closeId), rs.getString(8),
                            rs.getObject(9, OffsetDateTime.class).toInstant(), rs.getString(10).trim(),
                            rs.getString(11).trim()));
                }
            }
        }
        List<CloseSnapshot> closes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT snapshot::text, status FROM syirkah_ledger_close WHERE syirkah_id = ? ORDER BY sequence")) {
            ps.setObject(1, sid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CloseSnapshot stored = SyirkahJsonCodec.decodeClose(rs.getString(1));
                    CloseStatus dbStatus = CloseStatus.valueOf(rs.getString(2));
                    // the JSON is frozen at insert; the live status lives in the column
                    closes.add(stored.status() == dbStatus ? stored : withStatus(stored, dbStatus));
                }
            }
        }
        LiquidationResult liquidation = liquidationJson == null ? null
                : SyirkahJsonCodec.decodeLiquidation(liquidationJson);
        return new Syirkah.Snapshot(id, tenant, scale, status, closedSinceRevision, contracts, movements, closes,
                liquidation, version, createdAt, updatedAt);
    }

    private static CloseSnapshot withStatus(CloseSnapshot s, CloseStatus status) {
        return new CloseSnapshot(s.id(), s.sequence(), s.periodStart(), s.periodEnd(), s.contractVersion(),
                s.contractCanonical(), s.result(), s.disposition(), s.fault(), s.lines(), s.supersedes(), status,
                s.firstMovementSequence(), s.lastMovementSequence(), s.closedAt(), s.previousHash(), s.hash());
    }

    // ---------------------------------------------------------------- save

    @Override
    public void save(Syirkah syirkah, int expectedVersion) {
        Syirkah.Snapshot before = syirkah.snapshot();
        syirkah.incrementVersion();
        Syirkah.Snapshot snap = syirkah.snapshot();
        try {
            inConnection(snap.tenantId(), c -> {
                write(c, snap, expectedVersion);
                return null;
            });
        } catch (RuntimeException e) {
            // keep the in-memory aggregate consistent with what is stored: the save did not
            // happen
            syirkah.setVersion(before.version());
            throw e;
        }
    }

    private void write(Connection c, Syirkah.Snapshot s, int expectedVersion) throws SQLException {
        UUID sid = s.id().value();
        SyirkahContract latest = s.contracts().get(s.contracts().size() - 1);
        String liquidation = s.liquidation() == null ? null : SyirkahJsonCodec.encodeLiquidation(s.liquidation());
        long storedMovements = 0;
        int storedContracts = 0;
        int storedCloses = 0;

        if (expectedVersion < 0) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO syirkah_ledger (syirkah_id, tenant_id, syirkah_type, currency, amount_scale, status,"
                            + " closed_since_revision, liquidation, version, created_at, updated_at)"
                            + " VALUES (?,?,?,?,?,?,?,?::jsonb,?,?,?)")) {
                ps.setObject(1, sid);
                ps.setObject(2, s.tenantId().value());
                ps.setString(3, latest.type().name());
                ps.setString(4, latest.currency().code());
                ps.setInt(5, s.scale());
                ps.setString(6, s.status().name());
                ps.setBoolean(7, s.closedSinceRevision());
                ps.setString(8, liquidation);
                ps.setInt(9, s.version());
                ps.setObject(10, ts(s.createdAt()));
                ps.setObject(11, ts(s.updatedAt()));
                ps.executeUpdate();
            } catch (SQLException e) {
                if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new ConcurrentModificationException("syirkah " + sid + " already exists");
                }
                throw e;
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE syirkah_ledger SET status = ?, closed_since_revision = ?, liquidation = ?::jsonb,"
                            + " version = ?, updated_at = ? WHERE syirkah_id = ? AND tenant_id = ? AND version = ?")) {
                ps.setString(1, s.status().name());
                ps.setBoolean(2, s.closedSinceRevision());
                ps.setString(3, liquidation);
                ps.setInt(4, s.version());
                ps.setObject(5, ts(s.updatedAt()));
                ps.setObject(6, sid);
                ps.setObject(7, s.tenantId().value());
                ps.setInt(8, expectedVersion);
                if (ps.executeUpdate() != 1) {
                    throw new ConcurrentModificationException("syirkah " + sid + " is not at version "
                            + expectedVersion + " (changed concurrently, or not found for this tenant)");
                }
            }
            // the UPDATE holds the row lock: these reads cannot race another saver of the
            // same syirkah
            storedMovements = scalarLong(c, "SELECT coalesce(max(sequence),0) FROM syirkah_ledger_movement"
                    + " WHERE syirkah_id = ?", sid);
            storedContracts = (int) scalarLong(c, "SELECT coalesce(max(version),0) FROM syirkah_ledger_contract"
                    + " WHERE syirkah_id = ?", sid);
            storedCloses = (int) scalarLong(c, "SELECT coalesce(max(sequence),0) FROM syirkah_ledger_close"
                    + " WHERE syirkah_id = ?", sid);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO syirkah_ledger_contract (syirkah_id, version, tenant_id, effective_from, canonical,"
                        + " contract) VALUES (?,?,?,?,?,?::jsonb)")) {
            for (SyirkahContract k : s.contracts()) {
                if (k.version() <= storedContracts) {
                    continue;
                }
                ps.setObject(1, sid);
                ps.setInt(2, k.version());
                ps.setObject(3, s.tenantId().value());
                ps.setObject(4, k.effectiveFrom());
                ps.setString(5, k.canonical());
                ps.setString(6, SyirkahJsonCodec.encodeContract(k));
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO syirkah_ledger_movement (syirkah_id, sequence, tenant_id, movement_id, partner_id, type,"
                        + " amount, currency, close_id, reference, occurred_at, prev_hash, hash)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (PartnerMovement m : s.movements()) {
                if (m.sequence() <= storedMovements) {
                    continue;
                }
                ps.setObject(1, sid);
                ps.setLong(2, m.sequence());
                ps.setObject(3, s.tenantId().value());
                ps.setObject(4, m.id());
                ps.setObject(5, m.partner().value());
                ps.setString(6, m.type().name());
                ps.setBigDecimal(7, m.amount().amount());
                ps.setString(8, m.amount().currency().code());
                ps.setObject(9, m.closeId() == null ? null : m.closeId().value());
                ps.setString(10, m.reference());
                ps.setObject(11, ts(m.occurredAt()));
                ps.setString(12, m.previousHash());
                ps.setString(13, m.hash());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO syirkah_ledger_close (syirkah_id, close_id, sequence, tenant_id, period_start, period_end,"
                        + " contract_version, status, supersedes, snapshot, first_movement, last_movement, closed_at,"
                        + " prev_hash, hash) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?)")) {
            for (CloseSnapshot k : s.closes()) {
                if (k.sequence() <= storedCloses) {
                    continue;
                }
                ps.setObject(1, sid);
                ps.setObject(2, k.id().value());
                ps.setInt(3, k.sequence());
                ps.setObject(4, s.tenantId().value());
                ps.setObject(5, k.periodStart());
                ps.setObject(6, k.periodEnd());
                ps.setInt(7, k.contractVersion());
                ps.setString(8, k.status().name());
                ps.setObject(9, k.supersedes() == null ? null : k.supersedes().value());
                ps.setString(10, SyirkahJsonCodec.encodeClose(k));
                ps.setLong(11, k.firstMovementSequence());
                ps.setLong(12, k.lastMovementSequence());
                ps.setObject(13, ts(k.closedAt()));
                ps.setString(14, k.previousHash());
                ps.setString(15, k.hash());
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // an already stored close may only change ACTIVE -> REVERSED (trigger-enforced)
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE syirkah_ledger_close SET status = ? WHERE syirkah_id = ? AND close_id = ? AND status <> ?")) {
            for (CloseSnapshot k : s.closes()) {
                if (k.sequence() > storedCloses || k.status() != CloseStatus.REVERSED) {
                    continue;
                }
                ps.setString(1, k.status().name());
                ps.setObject(2, sid);
                ps.setObject(3, k.id().value());
                ps.setString(4, k.status().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------------------------------------------------------------- plumbing

    private static OffsetDateTime ts(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static long scalarLong(Connection c, String sql, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private <T> T inConnection(TenantId tenant, JdbcSupport.SqlWork<T> work) {
        return JdbcSupport.inConnection(uow, tenant, work);
    }
}
