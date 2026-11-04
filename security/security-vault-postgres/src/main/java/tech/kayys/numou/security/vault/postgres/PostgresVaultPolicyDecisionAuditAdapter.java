package tech.kayys.numou.security.vault.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.security.vault.spi.VaultPolicyDecisionAudit;
import tech.kayys.numou.security.vault.spi.VaultPolicyDecisionAuditPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/** Durable, append-only PostgreSQL policy decision audit adapter. */
public final class PostgresVaultPolicyDecisionAuditAdapter implements VaultPolicyDecisionAuditPort {
    private final DataSource dataSource;

    public PostgresVaultPolicyDecisionAuditAdapter(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Uni<Void> append(VaultPolicyDecisionAudit audit) {
        Objects.requireNonNull(audit, "audit is required");
        return Uni.createFrom().item(() -> {
            var decision = audit.decision();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vault_policy_decision_audit
                        (event_id, tenant_id, policy_revision, subject_reference, vault_id, secret_id,
                         operation, effect, matched_rule_id, reason_code, correlation_id, evaluated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setObject(1, audit.eventId());
                statement.setObject(2, decision.tenantId());
                statement.setLong(3, decision.policyVersion());
                statement.setString(4, audit.subjectReference());
                statement.setObject(5, audit.vaultId().value());
                statement.setObject(6, audit.secretId().value());
                statement.setString(7, decision.operation().name());
                statement.setString(8, decision.effect().name());
                if (decision.matchedRuleId().isPresent())
                    statement.setObject(9, decision.matchedRuleId().orElseThrow());
                else statement.setNull(9, java.sql.Types.OTHER);
                statement.setString(10, decision.reasonCode());
                if (audit.correlationId() == null) statement.setNull(11, java.sql.Types.VARCHAR);
                else statement.setString(11, audit.correlationId());
                statement.setTimestamp(12, Timestamp.from(decision.evaluatedAt()));
                if (statement.executeUpdate() != 1) throw new SQLException("decision audit insert did not affect one row");
                return (Void) null;
            } catch (SQLException failure) {
                throw new VaultPersistenceException("Unable to persist vault policy decision audit", failure);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
