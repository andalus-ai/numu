package tech.kayys.numou.identity.adapter.outbound.health;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.observability.HealthProbe;
import tech.kayys.numou.observability.HealthStatus;

import java.util.Objects;

/**
 * Readiness probe for the reactive PostgreSQL connection pool: opens and closes one
 * connection, so it proves the pool, the network path and the database itself.
 */
@ApplicationScoped
public class DatabaseHealthProbe implements HealthProbe {

    private final Pool pool;

    @Inject
    public DatabaseHealthProbe(Pool pool) {
        this.pool = Objects.requireNonNull(pool);
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public Uni<HealthStatus> probe() {
        return pool.getConnection()
                .onItem().transformToUni(connection -> connection.close()
                        .replaceWith(HealthStatus.up(name())))
                .onFailure().recoverWithItem(ex -> HealthStatus.down(name(), safeMessage(ex)));
    }

    /** Never leak connection strings or credentials that JDBC drivers sometimes embed. */
    private static String safeMessage(Throwable ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String sanitized = message.replaceAll("(?i)(password|user(name)?|sslmode)=[^\\s;]*", "$1=REDACTED");
        return sanitized.length() > 200 ? sanitized.substring(0, 200) : sanitized;
    }
}
