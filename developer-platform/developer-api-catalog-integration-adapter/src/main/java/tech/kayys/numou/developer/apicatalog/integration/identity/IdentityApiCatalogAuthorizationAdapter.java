package tech.kayys.numou.developer.apicatalog.integration.identity;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.developer.apicatalog.application.port.ApiCatalogAuthorizationPort;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.AuthorizationException;
import tech.kayys.numou.identity.application.port.CurrentPrincipalPort;
import tech.kayys.numou.identity.application.port.TenantMembershipPort;
import tech.kayys.numou.identity.application.security.AuthorizationRequirement;
import tech.kayys.numou.identity.application.security.Principal;
import tech.kayys.numou.identity.application.service.ResourceAuthorizationService;

import java.util.Objects;

/**
 * Bridges catalog authorization to Numou Identity's tenant membership, RBAC and ABAC services.
 * The authenticated principal must match the actor captured in the trusted execution context.
 */
@ApplicationScoped
public final class IdentityApiCatalogAuthorizationAdapter implements ApiCatalogAuthorizationPort {
 private static final String RESOURCE="developer.api-catalog";
 private final CurrentPrincipalPort principals;
 private final TenantMembershipPort memberships;
 private final ResourceAuthorizationService authorization;

 @Inject public IdentityApiCatalogAuthorizationAdapter(CurrentPrincipalPort principals,TenantMembershipPort memberships,
                                                ResourceAuthorizationService authorization){
  this.principals=Objects.requireNonNull(principals,"principals cannot be null");
  this.memberships=Objects.requireNonNull(memberships,"memberships cannot be null");
  this.authorization=Objects.requireNonNull(authorization,"authorization cannot be null");
 }

 @Override public Uni<Void> requireCanManage(ExecutionContext context,TenantId tenant){
  return require(context,tenant,"manage");
 }
 @Override public Uni<Void> requireCanRead(ExecutionContext context,TenantId tenant){
  return require(context,tenant,"read");
 }

 private Uni<Void> require(ExecutionContext context,TenantId tenant,String action){
  Objects.requireNonNull(context,"context cannot be null");
  Objects.requireNonNull(tenant,"tenant cannot be null");
  if(context.tenantId()==null||!context.tenantId().equals(tenant)){
   return denied("developer.api-catalog.tenant-context");
  }
  return principals.current().flatMap(principal->{
   if(!authenticatedActorMatches(context,principal))return denied("developer.api-catalog.actor-context");
   String tenantId=tenant.value().toString();
   return memberships.isMember(principal,tenantId).flatMap(member->{
    if(!Boolean.TRUE.equals(member))return denied("tenant.membership");
    AuthorizationRequirement requirement=AuthorizationRequirement.permission(RESOURCE,action);
    return authorization.isAllowed(principal,tenantId,requirement,RESOURCE+":"+tenantId)
      .flatMap(allowed->Boolean.TRUE.equals(allowed)?Uni.createFrom().voidItem():denied(requirement.permissionName()));
   });
  });
 }

 private static boolean authenticatedActorMatches(ExecutionContext context,Principal principal){
  if(principal==null||!principal.authenticated()||context.actorId()==null)return false;
  String actor=context.actorId().value();
  if(actor.equals(principal.subject()))return true;
  return principal.type()==tech.kayys.numou.identity.application.security.PrincipalType.USER
    &&principal.userId()!=null&&actor.equals(principal.userId().value().toString());
 }
 private static Uni<Void> denied(String permission){
  return Uni.createFrom().failure(new AuthorizationException(permission));
 }
}
