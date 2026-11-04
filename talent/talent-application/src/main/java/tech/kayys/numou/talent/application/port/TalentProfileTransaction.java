package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.application.context.ExecutionContext;

import java.util.List;

/** Transaction-scoped operations; implementations persist state and enqueue events atomically. */
public interface TalentProfileTransaction {
    TalentProfileRepository profiles();
    Uni<Void> publish(ExecutionContext context, List<DomainEvent> events);
}
