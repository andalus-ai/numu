package tech.kayys.numou.socialgraph.spi;

import java.util.List;
import java.util.Objects;

/** One bounded raw page. nextCursor advances past scanned rows, including rows later hidden by policy. */
public record SocialGraphRelationshipQueryPage(List<SocialGraphRelationshipListEntry> items, String nextCursor) {
    public SocialGraphRelationshipQueryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items cannot be null"));
    }
}
