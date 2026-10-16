package tech.kayys.syirkah.accounting.application.legal;

import tech.kayys.syirkah.accounting.domain.legal.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LegalService {

    private final Map<String, LegalContract> contracts = new ConcurrentHashMap<>();
    private final Map<String, ComplianceObligation> obligations = new ConcurrentHashMap<>();
    private final Map<String, ComplianceCalendarEntry> calendarEntries = new ConcurrentHashMap<>();

    // ── Contracts ─────────────────────────────────────────────────────────────

    public LegalContract registerContract(String contractId, String title,
                                          String countsyirkaharty, LocalDate startDate, LocalDate endDate) {
        var contract = new LegalContract(contractId, title, countsyirkaharty, startDate, endDate);
        contracts.put(contractId, contract);
        return contract;
    }

    public LegalContract getContract(String contractId) {
        var c = contracts.get(contractId);
        if (c == null) throw new IllegalArgumentException("LegalContract not found: " + contractId);
        return c;
    }

    public List<LegalContract> listContracts() {
        return List.copyOf(contracts.values());
    }

    // ── Compliance Obligations ────────────────────────────────────────────────

    public ComplianceObligation addObligation(String contractId, String title, LocalDate dueDate) {
        var contract = getContract(contractId);
        if (contract.status() == LegalContract.Status.TERMINATED) {
            throw new IllegalStateException("Cannot add obligation to TERMINATED contract: " + contractId);
        }
        var obligation = new ComplianceObligation(UUID.randomUUID().toString(), contractId, title, dueDate);
        obligations.put(obligation.obligationId(), obligation);

        // Auto schedule in compliance calendar
        var entryId = ComplianceCalendarEntryId.newId();
        var calEntry = new ComplianceCalendarEntry(entryId, contractId, obligation.obligationId(),
                title, dueDate, 15, "Standard contract breach clause");
        calendarEntries.put(entryId.value(), calEntry);

        return obligation;
    }

    public List<ComplianceObligation> getObligations(String contractId) {
        getContract(contractId);
        return obligations.values().stream()
                .filter(o -> o.contractId().equals(contractId))
                .toList();
    }

    public List<ComplianceCalendarEntry> getCalendarEntries(String contractId) {
        return calendarEntries.values().stream()
                .filter(e -> e.contractId().equals(contractId))
                .toList();
    }

    public List<LegalContract> getContractsWithUpcomingRenewal(LocalDate asOfDate) {
        return contracts.values().stream()
                .filter(c -> c.isRenewalNoticeDue(asOfDate))
                .toList();
    }
}
