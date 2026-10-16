package tech.kayys.syirkah.tenancy.application.command.membership;

import tech.kayys.syirkah.tenancy.domain.membership.TenantMembership;
import tech.kayys.syirkah.tenancy.spi.port.TenantMembershipRepository;

import java.util.Objects;

public final class RemoveTenantMemberUseCase {

    private final TenantMembershipRepository repository;

    public RemoveTenantMemberUseCase(TenantMembershipRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(RemoveTenantMemberCommand command) {
        TenantMembership membership = repository
                .findById(command.tenantId(), command.membershipId())
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        membership.remove();
        repository.save(membership);
    }
}
