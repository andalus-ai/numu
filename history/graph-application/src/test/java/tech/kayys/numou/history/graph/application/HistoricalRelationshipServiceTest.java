package tech.kayys.numou.history.graph.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.chronology.domain.CalendarSystemId;
import tech.kayys.numou.history.chronology.domain.HistoricalDate;
import tech.kayys.numou.history.chronology.domain.HistoricalDateRange;
import tech.kayys.numou.history.entities.domain.HistoricalCollectiveId;
import tech.kayys.numou.history.entities.domain.HistoricalEntityReference;
import tech.kayys.numou.history.entities.domain.HistoricalPersonId;
import tech.kayys.numou.history.entities.domain.HistoricalRelationship;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipId;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipKindCode;
import tech.kayys.numou.history.entities.domain.HistoricalRelationshipVocabulary;
import tech.kayys.numou.history.graph.spi.HistoricalRelationshipRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalRelationshipServiceTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);
    private static final HistoricalRelationshipVocabulary VOCABULARY =
            HistoricalRelationshipVocabulary.standard();

    @Test
    void createUsesTrustedTenantAndAuthorizesBothEndpoints() {
        var repository = new FakeRepository();
        var authorization = new RecordingAuthorization();
        var service = service(repository, authorization);
        var subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        var object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());

        HistoricalRelationship created = service.create(subject, object,
                HistoricalRelationshipKindCode.of("founded"), null, "Historical foundation");

        assertEquals(TENANT, repository.lastTenant);
        assertEquals(0, repository.lastExpectedRevision);
        assertEquals(created, repository.findVisibleTo(TENANT, created.id()).orElseThrow());
        assertEquals(List.of(subject, object), authorization.referencedEntities);
    }

    @Test
    void readAuthorizationRunsBeforeRepositoryLookup() {
        var repository = new FakeRepository();
        var authorization = new RecordingAuthorization();
        authorization.allowed = false;
        var service = service(repository, authorization);

        assertThrows(SecurityException.class,
                () -> service.find(HistoricalRelationshipId.generate()));
        assertEquals(0, repository.readCount);
    }

    @Test
    void endpointSearchAlsoAuthorizesEachReturnedRelationship() {
        var repository = new FakeRepository();
        var authorization = new RecordingAuthorization();
        var service = service(repository, authorization);
        var subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        var object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        service.create(subject, object, HistoricalRelationshipKindCode.of("founded"), null, "Research");
        authorization.allowRelationshipRead = false;
        assertThrows(SecurityException.class, () -> service.findBySubject(subject, 20));
    }

    @Test
    void reviseAppendsNewRevisionAndReportsConcurrentWriter() {
        var repository = new FakeRepository();
        var authorization = new RecordingAuthorization();
        var service = service(repository, authorization);
        var subject = HistoricalEntityReference.person(HistoricalPersonId.generate());
        var object = HistoricalEntityReference.collective(HistoricalCollectiveId.generate());
        HistoricalRelationship created = service.create(subject, object,
                HistoricalRelationshipKindCode.of("founded"), range(), "Original");

        HistoricalRelationship revised = service.revise(created.id(), subject, object, created.kind(),
                range(), "Corrected").orElseThrow();
        assertEquals(created.id(), revised.id());
        assertEquals(2, revised.revision());
        assertEquals(revised, service.find(created.id()).orElseThrow());

        repository.forceConflict = true;
        assertThrows(HistoricalRelationshipConflictException.class,
                () -> service.revise(created.id(), subject, object, created.kind(), range(), "Stale edit"));
    }

    private static HistoricalRelationshipService service(
            FakeRepository repository, RecordingAuthorization authorization) {
        return new HistoricalRelationshipService(repository, () -> TENANT, authorization, VOCABULARY, CLOCK);
    }

    private static HistoricalDateRange range() {
        return new HistoricalDateRange(
                HistoricalDate.year(1000, CalendarSystemId.of("julian")),
                HistoricalDate.year(1100, CalendarSystemId.of("julian")));
    }

    private static final class RecordingAuthorization implements HistoricalRelationshipAuthorizationPort {
        boolean allowed = true;
        boolean allowRelationshipRead = true;
        final List<HistoricalEntityReference> referencedEntities = new ArrayList<>();
        private void check() { if (!allowed) throw new SecurityException("forbidden"); }
        @Override public void requireCanReadRelationship(UUID tenantId, HistoricalRelationshipId id) {
            check();
            if (!allowRelationshipRead) throw new SecurityException("relationship forbidden");
        }
        @Override public void requireCanCreateRelationship(UUID tenantId) { check(); }
        @Override public void requireCanReviseRelationship(UUID tenantId, HistoricalRelationshipId id) { check(); }
        @Override public void requireCanReferenceEntity(UUID tenantId, HistoricalEntityReference reference) {
            check();
            referencedEntities.add(reference);
        }
    }

    private static final class FakeRepository implements HistoricalRelationshipRepository {
        final Map<UUID, Map<UUID, HistoricalRelationship>> records = new HashMap<>();
        UUID lastTenant;
        long lastExpectedRevision = -1;
        int readCount;
        boolean forceConflict;

        @Override public Optional<HistoricalRelationship> findVisibleTo(UUID tenantId, HistoricalRelationshipId id) {
            readCount++;
            return Optional.ofNullable(records.getOrDefault(tenantId, Map.of()).get(id.value()));
        }
        @Override public List<HistoricalRelationship> findBySubject(UUID tenantId, HistoricalEntityReference subject, int limit) {
            return records.getOrDefault(tenantId, Map.of()).values().stream()
                    .filter(value -> value.subject().equals(subject)).limit(limit).toList();
        }
        @Override public List<HistoricalRelationship> findByObject(UUID tenantId, HistoricalEntityReference object, int limit) {
            return records.getOrDefault(tenantId, Map.of()).values().stream()
                    .filter(value -> value.object().equals(object)).limit(limit).toList();
        }
        @Override public boolean saveTenantScoped(UUID tenantId, HistoricalRelationship relationship, long expectedRevision) {
            lastTenant = tenantId;
            lastExpectedRevision = expectedRevision;
            if (forceConflict) return false;
            Map<UUID, HistoricalRelationship> tenantRecords =
                    records.computeIfAbsent(tenantId, ignored -> new HashMap<>());
            HistoricalRelationship current = tenantRecords.get(relationship.id().value());
            long actual = current == null ? 0 : current.revision();
            if (actual != expectedRevision || relationship.revision() != expectedRevision + 1) return false;
            tenantRecords.put(relationship.id().value(), relationship);
            return true;
        }
    }
}
