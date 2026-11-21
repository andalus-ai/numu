package tech.kayys.numou.accounting.interfaces.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.postgresql.ds.PGSimpleDataSource;
import org.flywaydb.core.Flyway;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresAccountRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static PGSimpleDataSource dataSource;
    PostgresAccountRepository repository;

    @BeforeAll
    static void migrateSchema() {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:src/main/resources/db/migration")
                .schemas("accounting")
                .defaultSchema("accounting")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() throws Exception {
        repository = new PostgresAccountRepository(dataSource);
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE accounting.chart_of_accounts CASCADE");
        }
    }

    @Test
    void persistsAndReconstitutesCompleteAccountState() {
        TenantRef tenant = TenantRef.of("tenant-a");
        LedgerId ledger = LedgerId.of("ifrs");
        Account account = account(tenant, ledger, AccountId.generate(), "1000");
        account.setDescription("Operating cash");
        account.setShariaCompliant(false);
        account.debit(Money.of(new BigDecimal("125.50"), "IDR"));

        repository.save(account).await().indefinitely();

        Account loaded = repository.findById(tenant, ledger, account.id())
                .await().indefinitely().orElseThrow();
        assertEquals("1000", loaded.getAccountNumber());
        assertEquals("Operating cash", loaded.getDescription());
        assertEquals(new BigDecimal("125.50"), loaded.getCurrentBalance().amount());
        assertEquals("IDR", loaded.getCurrentBalance().currency().code());
        assertFalse(loaded.isShariaCompliant());
        assertEquals(tenant, loaded.tenantId());
        assertEquals(ledger, loaded.ledgerId());
    }

    @Test
    void readsAreIsolatedByTenantAndLedgerAtTheDatabaseBoundary() {
        TenantRef tenantA = TenantRef.of("tenant-a");
        TenantRef tenantB = TenantRef.of("tenant-b");
        LedgerId primary = LedgerId.primary();
        LedgerId tax = LedgerId.of("tax");
        Account tenantAPrimary = account(tenantA, primary, AccountId.generate(), "1000");
        Account tenantATax = account(tenantA, tax, AccountId.generate(), "1000");
        Account tenantBPrimary = account(tenantB, primary, AccountId.generate(), "1000");

        repository.save(tenantAPrimary).await().indefinitely();
        repository.save(tenantATax).await().indefinitely();
        repository.save(tenantBPrimary).await().indefinitely();

        assertTrue(repository.findById(tenantB, primary, tenantAPrimary.id())
                .await().indefinitely().isEmpty());
        assertTrue(repository.findById(tenantA, tax, tenantAPrimary.id())
                .await().indefinitely().isEmpty());
        assertEquals(1, repository.findAll(tenantA, primary).await().indefinitely().size());
        assertEquals(1, repository.findAll(tenantA, tax).await().indefinitely().size());
        assertEquals(1, repository.findAll(tenantB, primary).await().indefinitely().size());
        assertTrue(repository.findByAccountNumber(tenantB, primary, "1000")
                .await().indefinitely().isPresent());
    }

    @Test
    void refusesToMoveAnAccountIdAcrossTenantOrLedger() {
        Account original = account(TenantRef.of("tenant-a"), LedgerId.primary(), AccountId.generate(), "1000");
        repository.save(original).await().indefinitely();

        Account attemptedMove = account(TenantRef.of("tenant-b"), LedgerId.primary(), original.id(), "1000");
        assertThrows(IllegalStateException.class,
                () -> repository.save(attemptedMove).await().indefinitely());

        assertTrue(repository.findById(original.tenantId(), original.ledgerId(), original.id())
                .await().indefinitely().isPresent());
        assertTrue(repository.findById(attemptedMove.tenantId(), attemptedMove.ledgerId(), attemptedMove.id())
                .await().indefinitely().isEmpty());
    }

    @Test
    void rejectsUnscopedCompatibilityMethods() {
        assertThrows(UnsupportedOperationException.class,
                () -> repository.findAll().await().indefinitely());
        assertThrows(UnsupportedOperationException.class,
                () -> repository.findById(AccountId.generate()).await().indefinitely());
    }

    private static Account account(TenantRef tenant, LedgerId ledger, AccountId id, String number) {
        return Account.reconstitute(id, tenant, ledger, number, "Cash", null,
                AccountType.ASSET,
                tech.kayys.numou.accounting.domain.valueobject.AccountStatus.ACTIVE,
                Money.zero(Currency.IDR()), null, true, true);
    }
}
