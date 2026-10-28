package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.AkadFormation;
import tech.kayys.numou.numoudomain.FormationPolicy;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.Optional;

/**
 * Keeps the akad formation record a syirkah was opened under, so the facts the formation checks relied on can be
 * audited later. A record is written once, in the same transaction that opens the syirkah, and never changed.
 */
public interface AkadFormationPort {

    /** Writes the record; a second record for the same syirkah is an error. */
    void record(TenantId tenant, SyirkahId syirkah, AkadFormation formation, FormationPolicy policy, Instant at);

    Optional<Recorded> find(TenantId tenant, SyirkahId syirkah);

    /** What was stored: the formation facts, the policy they were checked under, and when. */
    record Recorded(AkadFormation formation, FormationPolicy policy, Instant recordedAt) {
    }
}
