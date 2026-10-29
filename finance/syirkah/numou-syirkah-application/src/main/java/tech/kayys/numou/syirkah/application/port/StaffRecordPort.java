package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numoudomain.StaffContract;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.List;

/**
 * Keeps the staff (ijarah) contracts a syirkah has engaged, each tied to the akad version it was checked against.
 * Append-only: a contract that ends is replaced by recording a new one, never edited.
 */
public interface StaffRecordPort {

    /** Stores the contract and returns its sequence number in the syirkah (1, 2, 3 ...). */
    int add(TenantId tenant, SyirkahId syirkah, int contractVersion, StaffContract staff, Instant at);

    /** All contracts of a syirkah, oldest first. */
    List<Recorded> list(TenantId tenant, SyirkahId syirkah);

    record Recorded(int sequence, int contractVersion, StaffContract staff, Instant recordedAt) {
    }
}
