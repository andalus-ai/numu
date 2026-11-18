package tech.kayys.numou.content.domain;

import java.util.Map;
import java.util.Set;

/** Pure editorial state-transition policy; it performs no authorization or delivery side effects. */
public final class ContentLifecyclePolicy {
 private static final Map<ContentStatus, Set<ContentStatus>> ALLOWED = Map.of(
  ContentStatus.DRAFT, Set.of(ContentStatus.IN_REVIEW, ContentStatus.ARCHIVED),
  ContentStatus.IN_REVIEW, Set.of(ContentStatus.DRAFT, ContentStatus.APPROVED, ContentStatus.ARCHIVED),
  ContentStatus.APPROVED, Set.of(ContentStatus.DRAFT, ContentStatus.SCHEDULED, ContentStatus.PUBLISHED, ContentStatus.ARCHIVED),
  ContentStatus.SCHEDULED, Set.of(ContentStatus.APPROVED, ContentStatus.PUBLISHED, ContentStatus.ARCHIVED),
  ContentStatus.PUBLISHED, Set.of(ContentStatus.UNPUBLISHED, ContentStatus.ARCHIVED),
  ContentStatus.UNPUBLISHED, Set.of(ContentStatus.DRAFT, ContentStatus.IN_REVIEW, ContentStatus.ARCHIVED),
  ContentStatus.ARCHIVED, Set.of(ContentStatus.DRAFT)
 );
 private ContentLifecyclePolicy() { }
 public static boolean canTransition(ContentStatus from, ContentStatus to) {
  return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
 }
}
