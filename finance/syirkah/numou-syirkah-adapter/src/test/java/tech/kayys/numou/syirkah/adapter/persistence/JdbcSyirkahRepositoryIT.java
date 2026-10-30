package tech.kayys.numou.numouadapter.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numoudomain.*;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real PostgreSQL. Enable with SYIRKAH_PG_URL=jdbc:postgresql://host/db plus SYIRKAH_PG_USER and
 * SYIRKAH_PG_PASSWORD. The user must NOT be a superuser and must not have BYPASSRLS, otherwise row-level security
 * is silently skipped and the tenant checks below prove nothing (the test asserts this).
 * The schema is created from db/migration/V20261023_01__syirkah.sql if absent (run once as the table owner).
 */
@EnabledIfEnvironmentVariable(named = "SYIRKAH_PG_URL", matches = ".+")
class JdbcSyirkahRepositoryIT {

    private static final Currency IDR = Currency.of("IDR");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    private static JdbcUnitOfWork uow;
    private static JdbcSyirkahRepository repo;

    private static Money idr(String v) {
        return new Money(new BigDecimal(v), IDR);
    }

    @BeforeAll
    static void setUp() throws Exception {
        Object ds = Class.forName("org.postgresql.ds.PGSimpleDataSource").getDeclaredConstructor().newInstance();
        ds.getClass().getMethod("setUrl", String.class).invoke(ds, System.getenv("SYIRKAH_PG_URL"));
        ds.getClass().getMethod("setUser", String.class).invoke(ds, System.getenv("SYIRKAH_PG_USER"));
        ds.getClass().getMethod("setPassword", String.class).invoke(ds, System.getenv("SYIRKAH_PG_PASSWORD"));
        DataSource dataSource = (DataSource) ds;
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            var rs = st.executeQuery("select rolsuper or rolbypassrls from pg_roles where rolname = current_user");
            rs.next();
            assertFalse(rs.getBoolean(1), "test role must not bypass row-level security");
            rs = st.executeQuery("select to_regclass('syirkah_ledger') is not null");
            rs.next();
            if (!rs.getBoolean(1)) {
                for (String script : List.of("V20261023_01__syirkah.sql", "V20261023_02__syirkah_governance_outbox.sql",
                        "V20261028_01__syirkah_akad_formation.sql", "V20261029_01__syirkah_lifecycle.sql", "V20261030_01__syirkah_staff.sql", "V20261030_02__syirkah_trading.sql")) {
                    try (InputStream in = JdbcSyirkahRepositoryIT.class.getResourceAsStream("/db/migration/" + script)) {
                        st.execute(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        uow = new JdbcUnitOfWork(dataSource);
        repo = new JdbcSyirkahRepository(uow);
    }

    private static Syirkah open(TenantId tenant) {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, new BigDecimal("40"));
        n.put(B, new BigDecimal("60"));
        SyirkahContract c = new SyirkahContract(1, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr("40000000")), CapitalContribution.cash(B, idr("60000000"))),
                ProfitSharingRatio.ofPercent(n), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                LocalDate.parse("2026-01-01"));
        return Syirkah.open(SyirkahId.generate(), tenant, c, 0, T0);
    }

    private static CloseSnapshot close(Syirkah s, long profit, int day) {
        return s.closePeriod(CloseId.generate(), LocalDate.parse("2026-01-01").plusMonths(day),
                LocalDate.parse("2026-01-28").plusMonths(day),
                FinancialResult.of(new Money(BigDecimal.valueOf(profit), IDR), idr("0"), idr("0"), idr("0")),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, T0.plusSeconds(day + 1));
    }

    @Test
    void saveFindRoundTripAppendAndReverse() {
        TenantId tenant = TenantId.generate();
        Syirkah s = open(tenant);
        repo.save(s, -1);
        Syirkah loaded = repo.find(tenant, s.id()).orElseThrow();
        assertEquals(s.snapshot(), loaded.snapshot());

        int v = loaded.getVersion();
        CloseSnapshot c1 = close(loaded, 9_500_000, 0);
        repo.save(loaded, v);

        Syirkah again = repo.find(tenant, s.id()).orElseThrow();
        assertEquals(loaded.snapshot(), again.snapshot());
        v = again.getVersion();
        again.correctLatestClose(c1.id(), CloseId.generate(),
                FinancialResult.of(idr("8000000"), idr("0"), idr("0"), idr("0")), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, T0.plusSeconds(10));
        repo.save(again, v);

        Syirkah third = repo.find(tenant, s.id()).orElseThrow();
        assertEquals(again.snapshot(), third.snapshot());
        assertTrue(third.closes().stream().anyMatch(c -> c.status() == CloseStatus.REVERSED));
        assertTrue(third.verifyIntegrity().isEmpty());
    }

    @Test
    void staleVersionAndDuplicateCreateAreRejected() {
        TenantId tenant = TenantId.generate();
        Syirkah s = open(tenant);
        repo.save(s, -1);
        assertThrows(SyirkahRepository.ConcurrentModificationException.class, () -> repo.save(s, -1));

        Syirkah a = repo.find(tenant, s.id()).orElseThrow();
        Syirkah b = repo.find(tenant, s.id()).orElseThrow();
        int v = a.getVersion();
        close(a, 1_000_000, 0);
        repo.save(a, v);
        close(b, 2_000_000, 0);
        assertThrows(SyirkahRepository.ConcurrentModificationException.class, () -> repo.save(b, v));
        // the failed save must not leave the in-memory aggregate claiming the next version
        assertEquals(v, b.getVersion());
        // and nothing of b was stored
        assertEquals(1, repo.find(tenant, s.id()).orElseThrow().closes().size());
    }

    @Test
    void otherTenantsCannotSeeOrWrite() {
        TenantId owner = TenantId.generate();
        TenantId other = TenantId.generate();
        Syirkah s = open(owner);
        repo.save(s, -1);
        assertTrue(repo.find(other, s.id()).isEmpty());
        assertTrue(repo.find(owner, s.id()).isPresent());

        Syirkah mine = repo.find(owner, s.id()).orElseThrow();
        int v = mine.getVersion();
        close(mine, 1_000_000, 0);
        // pretend to be another tenant: same id, forged tenant -> RLS hides the row, so it reads as a conflict
        Syirkah.Snapshot snap = mine.snapshot();
        Syirkah forged = Syirkah.restore(new Syirkah.Snapshot(snap.id(), other, snap.scale(), snap.status(),
                snap.closedSinceRevision(), snap.contracts(), snap.movements(), snap.closes(), snap.liquidation(),
                v, snap.createdAt(), snap.updatedAt()));
        assertThrows(RuntimeException.class, () -> repo.save(forged, v));
        assertEquals(0, repo.find(owner, s.id()).orElseThrow().closes().size());
    }

    @Test
    void unitOfWorkRollsBackEverythingOnFailure() {
        TenantId tenant = TenantId.generate();
        Syirkah s = open(tenant);
        assertThrows(IllegalStateException.class, () -> uow.inTransaction(() -> {
            repo.save(s, -1);
            throw new IllegalStateException("downstream failure");
        }));
        assertTrue(repo.find(tenant, s.id()).isEmpty());
    }

    @Test
    void approvalsAreTenantScopedAndDecidedOnce() {
        var approvals = new JdbcApprovalPort(uow);
        TenantId t = TenantId.generate();
        SyirkahId sid = SyirkahId.generate();
        UUID id = UUID.randomUUID();
        var payload = new tech.kayys.numou.numouapplication.command.Commands.Liquidate(t, sid,
                new LiquidationInput(idr("9.00"), idr("1.00"), true, LocalDate.parse("2026-05-05")),
                ManagerFault.none());
        var p = new tech.kayys.numou.numouapplication.governance.PendingApproval(id, t, sid,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.LIQUIDATE, payload, "alice", T0,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Status.PENDING, null, null, null);
        approvals.save(p);
        assertEquals(List.of(p), approvals.pending(t));
        assertTrue(approvals.find(TenantId.generate(), id).isEmpty());
        var decided = new tech.kayys.numou.numouapplication.governance.PendingApproval(id, t, sid, p.kind(),
                payload, "alice", T0,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Status.APPROVED, "bob",
                T0.plusSeconds(5), "ok");
        approvals.save(decided);
        assertEquals(decided, approvals.find(t, id).orElseThrow());
        assertTrue(approvals.pending(t).isEmpty());
        assertThrows(IllegalStateException.class, () -> approvals.save(p));
    }

    @Test
    void outboxIsTransactionalAndIdempotent() throws Exception {
        var outbox = new JdbcOutboxEventPublisher(uow);
        TenantId t = TenantId.generate();
        var e = new SyirkahEvents.SyirkahOpened(UUID.randomUUID(), T0, SyirkahId.generate(), SyirkahType.INAN, 1);
        assertThrows(IllegalStateException.class, () -> uow.inTransaction(() -> {
            outbox.publish(t, List.of(e));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, countOutbox(t));
        outbox.publish(t, List.of(e));
        outbox.publish(t, List.of(e));
        assertEquals(1, countOutbox(t));
    }

    private static int countOutbox(TenantId t) {
        return uow.inTransaction(() -> JdbcSupport.inConnection(uow, t, c -> {
            try (var rs = c.createStatement().executeQuery("select count(*) from syirkah_ledger_outbox")) {
                rs.next();
                return rs.getInt(1);
            }
        }));
    }

    @Test
    void catalogAndOutboxRelayWorkAgainstPostgres() {
        var ops = new JdbcOperationsSupport(uow);
        TenantId t = TenantId.generate();
        Syirkah a = open(t);
        Syirkah b = open(t);
        repo.save(a, -1);
        repo.save(b, -1);
        var all = ops.page(t, Optional.empty(), Optional.empty(), 10);
        assertEquals(2, all.size());
        assertEquals(1, ops.page(t, Optional.empty(), Optional.of(all.get(0).id()), 10).size());
        assertTrue(ops.page(TenantId.generate(), Optional.empty(), Optional.empty(), 10).isEmpty());

        var outbox = new JdbcOutboxEventPublisher(uow);
        var e1 = new SyirkahEvents.SyirkahOpened(UUID.randomUUID(), T0, a.id(), SyirkahType.INAN, 1);
        var e2 = new SyirkahEvents.SyirkahOpened(UUID.randomUUID(), T0.plusSeconds(1), b.id(), SyirkahType.INAN, 1);
        outbox.publish(t, List.of(e1, e2));
        var relay = new tech.kayys.numou.numouapplication.ops.OutboxRelay(ops, uow, java.time.Clock.systemUTC());
        List<tech.kayys.numou.numouapplication.ops.OutboxRecord> got = new ArrayList<>();
        assertEquals(2, relay.drain(t, 10, got::add));
        assertEquals(List.of(e1.eventId(), e2.eventId()), got.stream().map(r -> r.eventId()).toList());
        assertEquals(0, relay.drain(t, 10, got::add));
    }

    @Test
    void auditorAcceptsAHealthyPersistedLedger() {
        var ops = new JdbcOperationsSupport(uow);
        TenantId t = TenantId.generate();
        Syirkah s = open(t);
        repo.save(s, -1);
        Syirkah loaded = repo.find(t, s.id()).orElseThrow();
        int v = loaded.getVersion();
        close(loaded, 5_000_000, 0);
        repo.save(loaded, v);
        var report = new tech.kayys.numou.numouapplication.ops.SyirkahIntegrityAuditor(repo, ops).audit(t);
        assertTrue(report.ok(), report.toString());
        assertEquals(1, report.checked());
    }

    @Test
    void akadFormationIsStoredOnceIsolatedByTenantAndNeverChanged() {
        var store = new JdbcAkadFormationPort(uow);
        TenantId t = TenantId.generate();
        Syirkah s = open(t);
        AkadFormation f = new AkadFormation(
                List.of(new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true, "ijab"),
                        new PartyRecord(B, LocalDate.parse("1991-06-18"), "Bandung", true, true, true, "qabul")),
                "Retail", new AkadFormation.LawfulnessReview("Sharia adviser", LocalDate.parse("2026-01-01"), true),
                Set.of(), List.of(), true, false, EnumSet.allOf(AkadSection.class), "Jakarta",
                LocalDate.parse("2026-01-01"),
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE)));
        FormationPolicy policy = FormationPolicy.bookDefault();
        uow.inTransaction(() -> {
            repo.save(s, -1);
            store.record(t, s.id(), f, policy, T0);
            return null;
        });
        var got = store.find(t, s.id()).orElseThrow();
        assertEquals(f, got.formation());
        assertEquals(policy, got.policy());
        assertEquals(T0, got.recordedAt());
        assertTrue(store.find(TenantId.generate(), s.id()).isEmpty(), "another tenant must not see the record");
        assertThrows(IllegalStateException.class, () -> store.record(t, s.id(), f, policy, T0.plusSeconds(1)));
        assertEquals(f, store.find(t, s.id()).orElseThrow().formation());
    }

