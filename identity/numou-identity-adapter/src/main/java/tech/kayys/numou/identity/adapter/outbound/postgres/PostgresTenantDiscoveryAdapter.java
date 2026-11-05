package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.identity.application.port.TenantDiscoveryPort;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.UUID;

/** Reads tenant choices from Identity's authoritative tenant_memberships table. */
@ApplicationScoped
public class PostgresTenantDiscoveryAdapter implements TenantDiscoveryPort {
    @Override
    public Uni<List<UUID>> activeTenantIds(UserId userId) {
        if (userId == null) {
            return Uni.createFrom().item(List.of());
        }
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(
                                "select m.tenantId from TenantMembershipEntity m, UserEntity u "
                                        + "where m.userId = :userId and m.status = 'ACTIVE' "
                                        + "and u.id = m.userId and u.status = 'ACTIVE' "
                                        + "order by m.tenantId",
                                UUID.class
                        )
                        .setParameter("userId", userId.value())
                        .getResultList());
    }
}
