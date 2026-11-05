package tech.kayys.numou.advertising.domain;
import java.util.UUID;
public record PublisherPropertyId(UUID value) { public PublisherPropertyId { Objects.requireNonNull(value); } public static PublisherPropertyId generate(){return new PublisherPropertyId(UUID.randomUUID());} }
