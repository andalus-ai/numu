package tech.kayys.syirkah.accounting.application.hardening;

import tech.kayys.syirkah.accounting.domain.hardening.AuditAction;
import tech.kayys.syirkah.accounting.domain.hardening.AuditRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centralized audit trail recorder for forensic compliance.
 */
public final class AuditTrailService {

    private final List<AuditRecord> records = Collections.synchronizedList(new ArrayList<>());

    public AuditRecord record(String user, AuditAction action, String aggType, String aggId, String detail) {
        AuditRecord rec = AuditRecord.of(user, action, aggType, aggId, detail);
        records.add(rec);
        return rec;
    }

    public List<AuditRecord> findByAggregate(String aggType, String aggId) {
        return records.stream()
                .filter(r -> r.targetAggregateType().equals(aggType) && r.targetAggregateId().equals(aggId))
                .toList();
    }

    public List<AuditRecord> all() { return List.copyOf(records); }
}
