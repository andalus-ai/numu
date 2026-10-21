package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.numou.identity.application.port.SamlReplayPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Postgres-backed SAML assertion replay store.
 *
 * <p>Uses an {@code INSERT … ON CONFLICT DO NOTHING} to make the check-and-set operation
 * <b>atomic</b>: the first caller to present an {@code assertionId} inserts a row and
 * receives {@code true}; all subsequent callers get a conflict and receive {@code false}.
 *
 * <p>A scheduled Postgres job (or TTL-based index) should purge rows older than the
 * maximum assertion window (typically 8 hours).  The table DDL:
 *
 * <pre>{@code
 * CREATE TABLE identity_saml_replay (
 *     assertion_id  TEXT        NOT NULL PRIMARY KEY,
 *     expires_at    TIMESTAMPTZ NOT NULL,
 *     consumed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
 * );
 * CREATE INDEX idx_saml_replay_expires ON identity_saml_replay (expires_at);
 * }</pre>
 */
@ApplicationScoped
public class PostgresSamlReplayStore implements SamlReplayPort {

    private static final Logger LOG = Logger.getLogger(PostgresSamlReplayStore.class);

    private static final String INSERT_OR_IGNORE = """
            INSERT INTO identity_saml_replay (assertion_id, expires_at)
            VALUES (?, ?)
            ON CONFLICT (assertion_id) DO NOTHING
            """;

    private static final String DELETE_EXPIRED =
            "DELETE FROM identity_saml_replay WHERE expires_at < now()";

    private final DataSource dataSource;
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    public PostgresSamlReplayStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Uni<Boolean> tryConsume(String assertionId, Instant expiresAt) {
        if (assertionId == null || assertionId.isBlank() || expiresAt == null) {
            return Uni.createFrom().item(false);
        }
        return Uni.createFrom().<Boolean>emitter(em -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_OR_IGNORE)) {
                stmt.setString(1, assertionId);
                stmt.setTimestamp(2, Timestamp.from(expiresAt));
                int affected = stmt.executeUpdate();
                // affected == 1 → row inserted (first consumption) → allow
                // affected == 0 → conflict (already consumed) → replay
                em.complete(affected == 1);
            } catch (SQLException ex) {
                LOG.errorf(ex, "SAML replay store write failed for assertionId=%s", assertionId);
                // Fail-safe: treat DB errors as replay to prevent security bypass
                em.complete(false);
            }
        }).runSubscriptionOn(executor);
    }

    /**
     * Purges expired assertion IDs.  Should be invoked periodically (e.g. every hour).
     * Callers may ignore the returned count; it is provided for monitoring only.
     */
    public Uni<Integer> purgeExpired() {
        return Uni.createFrom().<Integer>emitter(em -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_EXPIRED)) {
                em.complete(stmt.executeUpdate());
            } catch (SQLException ex) {
                LOG.warn("SAML replay store purge failed", ex);
                em.complete(0);
            }
        }).runSubscriptionOn(executor);
    }
}
