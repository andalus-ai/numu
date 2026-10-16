package tech.kayys.syirkah.accounting.interfaces.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.accounting.application.api.command.ApproveJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.api.command.ReverseJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.cqrs.CommandBus;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/accounting/journal-entries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Journal Entry API", description = "Endpoints for posting, approving, and reversing journal entries")
public class JournalEntryResource {

    @Inject
    CommandBus commandBus;

    @Inject
    JournalEntryRepository journalEntryRepository;

    
public record ReverseEntryRequest(String reversedBy, String reason) {}
    public record ApprovalRequest(String approver) {}

    @GET
    @Operation(summary = "List all journal entries")
    public Uni<List<JournalEntry>> listEntries() {
        return journalEntryRepository.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get journal entry details by ID")
    public Uni<Response> getEntry(@PathParam("id") UUID id) {
        return journalEntryRepository.findById(JournalEntryId.of(id))
                .map(opt -> opt.map(e -> Response.ok(e).build())
                               .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()));
    }

    @POST
    @Path("/post")
    @Operation(summary = "Post a balanced journal entry via CQRS Command Bus")
    public Uni<Response> postEntry(PostJournalEntryCommand command) {
        return commandBus.<PostJournalEntryCommand, JournalEntryId>dispatch(command)
                .map(id -> Response.status(Response.Status.CREATED).entity(id).build());
    }

    @POST
    @Path("/{id}/approve")
    @Operation(summary = "Maker-Checker: Approve a journal entry")
    public Uni<Response> approveEntry(@PathParam("id") UUID id, ApprovalRequest req) {
        ApproveJournalEntryCommand cmd = new ApproveJournalEntryCommand(JournalEntryId.of(id), req.approver());
        return commandBus.<ApproveJournalEntryCommand, Void>dispatch(cmd)
                .map(v -> Response.ok().build());
    }

    @POST
    @Path("/{id}/reverse")
    @Operation(summary = "Perform a Storno reversal via CQRS Command Bus")
    public Uni<Response> reverseEntry(@PathParam("id") UUID id, ReverseEntryRequest req) {
        ReverseJournalEntryCommand cmd = new ReverseJournalEntryCommand(JournalEntryId.of(id), req.reversedBy(), req.reason());
        return commandBus.<ReverseJournalEntryCommand, JournalEntryId>dispatch(cmd)
                .map(revId -> Response.ok(revId).build());
    }
}
