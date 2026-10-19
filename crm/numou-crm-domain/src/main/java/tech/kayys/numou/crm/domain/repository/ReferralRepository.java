package tech.kayys.numou.crm.domain.repository;

import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.foundation.domain.repository.Repository;

/**
 * Repository port for {@link Referral} aggregates.
 */
public interface ReferralRepository extends Repository<Referral, ReferralId> {
}