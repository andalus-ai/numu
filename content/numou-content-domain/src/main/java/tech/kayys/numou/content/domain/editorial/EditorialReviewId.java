package tech.kayys.numou.content.domain.editorial;

import java.util.Objects;
import java.util.UUID;

public record EditorialReviewId(UUID value) {
    public EditorialReviewId { Objects.requireNonNull(value, "value"); }
    public static EditorialReviewId newId() { return new EditorialReviewId(UUID.randomUUID()); }
}
