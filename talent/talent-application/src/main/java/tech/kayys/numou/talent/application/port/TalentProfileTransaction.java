package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.application.context.ExecutionContext;

import java.util.List;

/**
 * Transaction-scoped operations; implementations persist aggregate state,
 * idempotency receipts and outbox events atomically.
 */
public interface TalentProfileTransaction {
    TalentProfileRepository profiles();

    TalentProfileCommandReceiptStore commandReceipts();

    Uni<Void> publish(ExecutionContext context, List<DomainEvent> events);
}
