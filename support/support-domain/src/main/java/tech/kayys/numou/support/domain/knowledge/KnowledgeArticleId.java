package tech.kayys.numou.support.domain.knowledge;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record KnowledgeArticleId(UUID value) implements DomainId<UUID>, Serializable {

    public KnowledgeArticleId {
        Objects.requireNonNull(value, "KnowledgeArticleId value cannot be null");
    }

    public static KnowledgeArticleId of(UUID value) {
        return new KnowledgeArticleId(value);
    }

    public static KnowledgeArticleId generate() {
        return new KnowledgeArticleId(UUID.randomUUID());
    }

    public static KnowledgeArticleId fromString(String value) {
        return new KnowledgeArticleId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "KnowledgeArticleId{" + value + "}";
    }
}
