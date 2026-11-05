package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
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

    public Uni<ServiceAccountResponse> create(TenantId tenantId, CreateServiceAccountRequest request) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        return unitOfWork.execute(() ->
                serviceAccountRepository.existsByName(tenantId, request.name())
                        .chain(exists -> {
                            if (exists) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "SERVICE_ACCOUNT_EXISTS",
                                        "A service account named '" + request.name() + "' already exists in this tenant"
                                ).toException());
                            }
                            ServiceAccountId id = ServiceAccountId.newId();
                            UserId ownerId = request.ownerUserId() != null ? UserId.of(request.ownerUserId()) : null;
                            var sa = ServiceAccount.create(
                                    id, tenantId, request.name(), request.description(), ownerId, clock.now());
                            return serviceAccountRepository.save(tenantId, sa)
                                    .chain(() -> outbox.append(tenantId.value(), sa.pullDomainEvents()))
                                    .replaceWith(ServiceAccountResponse.from(sa));
                        })
        );
    }

    public Uni<ServiceAccountResponse> disable(TenantId tenantId, UUID id, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        return unitOfWork.execute(() ->
                serviceAccountRepository.findById(tenantId, ServiceAccountId.of(id))
                        .chain(optional -> {
                            if (optional.isEmpty()) {
                                return Uni.createFrom().failure(ApplicationError.of(
                                        "SERVICE_ACCOUNT_NOT_FOUND", "Service account not found"
                                ).toException());
                            }
                            var sa = optional.get();
                            sa.disable(actorId != null ? actorId.toString() : null, clock.now());
                            return serviceAccountRepository.save(tenantId, sa)
                                    .chain(() -> outbox.append(tenantId.value(), sa.pullDomainEvents()))
                                    .replaceWith(ServiceAccountResponse.from(sa));
                        })
        );
    }

    public Uni<List<ServiceAccountResponse>> listAll(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return serviceAccountRepository.findAll(tenantId)
                .map(list -> list.stream().map(ServiceAccountResponse::from).toList());
    }
}
