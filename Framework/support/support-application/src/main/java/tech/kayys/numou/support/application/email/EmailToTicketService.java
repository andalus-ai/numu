package tech.kayys.numou.support.application.email;

import tech.kayys.numou.support.application.email.EmailToTicketPorts.Idempotency;
import tech.kayys.numou.support.application.email.EmailToTicketPorts.RequesterResolver;
import tech.kayys.numou.support.application.email.EmailToTicketPorts.Routing;
import tech.kayys.numou.support.application.email.EmailToTicketPorts.ThreadLookup;
import tech.kayys.numou.support.application.email.EmailToTicketPorts.TicketGateway;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class EmailToTicketService {

    private final Routing routing;
    private final ThreadLookup threadLookup;
    private final RequesterResolver requesterResolver;
    private final TicketGateway ticketGateway;
    private final Idempotency idempotency;

    public EmailToTicketService(Routing routing, ThreadLookup threadLookup,
                                RequesterResolver requesterResolver, TicketGateway ticketGateway,
                                Idempotency idempotency) {
        this.routing = Objects.requireNonNull(routing);
        this.threadLookup = Objects.requireNonNull(threadLookup);
        this.requesterResolver = Objects.requireNonNull(requesterResolver);
        this.ticketGateway = Objects.requireNonNull(ticketGateway);
        this.idempotency = Objects.requireNonNull(idempotency);
    }

    public CompletionStage<EmailProcessingResult> process(InboundEmail email, List<String> references,
                                                          String explicitTicketToken) {
        Objects.requireNonNull(email, "email cannot be null");
        if (email.isSupportOutboundMessage()) {
            return CompletableFuture.failedStage(
                    new IllegalArgumentException("Support-originated email cannot be ingested"));
        }
        EmailAddress recipient = email.to().getFirst();
        return routing.resolve(recipient).thenCompose(route ->
                idempotency.processOnce(route, email.messageId(), () ->
                        threadLookup.findTicket(route, references == null ? List.of() : List.copyOf(references),
                                explicitTicketToken)
                                .thenCompose(existing -> existing
                                        .map(ticketId -> ticketGateway.addPublicReply(route, ticketId, email))
                                        .orElseGet(() -> requesterResolver.resolve(email.from())
                                                .thenCompose(requester ->
                                                        ticketGateway.createFromEmail(route, requester, email))))));
    }
}
