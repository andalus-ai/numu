package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.identity.application.dto.CreateServiceAccountRequest;
import tech.kayys.numou.identity.application.dto.ServiceAccountResponse;
import tech.kayys.numou.identity.application.port.IdentityOutboxPort;
import tech.kayys.numou.identity.application.port.ServiceAccountRepository;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccountId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ServiceAccountService {

    private final ServiceAccountRepository serviceAccountRepository;
    private final IdentityOutboxPort outbox;
    private final UnitOfWork unitOfWork;
    private final DomainClock clock;

    public ServiceAccountService(
            ServiceAccountRepository serviceAccountRepository,
            IdentityOutboxPort outbox,
            UnitOfWork unitOfWork,
            DomainClock clock
    ) {
        this.serviceAccountRepository = Objects.requireNonNull(serviceAccountRepository);
        this.outbox = Objects.requireNonNull(outbox);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    public Uni<ServiceAccountResponse> create(CreateServiceAccountRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        return unitOfWork.execute(() ->
                serviceAccountRepository.existsByName(request.name())
                        .chain(exists -> {
                            if (exists) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "SERVICE_ACCOUNT_EXISTS",
                                        "A service account named '" + request.name() + "' already exists"
                                ).toException());
                            }
                            ServiceAccountId id = ServiceAccountId.newId();
                            UserId ownerId = request.ownerUserId() != null ? UserId.of(request.ownerUserId()) : null;
                            var sa = ServiceAccount.create(
                                    id,
                                    null,
                                    request.name(),
                                    request.description(),
                                    ownerId,
                                    clock.now()
                            );
                            return serviceAccountRepository.save(sa)
                                    .chain(() -> outbox.append(null, sa.pullDomainEvents()))
                                    .replaceWith(ServiceAccountResponse.from(sa));
                        })
        );
    }

    public Uni<ServiceAccountResponse> disable(UUID id) {
        return disable(id, null);
    }

    /**
     * Disables the service account, recording the acting principal in the audit metadata.
     *
     * @param id      service account id
     * @param actorId authenticated actor; {@code null} keeps the previously recorded actor
     */
    public Uni<ServiceAccountResponse> disable(UUID id, UUID actorId) {
        Objects.requireNonNull(id, "id cannot be null");
        return unitOfWork.execute(() ->
                serviceAccountRepository.findById(ServiceAccountId.of(id))
                        .chain(optional -> {
                            if (optional.isEmpty()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "SERVICE_ACCOUNT_NOT_FOUND", "Service account not found"
                                ).toException());
                            }
                            var sa = optional.get();
                            sa.disable(actorId != null ? actorId.toString() : null, clock.now());
                            return serviceAccountRepository.save(sa)
                                    .chain(() -> outbox.append(null, sa.pullDomainEvents()))
                                    .replaceWith(ServiceAccountResponse.from(sa));
                        })
        );
    }

    public Uni<List<ServiceAccountResponse>> listAll() {
        return serviceAccountRepository.findAll()
                .map(list -> list.stream().map(ServiceAccountResponse::from).toList());
    }
}
