package tech.kayys.numou.history.source.application;

import tech.kayys.numou.history.source.domain.HistoricalSource;
import java.util.Objects;

/** A source in a bounded citation graph, with its shortest discovered hop distance from the root. */
public record SourceCitationGraphNode(HistoricalSource source, int distance) {
    public SourceCitationGraphNode {
        Objects.requireNonNull(source, "source");
        if (distance < 0) throw new IllegalArgumentException("distance cannot be negative");
    }
}
