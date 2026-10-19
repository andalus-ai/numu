package tech.kayys.numou.security.application.authz;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.authorization.Permission;

import java.util.Objects;
import java.util.Set;

/** Resolves grants from the roles in the verified token via a {@link RolePermissionMatrix}. */
public final class RoleGrantResolver implements GrantResolver {

    private final RolePermissionMatrix matrix;

    public RoleGrantResolver(RolePermissionMatrix matrix) {
        this.matrix = Objects.requireNonNull(matrix, "matrix cannot be null");
    }

    @Override
    public Uni<Set<Permission>> grantsFor(SecurityContext context) {
        return Uni.createFrom().item(() -> matrix.permissionsFor(context.roles()));
    }
}
