package tech.kayys.numou.workforce.domain.learning;

import java.util.Objects;

public record LearningProviderRef(String type, String id) {
    public LearningProviderRef {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
