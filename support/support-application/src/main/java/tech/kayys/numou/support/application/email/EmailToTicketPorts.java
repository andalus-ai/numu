package tech.kayys.numou.support.application.email;

import tech.kayys.numou.support.domain.ticket.Requester;
import tech.kayys.numou.support.domain.ticket.TicketId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class EmailToTicketPorts {

    private EmailToTicketPorts() {
    }

    public interface Routing {
        CompletionStage<EmailRoute> resolve(EmailAddress recipient);
    }

    public interface ThreadLookup {
        CompletionStage<Optional<TicketId>> findTicket(EmailRoute route, List<String> references,
                                                       String explicitTicketToken);
    }

    public interface RequesterResolver {
        CompletionStage<Requester> resolve(EmailAddress sender);
    }

    public interface TicketGateway {
        CompletionStage<EmailProcessingResult> createFromEmail(EmailRoute route, Requester requester,
                                                                InboundEmail email);

        CompletionStage<EmailProcessingResult> addPublicReply(EmailRoute route, TicketId ticketId,
                                                               InboundEmail email);
    }

    public interface Idempotency {
        CompletionStage<EmailProcessingResult> processOnce(EmailRoute route, String messageId,
                Supplier<CompletionStage<EmailProcessingResult>> process);
    }
}
