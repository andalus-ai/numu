package tech.kayys.numou.developer.apicatalog.integration.identity;

import static org.assertj.core.api.Assertions.*;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.application.context.ActorId;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.*;
import tech.kayys.numou.identity.application.security.*;
import tech.kayys.numou.identity.application.security.abac.*;
import tech.kayys.numou.identity.application.service.ResourceAuthorizationService;

import java.time.Instant;
import java.util.*;

class IdentityApiCatalogAuthorizationAdapterTest {
 @Test void requiresMatchingAuthenticatedActorTenantMembershipAndCatalogPermission(){
  TenantId tenant=TenantId.generate();
  Principal principal=new Principal(null,"actor-1",null,null,Set.of(),Map.of(),PrincipalType.SERVICE);
  var current=(CurrentPrincipalPort)()->Uni.createFrom().item(principal);
  TenantMembershipPort membership=new TenantMembershipPort(){
   public Uni<Boolean> isMember(Principal p,String t){return Uni.createFrom().item(p==principal&&t.equals(tenant.value().toString()));}
   public Uni<Set<String>> roles(Principal p,String t){return Uni.createFrom().item(Set.of());}
  };
  var requirementSeen=new java.util.concurrent.atomic.AtomicReference<AuthorizationRequirement>();
  AuthorizationPort rbac=(p,t,r)->{requirementSeen.set(r);return Uni.createFrom().item(true);};
  ResourceAttributePort attributes=(p,t,r,id)->Uni.createFrom().item(new AuthorizationContext(t,r.permissionName(),Map.of(),Map.of(),Map.of()));
  AuthorizationPolicyRepository policies=(t,p)->Uni.createFrom().item(List.of());
  var resourceAuthorization=new ResourceAuthorizationService(rbac,attributes,policies,new PolicyEvaluator());
  var adapter=new IdentityApiCatalogAuthorizationAdapter(current,membership,resourceAuthorization);
  var context=new ExecutionContext(tenant,null,null,ActorId.of("actor-1"),null,null,Instant.parse("2026-11-04T10:00:00Z"),null,null,null);
  adapter.requireCanManage(context,tenant).await().indefinitely();
  assertThat(requirementSeen.get().permissionName()).isEqualTo("developer.api-catalog.manage");
 }

 @Test void failsClosedWhenExecutionActorDoesNotMatchAuthenticatedPrincipal(){
  TenantId tenant=TenantId.generate();
  Principal principal=new Principal(null,"real-subject",null,null,Set.of(),Map.of(),PrincipalType.SERVICE);
  var current=(CurrentPrincipalPort)()->Uni.createFrom().item(principal);
  TenantMembershipPort membership=new TenantMembershipPort(){
   public Uni<Boolean> isMember(Principal p,String t){return Uni.createFrom().item(true);}
   public Uni<Set<String>> roles(Principal p,String t){return Uni.createFrom().item(Set.of());}
  };
  AuthorizationPort rbac=(p,t,r)->Uni.createFrom().item(true);
  ResourceAttributePort attributes=(p,t,r,id)->Uni.createFrom().item(new AuthorizationContext(t,r.permissionName(),Map.of(),Map.of(),Map.of()));
  AuthorizationPolicyRepository policies=(t,p)->Uni.createFrom().item(List.of());
  var service=new ResourceAuthorizationService(rbac,attributes,policies,new PolicyEvaluator());
  var adapter=new IdentityApiCatalogAuthorizationAdapter(current,membership,service);
  var context=new ExecutionContext(tenant,null,null,ActorId.of("spoofed-subject"),null,null,Instant.parse("2026-11-04T10:00:00Z"),null,null,null);
  assertThatThrownBy(()->adapter.requireCanRead(context,tenant).await().indefinitely())
    .isInstanceOf(RuntimeException.class);
 }
}