    @Test
    void lifecycleRecordIsStoredOncePerVersionIsolatedByTenantAndNeverChanged() {
        var store = new JdbcLifecycleRecordPort(uow);
        TenantId t = TenantId.generate();
        Syirkah s = open(t);
        LifecycleChange ch = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), LocalDate.parse("2026-02-01"),
                List.of(new LifecycleConsent(A, true, LocalDate.parse("2026-02-01"), "sig-a")),
                ProfitDisposition.RETAIN, List.of());
        uow.inTransaction(() -> {
            repo.save(s, -1);
            store.record(t, s.id(), 2, ch, T0);
            return null;
        });
        var got = store.list(t, s.id());
        assertEquals(1, got.size());
        assertEquals(2, got.get(0).contractVersion());
        assertEquals(ch, got.get(0).change());
        assertTrue(store.list(TenantId.generate(), s.id()).isEmpty(), "another tenant must not see the record");
        assertThrows(RuntimeException.class, () -> store.record(t, s.id(), 2, ch, T0.plusSeconds(1)));
    }

    @Test
    void staffContractsAreNumberedIsolatedByTenantAndNeverChanged() {
        var store = new JdbcStaffRecordPort(uow);
        TenantId t = TenantId.generate();
        Syirkah s = open(t);
        StaffContract st = new StaffContract(StaffContract.Employer.SYIRKAH, null, "Sales", "12 months", "8 hours",
                new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT,
                        tech.kayys.numou.foundation.domain.valueobject.Money.of(new java.math.BigDecimal("3000000"),
                                tech.kayys.numou.foundation.domain.valueobject.Currency.of("IDR")), "monthly"),
                List.of("uniform"), true);
        uow.inTransaction(() -> {
            repo.save(s, -1);
            assertEquals(1, store.add(t, s.id(), 1, st, T0));
            assertEquals(2, store.add(t, s.id(), 1, st, T0.plusSeconds(1)));
            return null;
        });
        var got = store.list(t, s.id());
        assertEquals(2, got.size());
        assertEquals(st, got.get(0).staff());
        assertTrue(store.list(TenantId.generate(), s.id()).isEmpty(), "another tenant must not see the contracts");
    }

    @Test
    void tradingFiguresAreStoredOncePerCloseAndIsolatedByTenant() {
        var store = new JdbcTradingRecordPort(uow);
        TenantId t = TenantId.generate();
        Syirkah s = open(t);
        var cur = tech.kayys.numou.foundation.domain.valueobject.Currency.of("IDR");
        java.util.function.LongFunction<tech.kayys.numou.foundation.domain.valueobject.Money> m =
                v -> tech.kayys.numou.foundation.domain.valueobject.Money.of(java.math.BigDecimal.valueOf(v), cur);
        var in = new TradingResultCalculator.Input(m.apply(100), m.apply(5), m.apply(0), m.apply(20), m.apply(60),
                m.apply(0), m.apply(0), m.apply(30), m.apply(10), m.apply(0));
        CloseId close = CloseId.generate();
        uow.inTransaction(() -> {
            repo.save(s, -1);
            store.record(t, s.id(), close, in, T0);
            return null;
        });
        assertEquals(in, store.find(t, s.id(), close).orElseThrow().input());
        assertTrue(store.list(TenantId.generate(), s.id()).isEmpty(), "another tenant must not see the figures");
        assertThrows(RuntimeException.class, () -> store.record(t, s.id(), close, in, T0.plusSeconds(1)));
    }
}
