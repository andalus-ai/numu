package tech.kayys.syirkah.construction.domain.document;

import tech.kayys.syirkah.construction.domain.document.event.RfiSubmitted;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RequestForInformation extends AbstractAggregateRoot<RequestForInformationId> {
    private final UUID projectId;
    private final String rfiNumber;
    private final String question;
    private RfiStatus status;
    private String response;

    private RequestForInformation(RequestForInformationId id, UUID projectId, String rfiNumber, String question) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.rfiNumber = Objects.requireNonNull(rfiNumber);
        this.question = Objects.requireNonNull(question);
        this.status = RfiStatus.OPEN;
    }

    public static RequestForInformation submit(UUID projectId, String rfiNumber, String question) {
        var rfi = new RequestForInformation(RequestForInformationId.generate(), projectId, rfiNumber, question);
        rfi.raise(new RfiSubmitted(UUID.randomUUID(), Instant.now(), rfi.id().value(), projectId, rfiNumber));
        return rfi;
    }

    public void answer(String answerResponse) {
        this.response = Objects.requireNonNull(answerResponse);
        this.status = RfiStatus.ANSWERED;
    }

    public void close() {
        this.status = RfiStatus.CLOSED;
    }

    public UUID projectId() { return projectId; }
    public String rfiNumber() { return rfiNumber; }
    public String question() { return question; }
    public RfiStatus status() { return status; }
    public String response() { return response; }
}
