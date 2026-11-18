package tech.kayys.numou.notification.adapter.security;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.adapter.quarkus.VerifiedNotificationPrincipalProvider;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.identifier.PrincipalId;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumouVerifiedNotificationPrincipalProviderTest {

    @Test
    void exposesOnlyTenantAndPrincipalFromVerifiedSecurityContext() {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID principalId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        SecurityContext verified = new SecurityContext(
                Principal.of(PrincipalId.of(principalId), "verified-user", tenantId),
                tenantId,
                Set.of("user"),
                "127.0.0.1");
        CurrentSecurity current = new CurrentSecurity() {
            @Override
            public SecurityContext require() {
                return verified;
            }
        };

        var result = new NumouVerifiedNotificationPrincipalProvider(current).current();

        assertEquals(Optional.of(new VerifiedNotificationPrincipalProvider.VerifiedPrincipal(
                tenantId, principalId)), result);
    }

    @Test
    void returnsEmptyWhenSecurityFilterHasNotEstablishedARequestContext() {
        CurrentSecurity current = new CurrentSecurity() {
            @Override
            public SecurityContext require() {
                throw new IllegalStateException("No authenticated security context on this request");
            }
        };

        assertTrue(new NumouVerifiedNotificationPrincipalProvider(current).current().isEmpty());
    }
}
