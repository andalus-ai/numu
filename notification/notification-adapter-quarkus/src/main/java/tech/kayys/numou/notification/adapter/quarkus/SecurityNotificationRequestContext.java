package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.notification.application.port.NotificationRequestContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Adapts the host's verified security identity and tenant-scoped identity
 * mapping to the transport-neutral notification request context.
 */
@ApplicationScoped
public class SecurityNotificationRequestContext implements NotificationRequestContext {
    private final VerifiedNotificationPrincipalProvider principals;
    private final NotificationRecipientResolver recipients;

    @Inject
    public SecurityNotificationRequestContext(
            VerifiedNotificationPrincipalProvider principals,
            NotificationRecipientResolver recipients) {
        this.principals = Objects.requireNonNull(principals, "principals is required");
        this.recipients = Objects.requireNonNull(recipients, "recipients is required");
    }

    @Override
    public Optional<Actor> currentActor() {
        return principals.current().flatMap(principal ->
                recipients.resolveRecipient(principal.tenantId(), principal.principalId())
                        .map(recipientId -> new Actor(principal.tenantId(), recipientId)));
    }
}
