package tech.kayys.numou.crm.infrastructure.persistence.mapper;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.infrastructure.persistence.entity.ReferralEntity;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between {@link Referral} domain and persistence entities.
 */
@ApplicationScoped
public class ReferralMapper {

    public ReferralEntity toEntity(Referral referral) {
        ReferralEntity entity = new ReferralEntity();
        entity.id = referral.id().getValue();
        entity.referrerAccountId = referral.referrerAccountId().getValue();
        entity.target = referral.target();
        entity.type = referral.type();
        entity.status = referral.status();
        entity.code = referral.code();
        entity.description = referral.description();
        entity.referredAt = referral.referredAt();
        entity.acceptedAt = referral.acceptedAt();
        entity.convertedAt = referral.convertedAt();
        entity.targetLeadId = referral.targetLeadId();
        entity.targetOpportunityId = referral.targetOpportunityId();
        entity.externalReference = referral.externalReference();
        entity.active = true;
        entity.createdAt = referral.getCreatedAt();
        entity.updatedAt = referral.getUpdatedAt();
        entity.version = (long) referral.getVersion();
        return entity;
    }

    public Referral toDomain(ReferralEntity entity) {
        Referral referral = Referral.restore(
                ReferralId.of(entity.id),
                AccountId.of(entity.referrerAccountId),
                entity.target,
                entity.type,
                entity.status,
                entity.code,
                entity.description,
                entity.referredAt,
                entity.acceptedAt,
                entity.convertedAt,
                entity.targetLeadId,
                entity.targetOpportunityId,
                entity.externalReference);
        referral.setCreatedAt(entity.createdAt);
        referral.setUpdatedAt(entity.updatedAt);
        referral.setVersion(entity.version != null ? entity.version.intValue() : 0);
        return referral;
    }
}
