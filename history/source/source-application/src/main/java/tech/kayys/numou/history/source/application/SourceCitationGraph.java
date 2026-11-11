package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.HistoricalSourceId;
import tech.kayys.numou.history.source.domain.SourceRelationship;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable read projection. It is derived from authoritative source and relationship records. */
public record SourceCitationGraph(
    HistoricalSourceId rootSourceId,
    int maxDepth,
    List<SourceCitationGraphNode> nodes,
    List<SourceRelationship> relationships,
    boolean truncated
) {
    public SourceCitationGraph {
        Objects.requireNonNull(rootSourceId, "rootSourceId");
        if (maxDepth < 0) throw new IllegalArgumentException("maxDepth cannot be negative");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        relationships = List.copyOf(Objects.requireNonNull(relationships, "relationships"));

        Set<HistoricalSourceId> nodeIds = new HashSet<>();
        boolean rootPresent = false;
        for (SourceCitationGraphNode node : nodes) {
            HistoricalSourceId id = node.source().id();
            if (!nodeIds.add(id)) throw new IllegalArgumentException("graph cannot contain duplicate source nodes");
            if (node.distance() > maxDepth) throw new IllegalArgumentException("node distance exceeds graph maxDepth");
            if (id.equals(rootSourceId)) {
                rootPresent = true;
                if (node.distance() != 0) throw new IllegalArgumentException("root source distance must be zero");
            }
        }
        if (!rootPresent) throw new IllegalArgumentException("graph must contain its root source");

        Set<UUID> relationshipIds = new HashSet<>();
        for (SourceRelationship relationship : relationships) {
            if (!relationshipIds.add(relationship.id())) {
                throw new IllegalArgumentException("graph cannot contain duplicate relationships");
            }
            if (!nodeIds.contains(relationship.sourceId()) || !nodeIds.contains(relationship.relatedSourceId())) {
                throw new IllegalArgumentException("graph relationships must reference included nodes");
            }
        }
    }
}
