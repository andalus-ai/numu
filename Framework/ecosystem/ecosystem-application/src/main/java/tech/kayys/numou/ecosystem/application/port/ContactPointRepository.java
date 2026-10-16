package tech.kayys.syirkah.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.ecosystem.domain.contact.ContactPoint;
import tech.kayys.syirkah.ecosystem.domain.contact.ContactPointId;

import java.util.Optional;

/**
 * Outbound repository port for ContactPoint persistence (config03.md §P4-13 #2).
 */
public interface ContactPointRepository {

    Uni<ContactPoint> save(ContactPoint contactPoint);

    Uni<Optional<ContactPoint>> findById(ContactPointId id);

    Uni<Boolean> existsById(ContactPointId id);
}
