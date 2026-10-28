package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.ProgramMembership;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped membership contract; implementations must enforce unique tenant/program/partner membership atomically. */
public interface ProgramMembershipRepository {
    CompletionStage<ProgramMembership> save(ProgramMembership membership);
    CompletionStage<Optional<ProgramMembership>> findByProgramAndPartner(
            String tenantId, UUID programId, UUID partnerId);
}
