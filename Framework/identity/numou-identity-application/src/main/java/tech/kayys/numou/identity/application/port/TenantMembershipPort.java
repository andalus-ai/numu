package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.security.Principal;

import java.util.Set;

public interface TenantMembershipPort {
    Uni<Boolean> isMember(Principal principal, String tenantId);

    Uni<Set<String>> roles(Principal principal, String tenantId);
}
