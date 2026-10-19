package tech.kayys.numou.crm.application.opportunity;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.UpdateOpportunityCommand;
import tech.kayys.numou.crm.domain.model.Opportunity;
import tech.kayys.numou.crm.domain.repository.OpportunityRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;

/**
 * Applies an update to an existing {@link Opportunity}. Currency is
 * fixed at creation (the aggregate exposes no currency mutator), so the
 * command's currencyCode is validated but not re-applied here.
 */
public final class UpdateOpportunityHandler implements CommandHandler<UpdateOpportunityCommand, Void> {

    private final OpportunityRepository opportunityRepository;

    public UpdateOpportunityHandler(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = Objects.requireNonNull(opportunityRepository, "opportunityRepository cannot be null");
    }

    @Override
    public Uni<Void> handle(UpdateOpportunityCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        return Uni.createFrom().completionStage(opportunityRepository.findById(command.opportunityId()))
                .map(optional -> optional.orElseThrow(
                        () -> new IllegalArgumentException("Opportunity not found: " + command.opportunityId())))
                .flatMap(opportunity -> {
                    apply(opportunity, command);
                    return Uni.createFrom().completionStage(opportunityRepository.save(opportunity));
                })
                .flatMap(saved -> Uni.createFrom().voidItem());
    }

    private static void apply(Opportunity opportunity, UpdateOpportunityCommand command) {
        opportunity.update(command.name(), command.description(), command.estimatedValue());
        if (command.assignedTo() != null) {
            opportunity.assign(command.assignedTo());
        }
        if (command.expectedCloseDate() != null) {
            opportunity.setExpectedCloseDate(command.expectedCloseDate());
        }
        opportunity.setLeadSource(command.leadSource());
        opportunity.setProductInterest(command.productInterest());
        opportunity.updateCompetitors(command.competitors(), command.decisionCriteria());
        opportunity.setNextStep(command.nextStep());
        opportunity.setNotes(command.notes());
    }
}
