package tech.kayys.numou.content.domain;
import java.time.Instant;
import java.util.Objects;
public record ContentRevision(int number, String title, String slug, String body, ActorId authorId, Instant createdAt) {
 public ContentRevision {
  if (number < 1) throw new IllegalArgumentException("Revision number must be positive");
  title = required(title, "title"); slug = required(slug, "slug");
  body = Objects.requireNonNull(body, "body"); Objects.requireNonNull(authorId, "authorId"); Objects.requireNonNull(createdAt, "createdAt");
 }
 private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
}