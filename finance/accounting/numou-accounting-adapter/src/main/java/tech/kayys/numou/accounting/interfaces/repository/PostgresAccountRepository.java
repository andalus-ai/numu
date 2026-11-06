package tech.kayys.numou.accounting.interfaces.repository;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountStatus;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable chart-of-accounts adapter. Every read is constrained by tenant and
 * ledger in SQL; an account identifier can never be used to cross either scope.
 */
@ApplicationScoped
public final class PostgresAccountRepository implements AccountRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, tenant_id, ledger_id, account_code, account_name, description,
                   account_type, status, currency, current_balance, parent_account_id,
                   active, sharia_compliant
              FROM accounting.chart_of_accounts
            """;

    private static final String UPSERT = """
            INSERT INTO accounting.chart_of_accounts
                (id, tenant_id, ledger_id, account_code, account_name, description,
                 account_type, status, currency, current_balance, parent_account_id,
                 active, sharia_compliant)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                account_code = EXCLUDED.account_code,
                account_name = EXCLUDED.account_name,
                description = EXCLUDED.description,
                account_type = EXCLUDED.account_type,
                status = EXCLUDED.status,
                currency = EXCLUDED.currency,
                current_balance = EXCLUDED.current_balance,
                parent_account_id = EXCLUDED.parent_account_id,
                active = EXCLUDED.active,
                sharia_compliant = EXCLUDED.sharia_compliant
            WHERE chart_of_accounts.tenant_id = EXCLUDED.tenant_id
              AND chart_of_accounts.ledger_id = EXCLUDED.ledger_id
            """;

    private final DataSource dataSource;

    @Inject
    public PostgresAccountRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Uni<Optional<Account>> findById(TenantRef tenantId, LedgerId ledgerId, AccountId id) {
        requireScope(tenantId, ledgerId);
        Objects.requireNonNull(id, "id");
        return Uni.createFrom().item(() -> queryOne(
                SELECT_COLUMNS + " WHERE tenant_id = ? AND ledger_id = ? AND id = ?",
                statement -> {
                    statement.setString(1, tenantId.value());
                    statement.setString(2, ledgerId.value());
                    statement.setString(3, id.value().toString());
                }));
    }

    @Override
    public Uni<Optional<Account>> findByAccountNumber(
            TenantRef tenantId, LedgerId ledgerId, String accountNumber) {
        requireScope(tenantId, ledgerId);
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("accountNumber is required");
        }
        return Uni.createFrom().item(() -> queryOne(
                SELECT_COLUMNS + " WHERE tenant_id = ? AND ledger_id = ? AND account_code = ?",
                statement -> {
                    statement.setString(1, tenantId.value());
                    statement.setString(2, ledgerId.value());
                    statement.setString(3, accountNumber);
                }));
    }

    @Override
    public Uni<List<Account>> findAll(TenantRef tenantId, LedgerId ledgerId) {
        requireScope(tenantId, ledgerId);
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         SELECT_COLUMNS + " WHERE tenant_id = ? AND ledger_id = ? ORDER BY account_code, id")) {
                statement.setString(1, tenantId.value());
                statement.setString(2, ledgerId.value());
                try (ResultSet rows = statement.executeQuery()) {
                    List<Account> accounts = new ArrayList<>();
                    while (rows.next()) accounts.add(read(rows));
                    return List.copyOf(accounts);
                }
            } catch (SQLException exception) {
                throw storageFailure("list accounts", tenantId, ledgerId, exception);
            }
        });
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<Account>> findById(AccountId id) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
                "Unscoped account lookup is disabled for PostgreSQL persistence"));
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<Optional<Account>> findByAccountNumber(String accountNumber) {
        return Uni.createFrom().failure(new UnsupportedOperationException(
                "Unscoped account lookup is disabled for PostgreSQL persistence"));
    }

    @Override
    @Deprecated(forRemoval = true)
    public Uni<List<Account>> findAll() {
        return Uni.createFrom().failure(new UnsupportedOperationException(
                "Unscoped account listing is disabled for PostgreSQL persistence"));
    }

    @Override
    public Uni<Void> save(Account account) {
        Objects.requireNonNull(account, "account");
        return Uni.createFrom().item(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, account.id().value().toString());
                statement.setString(2, account.tenantId().value());
                statement.setString(3, account.ledgerId().value());
                statement.setString(4, account.getAccountNumber());
                statement.setString(5, account.getName());
                statement.setString(6, account.getDescription());
                statement.setString(7, account.getAccountType().name());
                statement.setString(8, account.getStatus().name());
                statement.setString(9, account.getCurrentBalance().currency().code());
                statement.setBigDecimal(10, account.getCurrentBalance().amount());
                if (account.getParentAccountId() == null) statement.setNull(11, java.sql.Types.VARCHAR);
                else statement.setString(11, account.getParentAccountId().value().toString());
                statement.setBoolean(12, account.isActive());
                statement.setBoolean(13, account.isShariaCompliant());

                int affected = statement.executeUpdate();
                if (affected != 1) {
                    throw new IllegalStateException("Account ID already belongs to a different tenant or ledger");
                }
                return (Void) null;
            } catch (SQLException exception) {
                throw storageFailure("save account " + account.id(), account.tenantId(), account.ledgerId(), exception);
            }
        });
    }

    private Optional<Account> queryOne(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(read(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load scoped accounting account", exception);
        }
    }

    private static Account read(ResultSet row) throws SQLException {
        String parentId = row.getString("parent_account_id");
        return Account.reconstitute(
                AccountId.of(row.getString("id")),
                TenantRef.of(row.getString("tenant_id")),
                LedgerId.of(row.getString("ledger_id")),
                row.getString("account_code"),
                row.getString("account_name"),
                row.getString("description"),
                AccountType.valueOf(row.getString("account_type")),
                AccountStatus.valueOf(row.getString("status")),
                Money.of(row.getBigDecimal("current_balance"), Currency.of(row.getString("currency"))),
                parentId == null ? null : AccountId.of(parentId),
                row.getBoolean("active"),
                row.getBoolean("sharia_compliant"));
    }

    private static void requireScope(TenantRef tenantId, LedgerId ledgerId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ledgerId, "ledgerId is required");
    }

    private static IllegalStateException storageFailure(
            String operation, TenantRef tenantId, LedgerId ledgerId, SQLException cause) {
        return new IllegalStateException(
                "Unable to " + operation + " for tenant " + tenantId.value() + " and ledger " + ledgerId.value(),
                cause);
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
