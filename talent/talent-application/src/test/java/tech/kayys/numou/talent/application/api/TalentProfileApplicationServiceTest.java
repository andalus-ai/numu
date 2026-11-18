package tech.kayys.numou.talent.application.api;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.ecosystem.domain.party.PartyKind;
import tech.kayys.numou.ecosystem.domain.party.PartyRef;
import tech.kayys.numou.foundation.application.context.ActorId;
import tech.kayys.numou.foundation.application.context.ExecutionContext;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.application.port.*;
import tech.kayys.numou.talent.domain.profile.*;

import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TalentProfileApplicationServiceTest {

    private TenantId tenant;
    private TenantId otherTenant;
    private PartyRef person;
    private ExecutionContext context;
    private InMemoryUnitOfWork unitOfWork;
    private MutableAccessPolicy access;
    private MutableEvidenceVerifier evidenceVerifier;
    private TalentProfileApplicationService service;

    @BeforeEach
    void setUp() {
        tenant = TenantId.generate();
        otherTenant = TenantId.generate();
        person = PartyRef.of(PartyId.generate(), PartyKind.PERSON);
        context = ExecutionContext.builder().tenantId(tenant).actorId(ActorId.of("user:alice")).build();
        unitOfWork = new InMemoryUnitOfWork();
        access = new MutableAccessPolicy();
        evidenceVerifier = new MutableEvidenceVerifier();
        service = new TalentProfileApplicationService(unitOfWork.repository,
                (tenantId, subject) -> Uni.createFrom().item(tenant.equals(tenantId) && person.equals(subject)),
                access, evidenceVerifier, unitOfWork);
    }

    @Test
    void createsAndPublishesProfileEventsWithinApplicationTransaction() {
        TalentProfileId id = TalentProfileId.generate();

        TalentProfileView view = service.create(context, id, person, "Backend Engineer", null, "Bandung")
                .await().indefinitely();

        assertThat(view.id()).isEqualTo(id);
        assertThat(view.status()).isEqualTo(TalentProfileStatus.DRAFT);
        assertThat(view.visibility()).isEqualTo(ProfileVisibility.PRIVATE);
        assertThat(unitOfWork.repository.findByTenantAndId(tenant, id).await().indefinitely()).isPresent();
        assertThat(unitOfWork.publishedEvents).extracting(DomainEvent::eventType)
                .containsExactly("talent.profile.created");
        assertThat(unitOfWork.publishedContexts).hasSize(1);
        assertThat(unitOfWork.publishedContexts.get(0).tenantId()).isEqualTo(tenant);
        assertThat(unitOfWork.publishedContexts.get(0).actorId()).isEqualTo(ActorId.of("user:alice"));
    }

    @Test
    void repositoryRejectsUpdateWhenExpectedVersionIsStale() {
        TalentProfileId id = TalentProfileId.generate();
        service.create(context, id, person, "Engineer", null, null).await().indefinitely();
        TalentProfile stored = unitOfWork.repository.findByTenantAndId(tenant, id).await().indefinitely().orElseThrow();
        stored.updatePresentation("Updated", null, null);

        assertThatThrownBy(() -> unitOfWork.repository.update(tenant, stored, 9).await().indefinitely())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("concurrently");
    }

    @Test
    void rejectsPartyThatIsNotPresentInTheActiveTenant() {
        assertThatThrownBy(() -> service.create(context, TalentProfileId.generate(),
                PartyRef.of(PartyId.generate(), PartyKind.PERSON), "Engineer", null, null)
                .await().indefinitely()).isInstanceOf(IllegalArgumentException.class);
        assertThat(unitOfWork.repository.profiles).isEmpty();
        assertThat(unitOfWork.publishedEvents).isEmpty();
    }

    @Test
    void tenantScopePreventsReadingAnotherTenantsProfile() {
        TalentProfileId id = TalentProfileId.generate();
        service.create(context, id, person, "Engineer", null, null).await().indefinitely();

        ExecutionContext foreign = ExecutionContext.builder()
                .tenantId(otherTenant).actorId(ActorId.of("user:bob")).build();

        assertThatThrownBy(() -> service.get(foreign, id).await().indefinitely())
                .isInstanceOf(TalentProfileNotFoundException.class);
    }

    @Test
    void deniedReadNeverReturnsProfileView() {
        TalentProfileId id = TalentProfileId.generate();
        service.create(context, id, person, "Private Engineer", null, null).await().indefinitely();
        access.allowed = false;

        assertThatThrownBy(() -> service.get(context, id).await().indefinitely())
                .isInstanceOf(TalentProfileAccessDeniedException.class);
    }

    @Test
    void deniedMutationDoesNotChangeAggregateOrPublishEvents() {
        TalentProfileId id = TalentProfileId.generate();
        service.create(context, id, person, "Engineer", null, null).await().indefinitely();
        int eventCount = unitOfWork.publishedEvents.size();
        access.allowed = false;

        assertThatThrownBy(() -> service.updatePresentation(context, id, "Changed", null, null)
                .await().indefinitely())
                .isInstanceOf(TalentProfileAccessDeniedException.class);

        TalentProfile stored = unitOfWork.repository.findByTenantAndId(tenant, id).await().indefinitely().orElseThrow();
        assertThat(stored.headline()).isEqualTo("Engineer");
        assertThat(unitOfWork.publishedEvents).hasSize(eventCount);
    }

    @Test
    void rejectsUnverifiedSkillClaimBeforeChangingProfile() {
        TalentProfileId id = TalentProfileId.generate();
        service.create(context, id, person, "Engineer", null, null).await().indefinitely();
        evidenceVerifier.allowed = false;
        var skill = new ProfileContent.Skill(UUID.randomUUID(), "Java", null,
                ProfileContent.SkillLevel.EXPERT, ProfileContent.Verification.PLATFORM_VERIFIED);

        assertThatThrownBy(() -> service.replaceSkills(context, id, List.of(skill)).await().indefinitely())
                .isInstanceOf(SecurityException.class);

        TalentProfile stored = unitOfWork.repository.findByTenantAndId(tenant, id).await().indefinitely().orElseThrow();
        assertThat(stored.skills()).isEmpty();
        assertThat(stored.getVersion()).isZero();
    }

    @Test
    void anonymousOrMissingTenantExecutionContextIsRejected() {
        ExecutionContext anonymous = ExecutionContext.builder().tenantId(tenant).build();
        assertThatThrownBy(() -> service.get(anonymous, TalentProfileId.generate()))
                .isInstanceOf(SecurityException.class);

        ExecutionContext withoutTenant = ExecutionContext.builder().actorId(ActorId.of("user:alice")).build();
        assertThatThrownBy(() -> service.get(withoutTenant, TalentProfileId.generate()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableAccessPolicy implements TalentProfileAccessPolicy {
        private boolean allowed = true;

        @Override
        public Uni<Boolean> isAllowed(ExecutionContext context, Action action, PartyRef subject, TalentProfile profile) {
            return Uni.createFrom().item(allowed);
        }
    }

    private static final class MutableEvidenceVerifier implements ProfileEvidenceVerifier {
        private boolean allowed = true;

        @Override
        public Uni<Boolean> verifySkill(ExecutionContext context, PartyRef subject, ProfileContent.Skill skill) {
            return Uni.createFrom().item(allowed);
        }

        @Override
        public Uni<Boolean> verifyCredential(ExecutionContext context, PartyRef subject, ProfileContent.Credential credential) {
            return Uni.createFrom().item(allowed);
        }
    }

    private static final class InMemoryUnitOfWork implements TalentProfileTransactionRunner, TalentProfileTransaction {
        private final InMemoryRepository repository = new InMemoryRepository();
        private final List<DomainEvent> publishedEvents = new ArrayList<>();
        private final List<ExecutionContext> publishedContexts = new ArrayList<>();

        @Override
        public <T> Uni<T> inTransaction(ExecutionContext context, Function<TalentProfileTransaction, Uni<T>> work) {
            return work.apply(this);
        }

        @Override
        public TalentProfileRepository profiles() {
            return repository;
        }

        @Override
        public Uni<Void> publish(ExecutionContext context, List<DomainEvent> events) {
            publishedContexts.add(context);
            publishedEvents.addAll(events);
            return Uni.createFrom().voidItem();
        }
    }

    private static final class InMemoryRepository implements TalentProfileRepository {
        private final Map<Key, TalentProfile> profiles = new HashMap<>();

        @Override
        public Uni<Optional<TalentProfile>> findByTenantAndId(TenantId tenantId, TalentProfileId profileId) {
            return Uni.createFrom().item(Optional.ofNullable(profiles.get(new Key(tenantId, profileId))));
        }

        @Override
        public Uni<TalentProfile> insert(TenantId tenantId, TalentProfile profile) {
            if (!tenantId.equals(profile.tenantId())) {
                return Uni.createFrom().failure(new SecurityException("Tenant does not own talent profile"));
            }
            Key key = new Key(tenantId, profile.id());
            if (profiles.containsKey(key)) {
                return Uni.createFrom().failure(new IllegalStateException("Talent profile already exists"));
            }
            profiles.put(key, profile);
            return Uni.createFrom().item(profile);
        }

        @Override
        public Uni<TalentProfile> update(TenantId tenantId, TalentProfile profile, int expectedVersion) {
            if (!tenantId.equals(profile.tenantId())) {
                return Uni.createFrom().failure(new SecurityException("Tenant does not own talent profile"));
            }
            Key key = new Key(tenantId, profile.id());
            TalentProfile current = profiles.get(key);
            if (current == null || current.getVersion() != expectedVersion
                    || (profile.getVersion() != expectedVersion && profile.getVersion() != expectedVersion + 1)) {
                return Uni.createFrom().failure(new IllegalStateException("Talent profile changed concurrently"));
            }
            profiles.put(key, profile);
            return Uni.createFrom().item(profile);
        }

        private record Key(TenantId tenantId, TalentProfileId profileId) {}
    }
}
