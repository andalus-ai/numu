package tech.kayys.numou.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.*;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.organization.Organization;
import tech.kayys.numou.ecosystem.domain.organization.OrganizationType;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.event.EventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationHandlersTest {

    @Test
    void registerPartyPersistsBeforePublishingItsEvent() {
        RegisterPartyCommand command = new RegisterPartyCommand(PartyKind.ORGANIZATION);
        Command foundationCommand = command;
        assertSame(command, foundationCommand);
        List<String> order = new ArrayList<>();
        PartyRepository parties = new PartyRepository() {
            @Override
            public Uni<Party> save(Party party) {
                order.add("party-save");
                return Uni.createFrom().item(party);
            }

            @Override
            public Uni<Optional<Party>> findById(PartyId id) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        EventPublisher publisher = events -> {
            assertEquals(1, events.size());
            order.add("outbox-append");
            return Uni.createFrom().voidItem();
        };
        EcosystemTransactionRunner transactions = runner(parties,
                organizationRepository(order, Optional.empty()), order, publisher);
        RegisterPartyHandler handler = new RegisterPartyHandler(transactions);

        var result = handler.handle(command)
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(List.of("begin", "party-save", "outbox-append", "commit"), order);
        assertEquals(PartyKind.ORGANIZATION, result.orElseThrow().kind());
    }

    @Test
    void registerOrganizationRequiresExistingOrganizationParty() {
        Party person = Party.register(PartyId.generate(), PartyKind.PERSON);
        List<String> order = new ArrayList<>();
        EcosystemTransactionRunner runner = runner(partyRepository(order, Optional.of(person)),
                organizationRepository(order, Optional.empty()), order);

        var result = new RegisterOrganizationHandler(runner).handle(new RegisterOrganizationCommand(
                person.getId(), "Example Person", "Example",
                OrganizationType.of("numou", "company"), UUID.randomUUID())).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("ECOSYSTEM_PARTY_KIND_MISMATCH", result.failureError().orElseThrow().code());
        assertEquals(List.of("begin", "party-read", "commit"), order);
    }

    @Test
    void registerOrganizationPersistsProfileAndAppendsEventInTransaction() {
        Party party = Party.register(PartyId.generate(), PartyKind.ORGANIZATION);
        List<String> order = new ArrayList<>();
        EcosystemTransactionRunner runner = runner(partyRepository(order, Optional.of(party)),
                organizationRepository(order, Optional.empty()), order);

        var result = new RegisterOrganizationHandler(runner).handle(new RegisterOrganizationCommand(
                party.getId(), "Example Company Ltd", "Example",
                OrganizationType.of("numou", "company"), null)).await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(List.of("begin", "party-read", "organization-read",
                "organization-save", "outbox-append", "commit"), order);
        assertEquals(party.getId(), result.orElseThrow().partyId());
    }

    @Test
    void failedOutboxAppendFailsCommandAndTriggersRollback() {
        List<String> order = new ArrayList<>();
        PartyRepository parties = partyRepository(order, Optional.empty());
        EventPublisher failingPublisher = events -> {
            order.add("outbox-append");
            return Uni.createFrom().failure(new IllegalStateException("outbox unavailable"));
        };
        EcosystemTransactionRunner runner = runner(parties,
                organizationRepository(order, Optional.empty()), order, failingPublisher);

        assertThrows(RuntimeException.class, () -> new RegisterPartyHandler(runner)
                .handle(new RegisterPartyCommand(PartyKind.PERSON)).await().indefinitely());
        assertEquals(List.of("begin", "party-save", "outbox-append", "rollback"), order);
    }

    private static EcosystemTransactionRunner runner(
            PartyRepository parties, OrganizationRepository organizations, List<String> order) {
        return runner(parties, organizations, order, events -> {
            order.add("outbox-append");
            return Uni.createFrom().voidItem();
        });
    }

    private static EcosystemTransactionRunner runner(
            PartyRepository parties, OrganizationRepository organizations, List<String> order,
            EventPublisher publisher) {
        EcosystemTransaction context = new EcosystemTransaction() {
            @Override public PartyRepository parties() { return parties; }
            @Override public OrganizationRepository organizations() { return organizations; }
            @Override public EventPublisher events() { return publisher; }
        };
        return new EcosystemTransactionRunner() {
            @Override
            public <T> Uni<T> inTransaction(Function<EcosystemTransaction, Uni<T>> work) {
                order.add("begin");
                return EcosystemTransactionRunner.invoke(work, context)
                        .onItem().invoke(ignored -> order.add("commit"))
                        .onFailure().invoke(ignored -> order.add("rollback"));
            }
        };
    }

    private static PartyRepository partyRepository(List<String> order, Optional<Party> party) {
        return new PartyRepository() {
            @Override public Uni<Party> save(Party value) {
                order.add("party-save");
                return Uni.createFrom().item(value);
            }
            @Override public Uni<Optional<Party>> findById(PartyId id) {
                order.add("party-read");
                return Uni.createFrom().item(party);
            }
        };
    }

    private static OrganizationRepository organizationRepository(
            List<String> order, Optional<Organization> organization) {
        return new OrganizationRepository() {
            @Override public Uni<Organization> save(Organization value) {
                order.add("organization-save");
                return Uni.createFrom().item(value);
            }
            @Override public Uni<Optional<Organization>> findByPartyId(PartyId id) {
                order.add("organization-read");
                return Uni.createFrom().item(organization);
            }
        };
    }
}
