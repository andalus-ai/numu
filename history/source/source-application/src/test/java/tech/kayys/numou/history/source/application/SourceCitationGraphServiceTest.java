package tech.kayys.numou.history.source.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.history.source.domain.*;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SourceCitationGraphServiceTest {
    private final UUID tenant = UUID.randomUUID();
    private final Map<UUID, HistoricalSource> sources = new HashMap<>();
    private final List<SourceRelationship> relationships = new ArrayList<>();
    private final Set<HistoricalSourceId> denied = new HashSet<>();
    private final HistoricalSourceAuthorizationPort authorization = new AllowListedAuthorization(denied);
    private final HistoricalSourceRepository repository = repositoryProxy();
    private final SourceCitationGraphService service =
        new SourceCitationGraphService(repository, () -> tenant, authorization);

    @Test
    void exploresBothDirectionsWithShortestDistancesAndCycleDeduplication() {
        HistoricalSource a = source("A");
        HistoricalSource b = source("B");
        HistoricalSource c = source("C");
        HistoricalSource d = source("D");
        link(a, b, SourceRelationshipType.CITES);
        link(c, a, SourceRelationshipType.TRANSLATES);
        HistoricalSource e = source("E");
        link(b, c, SourceRelationshipType.DERIVES_FROM);
        link(c, d, SourceRelationshipType.RESPONDS_TO);
        link(d, e, SourceRelationshipType.CITES);

        SourceCitationGraph graph = service.explore(a.id(), 2, 10, 10);

        assertEquals(4, graph.nodes().size());
        assertEquals(4, graph.relationships().size());
        assertEquals(0, distance(graph, a.id()));
        assertEquals(1, distance(graph, b.id()));
        assertEquals(1, distance(graph, c.id()));
        assertEquals(2, distance(graph, d.id()));
        assertFalse(graph.truncated());
        assertTrue(graph.relationships().stream().anyMatch(r -> r.sourceId().equals(c.id()) && r.relatedSourceId().equals(a.id())));
        assertTrue(graph.nodes().stream().noneMatch(n -> n.source().id().equals(e.id())));
    }

    @Test
    void respectsNodeAndRelationshipBudgetsAndMarksTruncation() {
        HistoricalSource a = source("A");
        HistoricalSource b = source("B");
        HistoricalSource c = source("C");
        link(a, b, SourceRelationshipType.CITES);
        link(a, c, SourceRelationshipType.CITES);

        SourceCitationGraph graph = service.explore(a.id(), 1, 2, 1);

        assertEquals(2, graph.nodes().size());
        assertEquals(1, graph.relationships().size());
        assertTrue(graph.truncated());
    }

    @Test
    void checksAuthorizationForEveryDiscoveredSource() {
        HistoricalSource a = source("A");
        HistoricalSource b = source("B");
        link(a, b, SourceRelationshipType.CITES);
        denied.add(b.id());

        assertThrows(SecurityException.class, () -> service.explore(a.id(), 1, 10, 10));
    }

    @Test
    void rejectsUnboundedTraversalRequestsBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.explore(HistoricalSourceId.generate(), 4, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> service.explore(HistoricalSourceId.generate(), 1, 101, 10));
        assertThrows(IllegalArgumentException.class, () -> service.explore(HistoricalSourceId.generate(), 1, 10, 201));
    }

    private HistoricalSource source(String title) {
        HistoricalSource source = HistoricalSource.register(HistoricalSourceId.generate(),
            HistoricalSourceKind.BOOK, title, null, null, null, null, null, "en",
            Instant.parse("2026-11-09T00:00:00Z"));
        sources.put(source.id().value(), source);
        return source;
    }

    private void link(HistoricalSource from, HistoricalSource to, SourceRelationshipType type) {
        relationships.add(SourceRelationship.create(from.id(), to.id(), type, null, null, null,
            Instant.parse("2026-11-09T00:00:01Z")));
    }

    private static int distance(SourceCitationGraph graph, HistoricalSourceId id) {
        return graph.nodes().stream().filter(n -> n.source().id().equals(id)).findFirst().orElseThrow().distance();
    }

    private HistoricalSourceRepository repositoryProxy() {
        return (HistoricalSourceRepository) Proxy.newProxyInstance(
            HistoricalSourceRepository.class.getClassLoader(),
            new Class<?>[]{HistoricalSourceRepository.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("find")) {
                    UUID tenantId = (UUID) args[0];
                    HistoricalSourceId id = (HistoricalSourceId) args[1];
                    return tenant.equals(tenantId) ? Optional.ofNullable(sources.get(id.value())) : Optional.empty();
                }
                if (name.equals("listOutgoingSourceRelationships") || name.equals("listIncomingSourceRelationships")) {
                    UUID tenantId = (UUID) args[0];
                    HistoricalSourceId id = (HistoricalSourceId) args[1];
                    int limit = (int) args[2];
                    if (!tenant.equals(tenantId)) return List.of();
                    return relationships.stream()
                        .filter(r -> name.equals("listOutgoingSourceRelationships")
                            ? r.sourceId().equals(id) : r.relatedSourceId().equals(id))
                        .sorted(Comparator.comparing(SourceRelationship::recordedAt).thenComparing(SourceRelationship::id))
                        .limit(limit).toList();
                }
                if (name.equals("toString")) return "SourceCitationGraphRepositoryProxy";
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == args[0];
                throw new UnsupportedOperationException("Unexpected repository method: " + name);
            });
    }

    private record AllowListedAuthorization(Set<HistoricalSourceId> denied)
        implements HistoricalSourceAuthorizationPort {
        @Override public void requireCanRegisterSource(UUID t) { }
        @Override public void requireCanReadSource(UUID t, HistoricalSourceId s) { }
        @Override public void requireCanReviseSource(UUID t, HistoricalSourceId s) { }
        @Override public void requireCanCreateClaim(UUID t) { }
        @Override public void requireCanReferenceSubject(UUID t, String k, String i) { }
        @Override public void requireCanReadClaim(UUID t, HistoricalClaimId c) { }
        @Override public void requireCanAttachEvidence(UUID t, HistoricalClaimId c, HistoricalSourceId s) { }
        @Override public void requireCanAssessSource(UUID t, HistoricalSourceId s, String a) { }
        @Override public void requireCanReviewSourceAssessment(UUID t, HistoricalSourceId s, UUID a, String r) { }
        @Override public void requireCanManageSourceBibliography(UUID t, HistoricalSourceId s) { }
        @Override public void requireCanReadSourceBibliography(UUID t, HistoricalSourceId s) {
            if (denied.contains(s)) throw new SecurityException("source access denied");
        }
    }
}
