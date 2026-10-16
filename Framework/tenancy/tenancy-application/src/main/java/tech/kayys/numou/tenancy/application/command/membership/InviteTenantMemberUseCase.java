package tech.kayys.syirkah.tenancy.application.command.membership;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembership;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.syirkah.tenancy.domain.tenant.Tenant;
import tech.kayys.syirkah.tenancy.domain.tenant.TenantStatus;
import tech.kayys.syirkah.tenancy.spi.port.TenantMembershipRepository;
import tech.kayys.syirkah.tenancy.spi.port.TenantRepository;

import java.time.Instant;
import java.util.Objects;

/**
 * Invites a User to join a Tenant.
 * Cross-aggregate invariant: the Tenant must be ACTIVE.
 */
public final class InviteTenantMemberUseCase {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;

    public InviteTenantMemberUseCase(TenantRepository tenantRepository,
                                      TenantMembershipRepository membershipRepository) {
        this.tenantRepository    = Objects.requireNonNull(tenantRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
    }

    public TenantMembershipId execute(InviteTenantMemberCommand command) {
        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + command.tenantId()));

        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Cannot invite member to non-ACTIVE tenant");
        }

        if (membershipRepository.exists(command.tenantId(), command.user())) {
            throw new IllegalStateException("User is already a member of this tenant");
        }

        AuditMeta audit = AuditMeta.initial(command.actor(), Instant.now());
        TenantMembership membership = TenantMembership.invite(
                TenantMembershipId.newId(),
                command.tenantId(),
                command.user(),
                command.type(),
                audit
        );

        membershipRepository.save(membership);
        return membership.id();
    }
}
