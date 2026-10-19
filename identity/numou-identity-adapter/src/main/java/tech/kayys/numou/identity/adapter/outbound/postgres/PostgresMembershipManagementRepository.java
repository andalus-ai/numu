package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.domain.user.UserId;

@ApplicationScoped
public class PostgresMembershipManagementRepository implements MembershipManagementPort {
    @Override
    public Uni<Boolean> isActive(TenantId tenantId, UserId userId) {
        return Panache.getSession()
                .chain(session -> session.createSelectionQuery(
                                "select count(m.id) from TenantMembershipEntity m, UserEntity u "
                                        + "where m.tenantId = :tenantId and m.userId = :userId "
                                        + "and m.status = 'ACTIVE' and u.id = m.userId and u.status = 'ACTIVE'",
                                Long.class
                        )
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("userId", userId.value())
                        .getSingleResult())
                .map(count -> count > 0);
    }
}
