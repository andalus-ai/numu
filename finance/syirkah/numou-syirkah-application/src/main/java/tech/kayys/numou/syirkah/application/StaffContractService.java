package tech.kayys.numou.numouapplication;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.StaffRecordPort;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numoudomain.StaffContract;
import tech.kayys.numou.numoudomain.StaffContractChecker;
import tech.kayys.numou.numoudomain.Syirkah;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numouapplication.port.TransactionPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Engages staff for a syirkah (Hukum Muamalah Bab XVII). A contract is stored only when it passes
 * {@link StaffContractChecker} against the syirkah's current akad: a fixed wage, a named employer, the work, period
 * and effort stated, no unreviewed jali clauses. Nothing is stored for a contract that fails.
 */
public class StaffContractService {

    private final SyirkahRepository repository;
    private final StaffRecordPort records;
    private final TransactionPort tx;
    private final Clock clock;

    public StaffContractService(SyirkahRepository repository, StaffRecordPort records, TransactionPort tx,
                                Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.records = Objects.requireNonNull(records);
        this.tx = Objects.requireNonNull(tx);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Checks and stores; returns the sequence number. Throws a business-rule violation listing every finding. */
    public int engage(TenantId tenant, SyirkahId id, StaffContract staff) {
        Objects.requireNonNull(staff, "staff");
        return tx.inTransaction(() -> {
            Syirkah s = repository.find(tenant, id)
                    .orElseThrow(() -> new tech.kayys.numou.numoudomain.SyirkahNotFoundException(
                            "unknown syirkah " + id.value()));
            StaffContractChecker.requirePassing(s.contract(), staff);
            return records.add(tenant, id, s.contract().version(), staff, clock.instant());
        });
    }

    public List<StaffRecordPort.Recorded> list(TenantId tenant, SyirkahId id) {
        return records.list(tenant, id);
    }
}
