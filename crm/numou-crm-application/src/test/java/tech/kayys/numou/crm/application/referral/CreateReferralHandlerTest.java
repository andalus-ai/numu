package tech.kayys.numou.crm.application.referral;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.crm.application.api.command.CreateReferralCommand;
import tech.kayys.numou.crm.application.support.InMemoryReferralRepository;
import tech.kayys.numou.crm.domain.event.referral.ReferralCreated;
import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.referral.ReferralTarget;
import tech.kayys.numou.crm.domain.referral.ReferralType;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link CreateReferralHandler} persists the new referral and
 * publishes a {@link ReferralCreated} event that identifies the aggregate.
 */
class CreateReferralHandlerTest {

    private final InMemoryReferralRepository repository = new InMemoryReferralRepository();
    private final List<DomainEvent> published = new ArrayList<>();

    private final EventPublisher eventPublisher = events -> {
        published.addAll(events);
        return Uni.createFrom().voidItem();
    };

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <R> Uni<R> execute(Supplier<Uni<R>> work) {
            return work.get();
        }
    };

    private final CreateReferralHandler handler =
            new CreateReferralHandler(repository, eventPublisher, unitOfWork);

    @Test
    void shouldCreateReferralAndPublishEvent() {
        ReferralId referralId = ReferralId.random();
        AccountId referrerAccountId = AccountId.generate();
        CreateReferralCommand command = new CreateReferralCommand(
                referralId,
                referrerAccountId,
                ReferralTarget.LEAD,
                ReferralType.PARTNER,
                "REF-001",
                "Test referral"
        );

        Result<Referral> result = handler.handle(command)
                .subscribe().asCompletionStage().join();

        assertTrue(result.isSuccess());
        Referral saved = result.orElseThrow();
        assertEquals(referralId, saved.id());
        assertEquals(referrerAccountId, saved.referrerAccountId());
        assertEquals(ReferralTarget.LEAD, saved.target());
        assertEquals(ReferralType.PARTNER, saved.type());
        assertEquals("REF-001", saved.code());

        assertTrue(repository.findById(referralId).toCompletableFuture().join().isPresent());

        assertEquals(1, published.size());
        assertTrue(published.get(0) instanceof ReferralCreated);
        ReferralCreated event = (ReferralCreated) published.get(0);
        assertEquals(referralId.value(), event.referralId());
        assertEquals(referrerAccountId.value(), event.referrerAccountId());
    }
}
