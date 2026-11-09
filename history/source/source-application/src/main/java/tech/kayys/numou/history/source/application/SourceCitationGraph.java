package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.HistoricalSourceId;
import tech.kayys.numou.history.source.domain.SourceRelationship;
import java.util.List;
import java.util.Objects;

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
        if (nodes.stream().noneMatch(node -> node.source().id().equals(rootSourceId))) {
            throw new IllegalArgumentException("graph must contain its root source");
        }
        for (SourceRelationship relationship : relationships) {
            boolean sourcePresent = nodes.stream().anyMatch(node -> node.source().id().equals(relationship.sourceId()));
            boolean targetPresent = nodes.stream().anyMatch(node -> node.source().id().equals(relationship.relatedSourceId()));
            if (!sourcePresent || !targetPresent) {
                throw new IllegalArgumentException("graph relationships must reference included nodes");
            }
        }
    }
}
