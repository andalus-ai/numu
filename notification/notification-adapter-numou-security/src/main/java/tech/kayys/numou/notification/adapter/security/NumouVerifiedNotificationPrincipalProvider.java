package tech.kayys.numou.notification.adapter.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.notification.adapter.quarkus.VerifiedNotificationPrincipalProvider;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.application.context.SecurityContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Bridges the verified request context populated by Numou's RequestSecurityFilter.
 *
 * <p>This adapter never reads request headers, JWT claims, or client DTOs. The
 * tenant and principal are accepted only from CurrentSecurity after token
 * validation and tenant resolution have completed.
 */
@ApplicationScoped
public class NumouVerifiedNotificationPrincipalProvider
        implements VerifiedNotificationPrincipalProvider {

    private final CurrentSecurity currentSecurity;

    @Inject
    public NumouVerifiedNotificationPrincipalProvider(CurrentSecurity currentSecurity) {
        this.currentSecurity = Objects.requireNonNull(currentSecurity, "currentSecurity is required");
    }

    @Override
    public Optional<VerifiedPrincipal> current() {
        final SecurityContext verified;
        try {
            verified = currentSecurity.require();
        } catch (IllegalStateException noVerifiedRequestContext) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedPrincipal(
                verified.tenantId(),
                verified.principal().id().value()));
    }
}
