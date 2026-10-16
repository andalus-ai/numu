package tech.kayys.syirkah.restaurant.api;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.observability.MetricsPort;
import tech.kayys.syirkah.observability.TracingPort;
import tech.kayys.syirkah.observability.application.ObservedHandlers;
import tech.kayys.syirkah.security.adapter.CurrentSecurity;
import tech.kayys.syirkah.security.application.authz.AuthorizationService;
import tech.kayys.syirkah.security.application.context.SecurityContext;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.function.Function;

/**
 * The one door every resource method goes through: authorise (deny by default), then run the use case inside a span
 * with duration/outcome metrics. Keeps resources to a single expression and makes "forgot the permission check"
 * structurally harder than doing it.
 */
@ApplicationScoped
public class Secured {

    @Inject
    CurrentSecurity current;
    @Inject
    AuthorizationService authz;
    @Inject
    MetricsPort metrics;
    @Inject
    TracingPort tracing;

    public <T> Uni<T> run(String useCase, ActionType action, String resource, Function<SecurityContext, Uni<T>> work) {
        return ObservedHandlers.useCase(useCase, metrics, tracing, () -> {
            final SecurityContext ctx = current.require();
            return authz.require(ctx, action, resource).flatMap(x -> work.apply(ctx));
        });
    }

    /** Same, but a second permission is required for an elevated option (e.g. waiving a fee) and is audited. */
    public <T> Uni<T> runElevated(String useCase, ActionType action, String resource, boolean elevated, String elevatedResource,
                                  String auditDetail, Function<SecurityContext, Uni<T>> work) {
        return run(useCase, action, resource, ctx -> {
            if (!elevated) {
                return work.apply(ctx);
            }
            return authz.require(ctx, ActionType.PERFORM, elevatedResource)
                    .flatMap(x -> work.apply(ctx))
                    .flatMap(result -> authz.auditPerformed(ctx, useCase + ":ELEVATED", elevatedResource, "", auditDetail).replaceWith(result));
        });
    }
}
