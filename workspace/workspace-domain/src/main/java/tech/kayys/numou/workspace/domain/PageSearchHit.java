package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal search projection; authorization must be checked before exposing a hit. */
public record PageSearchHit(UUID pageId, UUID workspaceId, UUID spaceId, UUID parentPageId,
                            String title, Instant updatedAt) {
    public PageSearchHit {
        Objects.requireNonNull(pageId, "pageId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(spaceId, "spaceId");
        title = Objects.requireNonNull(title, "title");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
