package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.vault.spi.VaultPolicyMetricsPort;
import tech.kayys.numou.security.vault.spi.VaultPolicyOperationalMetrics;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Authorized, bounded policy operational metrics query. */
public final class VaultPolicyMetricsService {
    private static final Duration MAX_WINDOW = Duration.ofDays(7);
    private final AuthorizationService authorization;
    private final VaultPolicyMetricsPort metrics;
    private final Clock clock;

    public VaultPolicyMetricsService(AuthorizationService authorization,
            VaultPolicyMetricsPort metrics, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.metrics = Objects.requireNonNull(metrics, "metrics is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public Uni<VaultPolicyOperationalMetrics> read(SecurityContext context, Duration window) {
        Objects.requireNonNull(context, "context is required");
        Objects.requireNonNull(window, "window is required");
        if (window.isNegative() || window.isZero() || window.compareTo(MAX_WINDOW) > 0)
            return Uni.createFrom().failure(new IllegalArgumentException("metrics window must be within (0, 7 days]"));
        var measuredAt = clock.instant();
        var windowStart = measuredAt.minus(window);
        return authorization.require(context, ActionType.READ, "vault-policy-metrics",
                        context.tenantId().toString())
                .flatMap(ignored -> metrics.load(context.tenantId(), windowStart, measuredAt));
    }
}
