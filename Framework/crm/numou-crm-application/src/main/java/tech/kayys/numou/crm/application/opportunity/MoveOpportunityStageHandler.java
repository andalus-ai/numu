package tech.kayys.numou.crm.application.opportunity;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.MoveOpportunityStageCommand;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;

/**
 * Moves an {@link Opportunity} to a new stage. The aggregate rejects
 * moves out of a terminal stage with {@link IllegalStateException},
 * which the REST adapter maps to 409.
 */
public final class MoveOpportunityStageHandler implements CommandHandler<MoveOpportunityStageCommand, Void> {

    private final OpportunityRepository opportunityRepository;

    public MoveOpportunityStageHandler(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository cannot be null");
    }

    @Override
    public Uni<Void> handle(MoveOpportunityStageCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(opportunityRepository.findById(command.opportunityId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Opportunity not found: " + command.opportunityId())))
                .flatMap(opportunity -> {
                    opportunity.moveStage(command.newStage());
                    return Uni.createFrom().completionStage(opportunityRepository.save(opportunity));
                })
                .flatMap(saved -> Uni.createFrom().voidItem());
    }
}
