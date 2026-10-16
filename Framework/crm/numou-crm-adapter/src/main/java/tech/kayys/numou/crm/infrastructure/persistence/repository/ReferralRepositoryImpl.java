package tech.kayys.numou.crm.infrastructure.persistence.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;
import tech.kayys.numou.crm.infrastructure.persistence.entity.ReferralEntity;
import tech.kayys.numou.crm.infrastructure.persistence.mapper.ReferralMapper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Implementation of {@link ReferralRepository} using Hibernate Reactive Panache.
 */
@ApplicationScoped
public class ReferralRepositoryImpl implements ReferralRepository {

    private final ReferralMapper mapper;

    public ReferralRepositoryImpl(ReferralMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CompletionStage<Referral> save(Referral referral) {
        ReferralEntity entity = mapper.toEntity(referral);
        return Panache.withTransaction(() -> Panache.getSession()
                .flatMap(session -> session.merge(entity))
                .replaceWith(referral)
                .map(v -> {
                    referral.clearEvents();
                    return referral;
                }))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<Referral>> findById(ReferralId id) {
    return Panache.withSession(() -> ReferralEntity.<ReferralEntity>findById(id.getValue())
                .map(entity -> entity == null ? Optional.<Referral>empty() : Optional.of(mapper.toDomain(entity)))
                ).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsById(ReferralId id) {
    return Panache.withSession(() -> ReferralEntity.<ReferralEntity>findById(id.getValue()).map(entity -> entity != null)).subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Void> delete(Referral referral) {
        return deleteById(referral.id());
    }

    @Override
    public CompletionStage<Void> deleteById(ReferralId id) {
    return Panache.withTransaction(() -> ReferralEntity.deleteById(id.getValue())
                .map(v -> (Void) null)
                ).subscribe().asCompletionStage();
    }
}
