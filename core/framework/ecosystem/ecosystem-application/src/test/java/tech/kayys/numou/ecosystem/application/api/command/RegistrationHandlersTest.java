package tech.kayys.numou.ecosystem.application.api.command;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.application.port.OrganizationRepository;
import tech.kayys.numou.ecosystem.application.port.PartyRepository;
import tech.kayys.numou.ecosystem.domain.model.Party;
import tech.kayys.numou.ecosystem.domain.organization.Organization;
import tech.kayys.numou.ecosystem.domain.organization.OrganizationType;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationHandlersTest {

    @Test
    void registerPartyPersistsBeforePublishingItsEvent() {
        List<String> order = new ArrayList<>();
        PartyRepository parties = new PartyRepository() {
            @Override
            public Uni<Party> save(Party party) {
                order.add("save");
                return Uni.createFrom().item(party);
            }

            @Override
            public Uni<Optional<Party>> findById(PartyId id) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        EventPublisher publisher = events -> {
            assertEquals(1, events.size());
            order.add("publish");
            return Uni.createFrom().voidItem();
        };
        RegisterPartyHandler handler = new RegisterPartyHandler(parties, publisher, directUnitOfWork());

        var result = handler.handle(new RegisterPartyCommand(PartyKind.ORGANIZATION))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(List.of("save", "publish"), order);
        assertEquals(PartyKind.ORGANIZATION, result.orElseThrow().kind());
    }

    @Test
    void registerOrganizationRequiresExistingOrganizationParty() {
        Party person = Party.register(PartyId.generate(), PartyKind.PERSON);
        PartyRepository parties = new PartyRepository() {
            @Override
            public Uni<Party> save(Party party) {
                return Uni.createFrom().item(party);
            }

            @Override
            public Uni<Optional<Party>> findById(PartyId id) {
                return Uni.createFrom().item(Optional.of(person));
            }
        };
        OrganizationRepository organizations = new OrganizationRepository() {
            @Override
            public Uni<Organization> save(Organization organization) {
                return Uni.createFrom().item(organization);
            }

            @Override
            public Uni<Optional<Organization>> findByPartyId(PartyId id) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        EventPublisher publisher = events -> Uni.createFrom().voidItem();
        RegisterOrganizationHandler handler = new RegisterOrganizationHandler(
                organizations, parties, publisher, directUnitOfWork());

        var result = handler.handle(new RegisterOrganizationCommand(
                person.getId(), "Example Person", "Example", OrganizationType.of("numou", "company"), UUID.randomUUID()))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("ECOSYSTEM_PARTY_KIND_MISMATCH", result.failureError().orElseThrow().code());
    }

    @Test
    void registerOrganizationPersistsProfileAndPublishesOnlyAfterSave() {
        Party party = Party.register(PartyId.generate(), PartyKind.ORGANIZATION);
        List<String> order = new ArrayList<>();
        PartyRepository parties = new PartyRepository() {
            @Override
            public Uni<Party> save(Party candidate) {
                return Uni.createFrom().item(candidate);
            }

            @Override
            public Uni<Optional<Party>> findById(PartyId id) {
                return Uni.createFrom().item(Optional.of(party));
            }
        };
        OrganizationRepository organizations = new OrganizationRepository() {
            @Override
            public Uni<Organization> save(Organization organization) {
                order.add("save");
                return Uni.createFrom().item(organization);
            }

            @Override
            public Uni<Optional<Organization>> findByPartyId(PartyId id) {
                return Uni.createFrom().item(Optional.empty());
            }
        };
        EventPublisher publisher = events -> {
            assertEquals(1, events.size());
            order.add("publish");
            return Uni.createFrom().voidItem();
        };
        RegisterOrganizationHandler handler = new RegisterOrganizationHandler(
                organizations, parties, publisher, directUnitOfWork());

        var result = handler.handle(new RegisterOrganizationCommand(
                party.getId(), "Example Company Ltd", "Example", OrganizationType.of("numou", "company"), null))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(List.of("save", "publish"), order);
        assertEquals(party.getId(), result.orElseThrow().partyId());
    }

    private static UnitOfWork directUnitOfWork() {
        return new UnitOfWork() {
            @Override
            public <R> Uni<R> execute(Supplier<Uni<R>> work) {
                return Uni.createFrom().deferred(work);
            }
        };
    }
}
