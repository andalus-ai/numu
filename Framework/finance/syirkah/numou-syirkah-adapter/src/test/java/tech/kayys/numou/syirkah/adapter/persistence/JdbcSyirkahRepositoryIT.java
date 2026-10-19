package tech.kayys.numou.syirkah.adapter.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.domain.*;

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
 * The schema is created from db/migration/V300__syirkah.sql if absent (run once as the table owner).
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
                try (InputStream in = JdbcSyirkahRepositoryIT.class.getResourceAsStream("/db/migration/V300__syirkah.sql")) {
                    st.execute(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
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
}
