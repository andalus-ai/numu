package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.identity.application.port.AuthorizationPolicyRepository;
import tech.kayys.syirkah.identity.application.security.abac.AuthorizationPolicy;
import tech.kayys.syirkah.identity.application.security.abac.PolicyEffect;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresAuthorizationPolicyRepository implements AuthorizationPolicyRepository {
    private final PolicyConditionJsonCodec conditionCodec;

    @Inject
    public PostgresAuthorizationPolicyRepository(PolicyConditionJsonCodec conditionCodec) {
        this.conditionCodec = conditionCodec;
    }

    @Override
    public Uni<List<AuthorizationPolicy>> findActive(String tenantId, String permission) {
        var tenantUuid = parseTenantId(tenantId);
        if (tenantUuid == null || permission == null || permission.isBlank()) {
            return Uni.createFrom().item(List.of());
        }
        return AuthorizationPolicyEntity
                .<AuthorizationPolicyEntity>find(
                        "tenantId = ?1 and permissionCode = ?2 and active = true",
                        tenantUuid,
                        permission
                )
                .list()
                .map(entities -> entities.stream().map(this::toDomain).toList());
    }

    private AuthorizationPolicy toDomain(AuthorizationPolicyEntity entity) {
        return new AuthorizationPolicy(
                entity.id,
                entity.tenantId.toString(),
                entity.permissionCode,
                PolicyEffect.valueOf(entity.effect),
                conditionCodec.decode(entity.conditionJson),
                entity.active,
                entity.version
        );
    }

    private static UUID parseTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException invalidTenantId) {
            return null;
        }
    }
}
