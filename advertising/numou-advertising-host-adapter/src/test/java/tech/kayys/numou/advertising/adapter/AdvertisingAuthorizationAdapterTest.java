package tech.kayys.numou.advertising.adapter;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.advertising.domain.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.authorization.*;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.spi.port.AuthorizationServicePort;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AdvertisingAuthorizationAdapterTest {
  @Test void usesVerifiedTenantAndIdentityMembershipBeforeEffectivePermissionCheck() {
    UUID tenantUuid=UUID.randomUUID();
    var foundationTenant=tech.kayys.numou.foundation.domain.tenant.TenantId.of(tenantUuid);
    var principal=Principal.of(PrincipalId.of(UUID.randomUUID()),"operator",foundationTenant);
    var context=new SecurityContext(principal,tenantUuid,Set.of("advertising-admin"),"127.0.0.1");
    CurrentSecurity current=new CurrentSecurity(){@Override public SecurityContext require(){return context;}};
    AtomicReference<AccessRequest> captured=new AtomicReference<>();
    MembershipManagementPort memberships=(tenant,user)->Uni.createFrom().item(true);
    AuthorizationServicePort authorization=request->{captured.set(request);return Uni.createFrom().item(AccessDecision.allow("granted"));};

    AdvertisingAuthorizationAdapter adapter=new AdvertisingAuthorizationAdapter(current,memberships,authorization);
    assertTrue(adapter.mayManageAdvertising(new TenantId(tenantUuid.toString()),"campaign.create")
      .toCompletableFuture().join());
    assertNotNull(captured.get());
    assertEquals("campaign",captured.get().resourceType());
    assertEquals(ActionType.CREATE,captured.get().action());
    assertEquals(foundationTenant,captured.get().tenantId());
  }

  @Test void deniesTenantMismatchUnknownActionAndInactiveMembership() {
    UUID tenantUuid=UUID.randomUUID();
    var foundationTenant=tech.kayys.numou.foundation.domain.tenant.TenantId.of(tenantUuid);
    var principal=Principal.of(PrincipalId.of(UUID.randomUUID()),"operator",foundationTenant);
    var context=new SecurityContext(principal,tenantUuid,Set.of(),"127.0.0.1");
    CurrentSecurity current=new CurrentSecurity(){@Override public SecurityContext require(){return context;}};
    AtomicReference<Boolean> authorizationCalled=new AtomicReference<>(false);
    AuthorizationServicePort authorization=request->{authorizationCalled.set(true);return Uni.createFrom().item(AccessDecision.allow("granted"));};
    MembershipManagementPort inactive=(tenant,user)->Uni.createFrom().item(false);
    AdvertisingAuthorizationAdapter adapter=new AdvertisingAuthorizationAdapter(current,inactive,authorization);

    assertFalse(adapter.mayManageAdvertising(new TenantId(UUID.randomUUID().toString()),"campaign.create").toCompletableFuture().join());
    assertFalse(adapter.mayManageAdvertising(new TenantId(tenantUuid.toString()),"campaign.unknown").toCompletableFuture().join());
    assertFalse(adapter.mayManageAdvertising(new TenantId(tenantUuid.toString()),"campaign.create").toCompletableFuture().join());
    assertFalse(authorizationCalled.get());
  }
}
