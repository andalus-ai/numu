package tech.kayys.numou.crm.application.lead;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.application.api.command.CreateLeadCommand;
import tech.kayys.numou.crm.domain.identifier.LeadId;
import tech.kayys.numou.crm.domain.model.Lead;
import tech.kayys.numou.crm.domain.repository.LeadRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;

import java.util.Objects;

/**
 * Creates a {@link Lead}. The Lead aggregate raises no domain events,
 * so this handler only persists and returns the new identity; the
 * Panache-backed repository owns its own transaction.
 */
public final class CreateLeadHandler implements CommandHandler<CreateLeadCommand, LeadId> {

    private final LeadRepository leadRepository;

    public CreateLeadHandler(LeadRepository leadRepository) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository cannot be null");
    }

    @Override
    public Uni<LeadId> handle(CreateLeadCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        Lead lead = Lead.create(
                command.leadId(),
                command.firstName(),
                command.lastName(),
                command.email(),
                command.source());
        lead.setPhone(command.phone());
        lead.setCompany(command.company());
        lead.setJobTitle(command.jobTitle());
        lead.setIndustry(command.industry());
        lead.setNotes(command.notes());

        return Uni.createFrom().completionStage(leadRepository.save(lead))
                .replaceWith(lead.getId());
    }
}
