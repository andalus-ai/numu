package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record RecommendationId(String value) {
    public RecommendationId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("RecommendationId must not be blank");
    }
    public static RecommendationId newId() {
        return new RecommendationId(UUID.randomUUID().toString());
    }
    public static RecommendationId of(String value) {
        return new RecommendationId(value);
    }
}
