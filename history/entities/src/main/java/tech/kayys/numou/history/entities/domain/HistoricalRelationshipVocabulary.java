package tech.kayys.numou.history.entities.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable vocabulary registry. Applications may compose additional domain vocabularies. */
public final class HistoricalRelationshipVocabulary {
    private final Map<HistoricalRelationshipKindCode, HistoricalRelationshipDefinition> definitions;

    public HistoricalRelationshipVocabulary(Collection<HistoricalRelationshipDefinition> definitions) {
        Objects.requireNonNull(definitions, "Relationship definitions are required");
        Map<HistoricalRelationshipKindCode, HistoricalRelationshipDefinition> indexed = new LinkedHashMap<>();
        for (var definition : definitions) {
            Objects.requireNonNull(definition, "Relationship definition is required");
            if (indexed.putIfAbsent(definition.code(), definition) != null) {
                throw new IllegalArgumentException("Duplicate relationship code: " + definition.code().value());
            }
        }
        for (var definition : indexed.values()) {
            if (definition.inverseCode() != null) {
                var inverse = indexed.get(definition.inverseCode());
                if (inverse == null || !definition.code().equals(inverse.inverseCode())) {
                    throw new IllegalArgumentException("Inverse relationship definitions must be registered reciprocally");
                }
                if (definition.direction() != HistoricalRelationshipDefinition.Direction.DIRECTED
                        || inverse.direction() != HistoricalRelationshipDefinition.Direction.DIRECTED) {
                    throw new IllegalArgumentException("Inverse relationships must both be directed");
                }
            }
        }
        this.definitions = Map.copyOf(indexed);
    }

    public Optional<HistoricalRelationshipDefinition> find(HistoricalRelationshipKindCode code) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(code)));
    }

    public HistoricalRelationshipDefinition require(HistoricalRelationshipKindCode code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("Unknown relationship kind: " + code.value()));
    }

    public static HistoricalRelationshipVocabulary standard() {
        var peopleCollectives = EnumSet.of(HistoricalEntityKind.PERSON, HistoricalEntityKind.COLLECTIVE);
        var collectives = EnumSet.of(HistoricalEntityKind.COLLECTIVE);
        var collectivePlace = EnumSet.of(HistoricalEntityKind.COLLECTIVE, HistoricalEntityKind.PLACE);
        var allButEvent = EnumSet.of(HistoricalEntityKind.PERSON, HistoricalEntityKind.COLLECTIVE, HistoricalEntityKind.PLACE);
        return new HistoricalRelationshipVocabulary(java.util.List.of(
                directed("founded", peopleCollectives, collectives, false, null),
                directed("successor-of", collectives, collectives, false, "predecessor-of"),
                directed("predecessor-of", collectives, collectives, false, "successor-of"),
                directed("member-of", peopleCollectives, collectives, false, null),
                directed("participated-in", peopleCollectives, EnumSet.of(HistoricalEntityKind.EVENT), false, null),
                directed("occurred-at", EnumSet.of(HistoricalEntityKind.EVENT), EnumSet.of(HistoricalEntityKind.PLACE), false, null),
                directed("located-in", collectivePlace, EnumSet.of(HistoricalEntityKind.PLACE), false, null),
                symmetric("allied-with", collectives),
                symmetric("traded-with", collectives),
                symmetric("related-to", allButEvent)
        ));
    }

    private static HistoricalRelationshipDefinition directed(String code,
            java.util.Set<HistoricalEntityKind> subjects, java.util.Set<HistoricalEntityKind> objects,
            boolean transitive, String inverse) {
        return new HistoricalRelationshipDefinition(HistoricalRelationshipKindCode.of(code),
                HistoricalRelationshipDefinition.Direction.DIRECTED, subjects, objects, transitive,
                inverse == null ? null : HistoricalRelationshipKindCode.of(inverse));
    }

    private static HistoricalRelationshipDefinition symmetric(String code, java.util.Set<HistoricalEntityKind> kinds) {
        return new HistoricalRelationshipDefinition(HistoricalRelationshipKindCode.of(code),
                HistoricalRelationshipDefinition.Direction.SYMMETRIC, kinds, kinds, false, null);
    }
}
