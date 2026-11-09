package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.HistoricalSource;
import tech.kayys.numou.history.source.domain.HistoricalSourceId;
import tech.kayys.numou.history.source.domain.SourceRelationship;
import tech.kayys.numou.history.source.spi.HistoricalSourceRepository;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds bounded, tenant-authorized citation graph read models from the source repository.
 * PostgreSQL source records remain authoritative; this service neither writes nor caches graph state.
 */
public final class SourceCitationGraphService {
    private static final int MAX_DEPTH = 3;
    private static final int MAX_NODES = 100;
    private static final int MAX_RELATIONSHIPS = 200;
    private static final int REPOSITORY_LIMIT = 200;

    private final HistoricalSourceRepository repository;
    private final HistoricalSourceTenantContext tenantContext;
    private final HistoricalSourceAuthorizationPort authorization;

    public SourceCitationGraphService(
        HistoricalSourceRepository repository,
        HistoricalSourceTenantContext tenantContext,
        HistoricalSourceAuthorizationPort authorization
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantContext = Objects.requireNonNull(tenantContext, "tenantContext");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    /**
     * Explores incoming and outgoing relationships using breadth-first traversal.
     * Bounds are deliberately conservative to prevent unbounded graph expansion.
     */
    public SourceCitationGraph explore(
        HistoricalSourceId rootSourceId,
        int maxDepth,
        int maxNodes,
        int maxRelationships
    ) {
        Objects.requireNonNull(rootSourceId, "rootSourceId");
        validateBounds(maxDepth, maxNodes, maxRelationships);

        UUID tenantId = Objects.requireNonNull(tenantContext.requireTenantId(),
            "trusted tenant context is required");
        HistoricalSource root = loadReadableSource(tenantId, rootSourceId);

        Map<HistoricalSourceId, SourceCitationGraphNode> nodes = new LinkedHashMap<>();
        Map<UUID, SourceRelationship> relationships = new LinkedHashMap<>();
        ArrayDeque<Traversal> frontier = new ArrayDeque<>();
        nodes.put(rootSourceId, new SourceCitationGraphNode(root, 0));
        frontier.addLast(new Traversal(rootSourceId, 0));
        boolean truncated = false;

        while (!frontier.isEmpty()) {
            Traversal current = frontier.removeFirst();
            if (current.distance() >= maxDepth) continue;

            int remaining = maxRelationships - relationships.size();
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            int queryLimit = Math.min(REPOSITORY_LIMIT, remaining + 1);
            List<SourceRelationship> outgoing =
                repository.listOutgoingSourceRelationships(tenantId, current.sourceId(), queryLimit);
            List<SourceRelationship> incoming =
                repository.listIncomingSourceRelationships(tenantId, current.sourceId(), queryLimit);

            if (outgoing.size() >= queryLimit || incoming.size() >= queryLimit) truncated = true;
            truncated |= addRelationships(tenantId, current, outgoing, maxNodes, maxRelationships,
                nodes, relationships, frontier);
            if (relationships.size() >= maxRelationships) {
                truncated = truncated || !frontier.isEmpty() || outgoing.size() >= queryLimit || incoming.size() >= queryLimit;
                break;
            }
            truncated |= addRelationships(tenantId, current, incoming, maxNodes, maxRelationships,
                nodes, relationships, frontier);
            if (relationships.size() >= maxRelationships) {
                truncated = truncated || !frontier.isEmpty() || outgoing.size() >= queryLimit || incoming.size() >= queryLimit;
                break;
            }
        }

        return new SourceCitationGraph(rootSourceId, maxDepth,
            List.copyOf(nodes.values()), List.copyOf(relationships.values()), truncated);
    }

    private boolean addRelationships(
        UUID tenantId,
        Traversal current,
        List<SourceRelationship> candidates,
        int maxNodes,
        int maxRelationships,
        Map<HistoricalSourceId, SourceCitationGraphNode> nodes,
        Map<UUID, SourceRelationship> relationships,
        ArrayDeque<Traversal> frontier
    ) {
        boolean truncated = false;
        for (SourceRelationship relationship : candidates) {
            if (relationships.containsKey(relationship.id())) continue;
            if (relationships.size() >= maxRelationships) return true;

            HistoricalSourceId counterpart = relationship.sourceId().equals(current.sourceId())
                ? relationship.relatedSourceId() : relationship.sourceId();
            if (!nodes.containsKey(counterpart)) {
                if (nodes.size() >= maxNodes) {
                    truncated = true;
                    continue;
                }
                HistoricalSource source = loadReadableSource(tenantId, counterpart);
                int distance = current.distance() + 1;
                nodes.put(counterpart, new SourceCitationGraphNode(source, distance));
                frontier.addLast(new Traversal(counterpart, distance));
            }
            relationships.put(relationship.id(), relationship);
        }
        return truncated;
    }

    private HistoricalSource loadReadableSource(UUID tenantId, HistoricalSourceId sourceId) {
        authorization.requireCanReadSourceBibliography(tenantId, sourceId);
        return repository.find(tenantId, sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Historical source not found"));
    }

    private static void validateBounds(int depth, int nodes, int relationships) {
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("maxDepth must be between 0 and " + MAX_DEPTH);
        }
        if (nodes < 1 || nodes > MAX_NODES) {
            throw new IllegalArgumentException("maxNodes must be between 1 and " + MAX_NODES);
        }
        if (relationships < 1 || relationships > MAX_RELATIONSHIPS) {
            throw new IllegalArgumentException("maxRelationships must be between 1 and " + MAX_RELATIONSHIPS);
        }
    }

    private record Traversal(HistoricalSourceId sourceId, int distance) { }
}
