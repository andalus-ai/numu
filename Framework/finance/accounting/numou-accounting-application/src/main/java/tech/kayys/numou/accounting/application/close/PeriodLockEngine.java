package tech.kayys.syirkah.accounting.application.close;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces hard period locks to protect closed accounting periods from unauthorized postings.
 */
public final class PeriodLockEngine {

    private final Set<String> lockedLedgerPeriods = ConcurrentHashMap.newKeySet();

    public void lockPeriod(String tenantId, String ledgerId, String fiscalPeriodId) {
        lockedLedgerPeriods.add(key(tenantId, ledgerId, fiscalPeriodId));
    }

    public void unlockPeriod(String tenantId, String ledgerId, String fiscalPeriodId) {
        lockedLedgerPeriods.remove(key(tenantId, ledgerId, fiscalPeriodId));
    }

    public boolean isPeriodLocked(String tenantId, String ledgerId, String fiscalPeriodId) {
        return lockedLedgerPeriods.contains(key(tenantId, ledgerId, fiscalPeriodId));
    }

    public void assertNotLocked(String tenantId, String ledgerId, String fiscalPeriodId) {
        if (isPeriodLocked(tenantId, ledgerId, fiscalPeriodId)) {
            throw new IllegalStateException("Period " + fiscalPeriodId + " is hard locked for ledger " + ledgerId + " in tenant " + tenantId);
        }
    }

    private String key(String tenantId, String ledgerId, String periodId) {
        return tenantId + ":" + ledgerId + ":" + periodId;
    }
}
