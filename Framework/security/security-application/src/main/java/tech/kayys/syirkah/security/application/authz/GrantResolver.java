package tech.kayys.syirkah.security.application.authz;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.application.context.SecurityContext;
import tech.kayys.syirkah.security.domain.authorization.Permission;

import java.util.Set;

/**
 * Where grants come from. The default is {@link RoleGrantResolver} (static role
 * matrix); a database- or IAM-backed implementation (e.g. the identity module's
 * policy store) can replace it without touching callers.
 */
public interface GrantResolver {

    Uni<Set<Permission>> grantsFor(SecurityContext context);
}
