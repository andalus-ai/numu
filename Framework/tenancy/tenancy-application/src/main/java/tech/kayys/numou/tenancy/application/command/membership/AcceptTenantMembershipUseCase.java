package tech.kayys.numou.tenancy.application.command.membership;

import tech.kayys.numou.tenancy.domain.membership.TenantMembership;
import tech.kayys.numou.tenancy.spi.port.TenantMembershipRepository;

import java.util.Objects;

public final class AcceptTenantMembershipUseCase {

    private final TenantMembershipRepository repository;

    public AcceptTenantMembershipUseCase(TenantMembershipRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(AcceptTenantMembershipCommand command) {
        TenantMembership membership = repository
                .findById(command.tenantId(), command.membershipId())
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        membership.accept();
        repository.save(membership);
    }
}
