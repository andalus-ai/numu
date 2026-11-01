package tech.kayys.numou.numouadapter.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouadapter.memory.InMemoryJournalPostingPort;
import tech.kayys.numou.numouapplication.FormationEnforcement;
import tech.kayys.numou.numouapplication.StaffContractService;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numoudomain.*;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One whole syirkah through the application service on a real PostgreSQL, with every store the service uses: open,
 * close, new capital with its lifecycle record, staff, a second close with its trading figures, liquidation. Then
 * everything is read back from the database. Same enabling rules as {@link JdbcSyirkahRepositoryIT}.
 */
@EnabledIfEnvironmentVariable(named = "SYIRKAH_PG_URL", matches = ".+")
class SyirkahPostgresFlowIT {

    private static final Currency IDR = Currency.of("IDR");
    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");
    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");
    private static final LocalDate FEB28 = LocalDate.parse("2026-02-28");

    private static JdbcUnitOfWork uow;
    private static JdbcSyirkahRepository repo;

    private static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), IDR);
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
            rs = st.executeQuery("select to_regclass('syirkah_ledger_trading') is not null");
            rs.next();
            if (!rs.getBoolean(1)) {
                for (String script : List.of("V20261023_01__syirkah.sql", "V20261023_02__syirkah_governance_outbox.sql",
                        "V20261028_01__syirkah_akad_formation.sql", "V20261029_01__syirkah_lifecycle.sql",
                        "V20261030_01__syirkah_staff.sql", "V20261030_02__syirkah_trading.sql")) {
                    try (InputStream in = SyirkahPostgresFlowIT.class.getResourceAsStream("/db/migration/" + script)) {
                        st.execute(new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        uow = new JdbcUnitOfWork(dataSource);
        repo = new JdbcSyirkahRepository(uow);
    }

    private static SyirkahContract contract(int version, long capA, long capB, int shareA, int shareB,
                                            LocalDate from) {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, BigDecimal.valueOf(shareA));
        n.put(B, BigDecimal.valueOf(shareB));
        return new SyirkahContract(version, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(capA)), CapitalContribution.cash(B, idr(capB))),
                ProfitSharingRatio.ofPercent(n), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                from);
    }

    @Test
    void aWholeSyirkahRunsOnPostgresAndReadsBackConsistently() {
        TenantId tenant = TenantId.generate();
        SyirkahAccountMap accounts = new SyirkahAccountMap(Map.of(SyirkahPostingPlan.AccountRole.CASH, "1100",
                SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100", SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE,
                "2300", SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
                SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300"));
        InMemoryJournalPostingPort journal = new InMemoryJournalPostingPort();
        JdbcLifecycleRecordPort lifecycle = new JdbcLifecycleRecordPort(uow);
        JdbcStaffRecordPort staff = new JdbcStaffRecordPort(uow);
        JdbcTradingRecordPort trading = new JdbcTradingRecordPort(uow);
        Clock clock = Clock.system(ZoneOffset.UTC);
        SyirkahApplicationService service = new SyirkahApplicationService(repo, journal,
                new JdbcOutboxEventPublisher(uow), uow, accounts, clock, FormationEnforcement.OFF,
                new JdbcAkadFormationPort(uow), lifecycle);
        StaffContractService staffService = new StaffContractService(repo, staff, uow, clock);

        // open and first close, profit retained
        SyirkahId id = SyirkahId.generate();
        service.open(new Commands.Open(tenant, id, contract(1, 100_000_000, 100_000_000, 50, 50, JAN1), 0));
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), JAN1, JAN31,
                FinancialResult.of(idr(10_000_000), idr(0), idr(0), idr(0)), ManagerFault.none(),
                ProfitDisposition.RETAIN));

        // new capital, agreed by both, with its record
        SyirkahContract v2 = contract(2, 150_000_000, 100_000_000, 55, 45, FEB1);
        LifecycleChange change = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                List.of(new LifecycleConsent(A, true, FEB1, "sig-a"), new LifecycleConsent(B, true, FEB1, "sig-b")),
                ProfitDisposition.RETAIN, List.of());
        service.reviseWithLifecycle(new Commands.ReviseContract(tenant, id, v2, Set.of(A, B), change), change);

        // staff on a fixed wage
        int seq = staffService.engage(tenant, id, new StaffContract(StaffContract.Employer.SYIRKAH, null, "Cashier",
                "12 months", "8 hours a day",
                new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, idr(3_000_000), "monthly"), List.of(),
                false));
        assertEquals(1, seq);

        // second close from trading figures, written in one unit of work with the figures
        TradingResultCalculator.Input in = new TradingResultCalculator.Input(idr(1_000_000), idr(0), idr(0),
                idr(200_000), idr(600_000), idr(0), idr(0), idr(300_000), idr(50_000), idr(0));
        CloseId feb = CloseId.generate();
        uow.inTransaction(() -> {
            service.closePeriod(new Commands.ClosePeriod(tenant, id, feb, FEB1, FEB28,
                    TradingResultCalculator.calculate(in).financialResult(), ManagerFault.none(),
                    ProfitDisposition.RETAIN));
            trading.record(tenant, id, feb, in, clock.instant());
            return null;
        });

        // liquidate
        service.startLiquidation(new Commands.StartLiquidation(tenant, id));
        service.liquidate(new Commands.Liquidate(tenant, id, new LiquidationInput(idr(260_000_000), idr(5_000_000),
                true, FEB28), ManagerFault.none()));

        // everything read back from the database
        Syirkah s = service.get(tenant, id);
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        assertEquals(2, s.closes().size());
        assertEquals(2, s.contractHistory().size());
        assertTrue(s.reconcile().balanced());
        assertEquals(List.of(), s.verifyIntegrity());
        var records = service.lifecycleRecords(tenant, id);
        assertEquals(1, records.size());
        assertEquals(2, records.get(0).contractVersion());
        assertEquals(change, records.get(0).change());
        assertEquals(1, staff.list(tenant, id).size());
        assertEquals(in, trading.find(tenant, id, feb).orElseThrow().input());
        BigDecimal trial = journal.trialBalance("IDR").values().stream().map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, trial.signum(), "the posted journals must net to zero");

        // another tenant sees nothing of it
        TenantId other = TenantId.generate();
        assertThrows(SyirkahNotFoundException.class, () -> service.get(other, id));
        assertTrue(staff.list(other, id).isEmpty());
        assertTrue(trading.list(other, id).isEmpty());
        assertTrue(service.lifecycleRecords(tenant, id).size() == 1);
    }
}
