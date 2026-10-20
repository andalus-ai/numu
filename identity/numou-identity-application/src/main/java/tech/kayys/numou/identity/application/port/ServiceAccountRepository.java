package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccountId;

import java.util.List;
import java.util.Optional;

public interface ServiceAccountRepository {

    Uni<Optional<ServiceAccount>> findById(ServiceAccountId id);

    Uni<Optional<ServiceAccount>> findByName(String name);

    Uni<Boolean> existsByName(String name);

    Uni<List<ServiceAccount>> findAll();

    Uni<Void> save(ServiceAccount serviceAccount);
}
