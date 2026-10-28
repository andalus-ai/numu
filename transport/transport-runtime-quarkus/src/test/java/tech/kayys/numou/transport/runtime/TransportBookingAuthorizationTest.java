package tech.kayys.numou.transport.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.security.domain.authorization.AccessDecision;
import tech.kayys.numou.security.domain.authorization.AccessRequest;
import tech.kayys.numou.security.spi.port.AuthorizationServicePort;
import tech.kayys.numou.security.spi.port.TenantMembershipResolver;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportBookingAuthorizationTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID departureId = UUID.randomUUID();
    private final UUID holdId = UUID.randomUUID();

    @Test
    void requiresMembershipAndExplicitPermissionForOperation() {
        AtomicReference<AccessRequest> observed = new AtomicReference<>();
        TransportBookingAuthorization guard = guard(true, request -> {
            observed.set(request);
            return AccessDecision.allow("policy matched");
        });

        guard.requireAllowed(caller(), "oidc-subject", TransportBookingAuthorization.BookingOperation.CONFIRM_HOLD,
                departureId, holdId);

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().tenantId().value()).isEqualTo(tenantId);
        assertThat(observed.get().principal().id().value()).isEqualTo(actorId);
        assertThat(observed.get().resourceType()).isEqualTo("transport.booking.hold");
        assertThat(observed.get().resourceId()).isEqualTo(holdId.toString());
        assertThat(observed.get().context()).isEqualTo("operation=confirm");
    }

    @Test
    void deniesInactiveTenantMembershipBeforeCallingPolicy() {
        AtomicReference<Boolean> policyCalled = new AtomicReference<>(false);
        TransportBookingAuthorization guard = guard(false, request -> {
            policyCalled.set(true);
            return AccessDecision.allow("must not be reached");
        });

        assertThatThrownBy(() -> guard.requireAllowed(caller(), "oidc-subject",
                TransportBookingAuthorization.BookingOperation.CREATE_HOLD, departureId, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("active tenant membership");
        assertThat(policyCalled).hasValue(false);
    }

    @Test
    void deniesWhenPolicyDoesNotAllowOperation() {
        TransportBookingAuthorization guard = guard(true, request -> AccessDecision.deny("no permission"));

        assertThatThrownBy(() -> guard.requireAllowed(caller(), "oidc-subject",
                TransportBookingAuthorization.BookingOperation.RELEASE_HOLD, departureId, holdId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permission was denied");
    }

    @Test
    void checkoutOperationsUseTheirOwnResourceAndExplicitPolicyContext() {
        AtomicReference<AccessRequest> observed = new AtomicReference<>();
        TransportBookingAuthorization guard = guard(true, request -> {
            observed.set(request);
            return AccessDecision.allow("policy matched");
        });

        guard.requireAllowed(caller(), "oidc-subject",
                TransportBookingAuthorization.BookingOperation.CREATE_CHECKOUT, departureId, null);
        assertThat(observed.get().resourceType()).isEqualTo("transport.booking.checkout");
        assertThat(observed.get().context()).isEqualTo("operation=create");

        guard.requireAllowed(caller(), "oidc-subject",
                TransportBookingAuthorization.BookingOperation.READ_CHECKOUT, null, holdId);
        assertThat(observed.get().resourceType()).isEqualTo("transport.booking.checkout");
        assertThat(observed.get().resourceId()).isEqualTo(holdId.toString());
        assertThat(observed.get().context()).isEqualTo("operation=read");
    }

    @Test
    void rejectsNonUuidTenantOrActorIdentifiers() {
        TransportBookingAuthorization guard = guard(true, request -> AccessDecision.allow("unused"));
        TransportIdentityContext invalid = new TransportIdentityContext("tenant-not-uuid", actorId.toString());

        assertThatThrownBy(() -> guard.requireAllowed(invalid, "oidc-subject",
                TransportBookingAuthorization.BookingOperation.CREATE_HOLD, departureId, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("must be UUIDs");
    }

    private TransportIdentityContext caller() {
        return new TransportIdentityContext(tenantId.toString(), actorId.toString());
    }

    private TransportBookingAuthorization guard(boolean member,
                                                  java.util.function.Function<AccessRequest, AccessDecision> decisions) {
        TransportBookingAuthorization guard = new TransportBookingAuthorization();
        guard.timeoutMillis = 1000;
        guard.memberships = new TenantMembershipResolver() {
            @Override
            public Uni<Boolean> isMember(tech.kayys.numou.security.domain.authorization.Principal principal,
                                         tech.kayys.numou.foundation.domain.tenant.TenantId tenant) {
                return Uni.createFrom().item(member);
            }
        };
        guard.authorization = new AuthorizationServicePort() {
            @Override
            public Uni<AccessDecision> authorize(AccessRequest request) {
                return Uni.createFrom().item(decisions.apply(request));
            }
        };
        return guard;
    }
}
