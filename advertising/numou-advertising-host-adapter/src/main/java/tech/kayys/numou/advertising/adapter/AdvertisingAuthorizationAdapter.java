package tech.kayys.numou.advertising.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.advertising.application.AdvertisingAuthorizationPort;
import tech.kayys.numou.advertising.domain.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.domain.authorization.AccessDecision;
import tech.kayys.numou.security.domain.authorization.AccessRequest;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.spi.port.AuthorizationServicePort;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Resolves the actor and tenant only from the verified request context, then
 * checks current Identity membership and effective Security permissions.
 * Invalid or absent request context is denied.
 */
@ApplicationScoped
public class AdvertisingAuthorizationAdapter implements AdvertisingAuthorizationPort {
  private final CurrentSecurity currentSecurity;
  private final MembershipManagementPort memberships;
  private final AuthorizationServicePort authorization;

  @Inject
  public AdvertisingAuthorizationAdapter(CurrentSecurity currentSecurity,MembershipManagementPort memberships,
                                          AuthorizationServicePort authorization) {
    this.currentSecurity=Objects.requireNonNull(currentSecurity);
    this.memberships=Objects.requireNonNull(memberships);
    this.authorization=Objects.requireNonNull(authorization);
  }

  @Override
  public CompletionStage<Boolean> mayManageAdvertising(TenantId tenantId,String action) {
    final UUID tenantUuid;
    final tech.kayys.numou.security.application.context.SecurityContext context;
    final PermissionTarget target;
    try {
      tenantUuid=UUID.fromString(tenantId.value());
      context=currentSecurity.require();
      target=PermissionTarget.forAction(action);
    } catch (IllegalArgumentException | IllegalStateException | NullPointerException invalidContext) {
      return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    if(!tenantUuid.equals(context.tenantId())) {
      return java.util.concurrent.CompletableFuture.completedFuture(false);
    }
    tech.kayys.numou.foundation.domain.tenant.TenantId trustedTenant=tech.kayys.numou.foundation.domain.tenant.TenantId.of(tenantUuid);
    if(!context.principal().isMemberOf(trustedTenant)) {
      return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    final UserId userId;
    try {
      userId=UserId.of(context.principal().id().value());
    } catch(RuntimeException invalidPrincipal) {
      return java.util.concurrent.CompletableFuture.completedFuture(false);
    }

    AccessRequest request=AccessRequest.of(context.principal(),trustedTenant,target.action(),target.resource());
    return memberships.isActive(trustedTenant,userId)
      .flatMap(active->active
        ? authorization.authorize(request).map(AccessDecision::allowed)
        : Uni.createFrom().item(false))
      .subscribeAsCompletionStage();
  }

  private record PermissionTarget(String resource,ActionType action) {
    private static final Map<String,PermissionTarget> ACTIONS=Map.ofEntries(
      Map.entry("advertiser.register",new PermissionTarget("advertiser",ActionType.CREATE)),
      Map.entry("advertiser.activate",new PermissionTarget("advertiser",ActionType.PERFORM)),
      Map.entry("publisher.register",new PermissionTarget("publisher",ActionType.CREATE)),
      Map.entry("publisher.activate",new PermissionTarget("publisher",ActionType.PERFORM)),
      Map.entry("publisher.inventory.manage",new PermissionTarget("publisher-inventory",ActionType.UPDATE)),
      Map.entry("publisher.inventory.verify",new PermissionTarget("publisher-inventory",ActionType.PERFORM)),
      Map.entry("campaign.create",new PermissionTarget("campaign",ActionType.CREATE)),
      Map.entry("campaign.manage",new PermissionTarget("campaign",ActionType.UPDATE)),
      Map.entry("creative.create",new PermissionTarget("creative",ActionType.CREATE)),
      Map.entry("creative.submit",new PermissionTarget("creative",ActionType.UPDATE)),
      Map.entry("creative.review",new PermissionTarget("creative",ActionType.PERFORM)),
      Map.entry("creative.manage",new PermissionTarget("creative",ActionType.UPDATE))
    );
    static PermissionTarget forAction(String action) {
      PermissionTarget target=ACTIONS.get(action);
      if(target==null) throw new IllegalArgumentException("unknown advertising authorization action");
      return target;
    }
  }
}
