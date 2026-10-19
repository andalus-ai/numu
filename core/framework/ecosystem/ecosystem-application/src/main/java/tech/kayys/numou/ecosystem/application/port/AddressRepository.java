package tech.kayys.numou.ecosystem.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.contact.Address;
import tech.kayys.numou.ecosystem.domain.contact.AddressId;

import java.util.Optional;

/**
 * Outbound repository port for canonical Address persistence (config03.md §P4-13 #2).
 */
public interface AddressRepository {

    Uni<Address> save(Address address);

    Uni<Optional<Address>> findById(AddressId id);

    Uni<Boolean> existsById(AddressId id);
}
