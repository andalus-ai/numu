package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.LifecycleChange;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.List;

/**
 * Keeps the records behind each lifecycle change (consents with evidence, dissent, profit decision, hak syuf'ah
 * answers, heirs' decisions) so they can be audited later. One record per akad version the change produced,
 * written in the same transaction as the revision and never changed.
 */
public interface LifecycleRecordPort {

    /** Writes the record for the akad version the change produced; a second one for the same version is an error. */
    void record(TenantId tenant, SyirkahId syirkah, int newContractVersion, LifecycleChange change, Instant at);

    /** All records of a syirkah, oldest version first. */
    List<Recorded> list(TenantId tenant, SyirkahId syirkah);

    record Recorded(int contractVersion, LifecycleChange change, Instant recordedAt) {
    }
}
