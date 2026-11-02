package tech.kayys.numou.notification.adapter.quarkus;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationRequestContext;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityNotificationRequestContextTest {
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRINCIPAL = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID RECIPIENT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void mapsVerifiedPrincipalThroughExplicitTenantScopedRecipientResolver() {
        var provider = (VerifiedNotificationPrincipalProvider) () ->
                Optional.of(new VerifiedNotificationPrincipalProvider.VerifiedPrincipal(TENANT, PRINCIPAL));
        var resolver = (NotificationRecipientResolver) (tenant, principal) -> {
            assertEquals(TENANT, tenant);
            assertEquals(PRINCIPAL, principal);
            return Optional.of(RECIPIENT);
        };

        NotificationRequestContext context = new SecurityNotificationRequestContext(provider, resolver);

        assertEquals(Optional.of(new NotificationRequestContext.Actor(TENANT, RECIPIENT)),
                context.currentActor());
    }

    @Test
    void doesNotInventActorWhenRequestIsAnonymous() {
        var provider = (VerifiedNotificationPrincipalProvider) Optional::empty;
        var resolver = (NotificationRecipientResolver) (tenant, principal) -> {
            fail("recipient resolver must not run for anonymous request");
            return Optional.empty();
        };

        NotificationRequestContext context = new SecurityNotificationRequestContext(provider, resolver);

        assertTrue(context.currentActor().isEmpty());
    }

    @Test
    void doesNotTreatPrincipalIdAsRecipientWhenMappingIsMissing() {
        var provider = (VerifiedNotificationPrincipalProvider) () ->
                Optional.of(new VerifiedNotificationPrincipalProvider.VerifiedPrincipal(TENANT, PRINCIPAL));
        var resolver = (NotificationRecipientResolver) (tenant, principal) -> Optional.empty();

        NotificationRequestContext context = new SecurityNotificationRequestContext(provider, resolver);

        assertTrue(context.currentActor().isEmpty());
    }
}
