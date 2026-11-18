package tech.kayys.numou.socialgraph.application;

import java.util.List;
import java.util.Objects;

/** A visible subset of one raw keyset page; the cursor advances past all scanned rows. */
public record SocialGraphRelationshipReadPage(List<SocialGraphRelationshipReadEntry> items, String nextCursor) {
    public SocialGraphRelationshipReadPage {
        items = List.copyOf(Objects.requireNonNull(items, "items cannot be null"));
    }
}
