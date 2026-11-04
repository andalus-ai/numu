package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.domain.profile.TalentProfile;

import java.util.List;

/** Transaction-scoped operations; implementations persist state and enqueue events atomically. */
public interface TalentProfileTransaction {
    TalentProfileRepository profiles();
    Uni<Void> publish(ExecutionContext context, List<DomainEvent> events);
}
