package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Syirkah aggregate: versioned akad, append-only partner movements and hashed period closes.
 *
 * <p>Fiqh rules enforced here: contract validity (net-profit basis, nisbah, capital, witnesses),
 * profit only above intact capital, loss by capital proportion, mudharabah manager liability only on
 * fault, any change of terms only after a close and with unanimous approval (new akad version),
 * liquidation pays debts first, then capital + profit/loss.</p>
 */
public class Syirkah extends AbstractAggregateRoot<SyirkahId> {

    private static final long serialVersionUID = 1L;

    private final TenantId tenantId;
    private final int scale;
    private final ArrayList<SyirkahContract> contracts = new ArrayList<>();
    private final ArrayList<PartnerMovement> movements = new ArrayList<>();
    private final ArrayList<CloseSnapshot> closes = new ArrayList<>();
    private final LinkedHashMap<PartnerId, long[]> buckets = new LinkedHashMap<>();
    private final LinkedHashMap<PartnerId, String> partnerNames = new LinkedHashMap<>();
    private SyirkahStatus status = SyirkahStatus.ACTIVE;
    private boolean closedSinceRevision;
    private LiquidationResult liquidation;

    private static final int MAX_AMOUNT_SCALE = 18;

    private Syirkah(SyirkahId id, TenantId tenantId, int scale) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (scale < 0 || scale > MAX_AMOUNT_SCALE) {
            throw new IllegalArgumentException("amount scale must be between 0 and " + MAX_AMOUNT_SCALE);
        }
        this.scale = scale;
    }

    // ------------------------------------------------------------------ creation

    /** Opens a syirkah from a valid akad; every promised capital is recorded as a contribution movement. */
    public static Syirkah open(SyirkahId id, TenantId tenantId, SyirkahContract contract, int amountScale,
                               Instant now) {
        Objects.requireNonNull(contract, "contract").requireValid();
        if (contract.version() != 1) {
            throw new BusinessRuleViolation("first contract version must be 1");
        }
        Syirkah s = new Syirkah(id, tenantId, amountScale);
        s.contracts.add(contract);
        for (Partner p : contract.partners()) {
            s.partnerNames.put(p.id(), p.name());
        }
        for (CapitalContribution c : contract.capital()) {
            s.append(c.partner(), MovementType.CONTRIBUTION, s.norm(c.amount()), null,
                    "akad:v1:" + c.partner().value(), now);
        }
        s.raise(new SyirkahEvents.SyirkahOpened(UUID.randomUUID(), now, id, contract.type(), 1));
        return s;
    }

    // ------------------------------------------------------------------ persistence support

    /** Complete, immutable state of the aggregate for persistence adapters. */
    public record Snapshot(SyirkahId id, TenantId tenantId, int scale, SyirkahStatus status,
                           boolean closedSinceRevision, List<SyirkahContract> contracts,
                           List<PartnerMovement> movements, List<CloseSnapshot> closes,
                           LiquidationResult liquidation, int version, Instant createdAt, Instant updatedAt) {
        public Snapshot {
            contracts = List.copyOf(contracts);
            movements = List.copyOf(movements);
            closes = List.copyOf(closes);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(id(), tenantId, scale, status, closedSinceRevision, contracts, movements, closes,
                liquidation, getVersion(), getCreatedAt(), getUpdatedAt());
    }

    /**
     * Rebuilds the aggregate from stored state. Partner positions are re-derived by folding the movements, and
     * both hash chains are verified: a tampered or inconsistent store fails loudly instead of loading.
     */
    public static Syirkah restore(Snapshot st) {
        validateSnapshotStructure(st);
        Syirkah s = new Syirkah(st.id(), st.tenantId(), st.scale());
        s.status = st.status();
        s.closedSinceRevision = st.closedSinceRevision();
        s.contracts.addAll(st.contracts());
        s.closes.addAll(st.closes());
        s.liquidation = st.liquidation();
        for (SyirkahContract c : s.contracts) {
            for (Partner p : c.partners()) {
                s.partnerNames.put(p.id(), p.name());
            }
        }
        for (PartnerMovement m : st.movements()) {
            s.movements.add(m);
            long[] b = s.buckets.computeIfAbsent(m.partner(), k -> new long[4]);
            long u = s.unitsOf(m.amount());
            b[0] += u * m.type().basisSign();
            b[1] += u * m.type().capitalSign();
            b[2] += u * m.type().payableSign();
            b[3] += u * m.type().liabilitySign();
        }
        s.setCreatedAt(st.createdAt());
        s.setUpdatedAt(st.updatedAt());
        s.setVersion(st.version());
        List<String> problems = s.verifyIntegrity();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("stored syirkah " + st.id().value() + " failed integrity: " + problems);
        }
        return s;
    }

    /**
     * Checks cross-record invariants that individual movement/close hashes cannot prove alone.
     * A valid close hash is not enough if the stored contract history was substituted independently.
     */
    private static void validateSnapshotStructure(Snapshot st) {
        if (st == null) {
            throw new IllegalStateException("stored syirkah snapshot is null");
        }
        if (st.id() == null || st.tenantId() == null || st.status() == null
                || st.contracts() == null || st.contracts().isEmpty()
                || st.movements() == null || st.closes() == null
                || st.createdAt() == null || st.updatedAt() == null) {
            throw new IllegalStateException("stored syirkah snapshot is missing required state");
        }
        if (st.updatedAt().isBefore(st.createdAt())) {
            throw new IllegalStateException("stored syirkah snapshot updatedAt precedes createdAt");
        }
        if (st.version() < 0) {
            throw new IllegalStateException("stored syirkah snapshot has negative aggregate version "
                    + st.version());
        }
        if (st.scale() < 0 || st.scale() > MAX_AMOUNT_SCALE) {
            throw new IllegalStateException("stored syirkah snapshot has unsupported amount scale " + st.scale());
        }
        for (int i = 0; i < st.contracts().size(); i++) {
            SyirkahContract contract = st.contracts().get(i);
            if (contract == null || contract.version() != i + 1) {
                throw new IllegalStateException("stored syirkah contract history has a version gap at index " + i);
            }
            if (i > 0 && !contract.effectiveFrom().isAfter(st.contracts().get(i - 1).effectiveFrom())) {
                throw new IllegalStateException("stored syirkah contract effective dates are not strictly increasing at version "
                        + contract.version());
            }
            try {
                contract.requireValid();
            } catch (RuntimeException invalid) {
                throw new IllegalStateException("stored syirkah contract v" + contract.version()
                        + " is invalid: " + invalid.getMessage(), invalid);
            }
        }
        Set<PartnerId> knownPartners = new java.util.HashSet<>();
        for (SyirkahContract contract : st.contracts()) {
            knownPartners.addAll(contract.partnerIds());
        }
        Set<CloseId> knownCloseIds = new java.util.HashSet<>();
        Map<CloseId, Integer> closeIndexes = new LinkedHashMap<>();
        for (int i = 0; i < st.closes().size(); i++) {
            CloseSnapshot close = st.closes().get(i);
            if (close == null || close.id() == null || close.status() == null
                    || close.periodStart() == null || close.periodEnd() == null || close.closedAt() == null
                    || close.contractCanonical() == null || close.result() == null
                    || close.disposition() == null || close.lines() == null
                    || close.previousHash() == null || close.hash() == null) {
                throw new IllegalStateException("stored syirkah close history has missing required metadata at index "
                        + i);
            }
            if (close.periodEnd().isBefore(close.periodStart())) {
                throw new IllegalStateException("stored syirkah close " + close.id().value()
                        + " has period end before period start");
            }
            if (close.closedAt().isBefore(st.createdAt()) || close.closedAt().isAfter(st.updatedAt())) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " timestamp falls outside snapshot lifetime");
            }
            if (!knownCloseIds.add(close.id())) {
                throw new IllegalStateException("stored syirkah close history contains duplicate close id "
                        + close.id().value());
            }
            closeIndexes.put(close.id(), i);
        }
        for (int i = 0; i < st.movements().size(); i++) {
            PartnerMovement movement = st.movements().get(i);
            if (movement == null || movement.id() == null || movement.partner() == null
                    || movement.type() == null || movement.amount() == null || movement.reference() == null
                    || movement.occurredAt() == null || movement.previousHash() == null || movement.hash() == null) {
                throw new IllegalStateException("stored movement has missing required metadata at index " + i);
            }
            if (movement.occurredAt().isBefore(st.createdAt())
                    || movement.occurredAt().isAfter(st.updatedAt())) {
                throw new IllegalStateException("stored movement " + movement.sequence()
                        + " timestamp falls outside snapshot lifetime");
            }
            if (movement.sequence() < 1) {
                throw new IllegalStateException("stored movement has invalid sequence " + movement.sequence());
            }
            try {
                movement.amount().amount().setScale(st.scale(), RoundingMode.UNNECESSARY);
            } catch (ArithmeticException invalidScale) {
                throw new IllegalStateException("stored movement amount exceeds the aggregate accounting scale at sequence "
                        + movement.sequence(), invalidScale);
            }
            java.util.UUID expectedMovementId = java.util.UUID.nameUUIDFromBytes(
                    (movement.sequence() + "|" + movement.partner().value() + "|" + movement.type() + "|"
                            + movement.reference()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!expectedMovementId.equals(movement.id())) {
                throw new IllegalStateException("stored movement id does not match its canonical identity at sequence "
                        + movement.sequence());
            }
            if (!knownPartners.contains(movement.partner())) {
                throw new IllegalStateException("stored movement references a partner absent from contract history");
            }
            if (movement.closeId() != null) {
                Integer closeIndex = closeIndexes.get(movement.closeId());
                if (closeIndex == null) {
                    throw new IllegalStateException("stored movement references a close absent from close history");
                }
                CloseSnapshot linkedClose = st.closes().get(closeIndex);
                if (movement.sequence() < linkedClose.firstMovementSequence()
                        || movement.sequence() > linkedClose.lastMovementSequence()) {
                    throw new IllegalStateException("stored movement references a close outside its recorded movement range");
                }
            }
        }
        Map<CloseId, Integer> supersededByCount = new LinkedHashMap<>();
        CloseSnapshot previousActiveClose = null;
        for (int i = 0; i < st.closes().size(); i++) {
            CloseSnapshot close = st.closes().get(i);
            if (close == null || close.sequence() != i + 1) {
                throw new IllegalStateException("stored syirkah close history has a sequence gap at index " + i);
            }
            if (close.supersedes() != null) {
                Integer supersededIndex = closeIndexes.get(close.supersedes());
                if (supersededIndex == null || supersededIndex >= i) {
                    throw new IllegalStateException("close " + close.id().value()
                            + " supersedes a close that is missing or not earlier in history");
                }
                CloseSnapshot superseded = st.closes().get(supersededIndex);
                if (superseded.status() != CloseStatus.REVERSED) {
                    throw new IllegalStateException("close " + close.id().value()
                            + " supersedes a close that is not marked reversed");
                }
                supersededByCount.merge(superseded.id(), 1, Integer::sum);
            }
            if (close.status() == CloseStatus.ACTIVE) {
                if (previousActiveClose != null && !close.periodStart().isAfter(previousActiveClose.periodEnd())) {
                    throw new IllegalStateException("active close periods overlap or are out of order at close "
                            + close.id().value());
                }
                previousActiveClose = close;
            }
            if (close.contractVersion() < 1 || close.contractVersion() > st.contracts().size()) {
                throw new IllegalStateException("close " + close.id().value()
                        + " references missing contract version " + close.contractVersion());
            }
            SyirkahContract contract = st.contracts().get(close.contractVersion() - 1);
            if (close.periodStart().isBefore(contract.effectiveFrom())) {
                throw new IllegalStateException("close " + close.id().value()
                        + " starts before its akad effective date");
            }
            if (!contract.canonical().equals(close.contractCanonical())) {
                throw new IllegalStateException("close " + close.id().value()
                        + " contract terms do not match stored contract history");
            }
            validateRestoredCloseAccounting(close, st.scale(), contract.currency(), contract.partnerIds(), contract.type());
            long firstMovement = close.firstMovementSequence();
            long lastMovement = close.lastMovementSequence();
            if (firstMovement < 1 || lastMovement < 0 || lastMovement > st.movements().size()
                    || firstMovement > st.movements().size() + 1L || firstMovement > lastMovement + 1L) {
                throw new IllegalStateException("close " + close.id().value()
                        + " has an invalid movement sequence range");
            }
            for (long movementSequence = firstMovement; movementSequence <= lastMovement; movementSequence++) {
                PartnerMovement movement = st.movements().get(Math.toIntExact(movementSequence - 1));
                if (!close.id().equals(movement.closeId())) {
                    throw new IllegalStateException("close " + close.id().value()
                            + " movement range contains unrelated movement " + movementSequence);
                }
            }
        }
        for (CloseSnapshot close : st.closes()) {
            int replacementCount = supersededByCount.getOrDefault(close.id(), 0);
            if (close.status() == CloseStatus.REVERSED && replacementCount != 1) {
                throw new IllegalStateException("reversed close " + close.id().value()
                        + " must have exactly one replacement close");
            }
            if (close.status() == CloseStatus.ACTIVE && replacementCount != 0) {
                throw new IllegalStateException("active close " + close.id().value()
                        + " cannot be superseded by another close");
            }
        }
        if (st.liquidation() != null) {
            if (st.liquidation().completedAt().isBefore(st.createdAt())
                    || st.liquidation().completedAt().isAfter(st.updatedAt())) {
                throw new IllegalStateException("stored liquidation completion timestamp falls outside snapshot lifetime");
            }
            validateRestoredLiquidation(st.liquidation(), st.scale(),
                    st.contracts().get(st.contracts().size() - 1).currency(),
                    st.contracts().get(st.contracts().size() - 1).partnerIds(),
                    st.contracts().get(st.contracts().size() - 1).type());
        }
        boolean hasCloseSinceCurrentAkad = st.closes().stream()
                .anyMatch(close -> close.contractVersion() == st.contracts().get(st.contracts().size() - 1).version());
        if (st.closedSinceRevision() != hasCloseSinceCurrentAkad) {
            throw new IllegalStateException("stored closedSinceRevision flag disagrees with current akad close history");
        }
        if (st.status() == SyirkahStatus.TERMINATED && st.liquidation() == null) {
            throw new IllegalStateException("terminated syirkah is missing its liquidation result");
        }
        if (st.status() != SyirkahStatus.TERMINATED && st.liquidation() != null) {
            throw new IllegalStateException("non-terminated syirkah contains a liquidation result");
        }
    }


    private static void validateRestoredCloseAccounting(CloseSnapshot close, int scale,
            tech.kayys.numou.foundation.domain.valueobject.Currency currency, Set<PartnerId> contractPartners,
            SyirkahType contractType) {
        FinancialResult result = close.result();
        if (result.revenue() == null || result.directCosts() == null || result.operatingCosts() == null
                || result.otherCosts() == null) {
            throw new IllegalStateException("stored close " + close.id().value()
                    + " has incomplete financial result");
        }
        List<Money> amounts = new ArrayList<>(List.of(result.revenue(), result.directCosts(),
                result.operatingCosts(), result.otherCosts()));
        ManagerFault fault = close.fault();
        if (fault != null && fault.negligentOrBreach()) {
            if (contractType != SyirkahType.MUDHARABAH) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " records manager fault outside a mudharabah contract");
            }
            if (!result.netResult().isNegative()) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " records manager fault without a period loss");
            }
            if (fault.attributableLoss() == null || fault.reason() == null || fault.reason().isBlank()) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " has incomplete manager-fault evidence");
            }
            if (fault.attributableLoss().compareTo(result.netResult().negate()) > 0) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " manager-fault attributable loss exceeds the period loss");
            }
            amounts.add(fault.attributableLoss());
        } else if (fault != null && (fault.attributableLoss() != null || fault.reason() != null)) {
            throw new IllegalStateException("stored close " + close.id().value()
                    + " contains manager-fault evidence without a finding of negligence or breach");
        }
        Set<PartnerId> allocatedPartners = new java.util.HashSet<>();
        for (AllocationLine line : close.lines()) {
            if (line == null || line.partner() == null || line.capitalRestored() == null
                    || line.profitShare() == null || line.lossBorne() == null
                    || line.managerLiability() == null || line.retained() == null) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " has incomplete allocation line");
            }
            if (!contractPartners.contains(line.partner())) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " allocation references a partner absent from its contract version");
            }
            if (!allocatedPartners.add(line.partner())) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " contains duplicate allocation lines for partner " + line.partner().value());
            }
            if (line.capitalRestored().isNegative() || line.profitShare().isNegative()
                    || line.lossBorne().isNegative() || line.managerLiability().isNegative()
                    || line.retained().isNegative()) {
                throw new IllegalStateException("stored close " + close.id().value() + " allocation contains a negative component");
            }
            amounts.add(line.capitalRestored());
            amounts.add(line.profitShare());
            amounts.add(line.lossBorne());
            amounts.add(line.managerLiability());
            amounts.add(line.retained());
        }
        if (!allocatedPartners.equals(contractPartners)) {
            throw new IllegalStateException("stored close " + close.id().value()
                    + " allocation lines do not cover every partner in its contract version");
        }
        Money capitalRestored = Money.zero(currency);
        Money profitShare = Money.zero(currency);
        Money lossBorne = Money.zero(currency);
        Money managerLiability = Money.zero(currency);
        Money retained = Money.zero(currency);
        for (AllocationLine line : close.lines()) {
            capitalRestored = capitalRestored.add(line.capitalRestored());
            profitShare = profitShare.add(line.profitShare());
            lossBorne = lossBorne.add(line.lossBorne());
            managerLiability = managerLiability.add(line.managerLiability());
            retained = retained.add(line.retained());
            if (line.capitalRestored().isNegative() || line.profitShare().isNegative()
                    || line.lossBorne().isNegative() || line.managerLiability().isNegative()
                    || line.retained().isNegative()) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " contains a negative allocation component");
            }
            if (line.retained().compareTo(line.profitShare()) > 0) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " retains more than the partner profit share");
            }
            if (close.disposition() == ProfitDisposition.RETAIN
                    && line.retained().compareTo(line.profitShare()) != 0) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " retained disposition does not retain each partner's profit share");
            }
            if (close.disposition() == ProfitDisposition.DISTRIBUTE && !line.retained().isZero()) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " distributes profit but contains retained amounts");
            }
        }
        Money netResult = result.netResult();
        if (netResult.isPositive()) {
            if (lossBorne.isPositive() || managerLiability.isPositive()
                    || capitalRestored.add(profitShare).compareTo(netResult) != 0) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " profit allocation does not reconcile to net result");
            }
        } else if (netResult.isNegative()) {
            if (capitalRestored.isPositive() || profitShare.isPositive() || retained.isPositive()
                    || lossBorne.add(managerLiability).compareTo(netResult.negate()) > 0) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " loss allocation does not reconcile to net result");
            }
        } else if (!capitalRestored.isZero() || !profitShare.isZero() || !lossBorne.isZero()
                || !managerLiability.isZero() || !retained.isZero()) {
            throw new IllegalStateException("stored close " + close.id().value()
                    + " zero-result close contains non-zero allocations");
        }
        for (Money amount : amounts) {
            if (!amount.currency().equals(currency)) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " amount currency does not match its contract version");
            }
            try {
                amount.amount().setScale(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException invalidScale) {
                throw new IllegalStateException("stored close " + close.id().value()
                        + " amount exceeds the aggregate accounting scale", invalidScale);
            }
        }
    }

    private static void validateRestoredLiquidation(LiquidationResult result, int scale,
                                                       tech.kayys.numou.foundation.domain.valueobject.Currency currency,
                                                       Set<PartnerId> knownPartners, SyirkahType contractType) {
        if (result.input() == null || result.residual() == null || result.netResult() == null
                || result.lines() == null || result.payouts() == null
                || result.completedAt() == null || result.hash() == null) {
            throw new IllegalStateException("stored liquidation result has missing required metadata");
        }
        if (!result.input().shufahOffered()) {
            throw new IllegalStateException("stored liquidation does not confirm that partners were offered hak syuf'ah");
        }
        List<Money> amounts = new ArrayList<>();
        amounts.add(result.input().assetProceeds());
        amounts.add(result.input().externalDebts());
        amounts.add(result.residual());
        amounts.add(result.netResult());
        Set<PartnerId> allocatedPartners = new java.util.HashSet<>();
        for (AllocationLine line : result.lines()) {
            if (line == null || line.partner() == null || line.capitalRestored() == null
                    || line.profitShare() == null || line.lossBorne() == null
                    || line.managerLiability() == null || line.retained() == null) {
                throw new IllegalStateException("stored liquidation result has incomplete allocation line");
            }
            if (!knownPartners.contains(line.partner())) {
                throw new IllegalStateException("stored liquidation allocation references a partner absent from current akad");
            }
            if (!allocatedPartners.add(line.partner())) {
                throw new IllegalStateException("stored liquidation result contains duplicate allocation lines for partner "
                        + line.partner().value());
            }
            if (line.capitalRestored().isNegative() || line.profitShare().isNegative()
                    || line.lossBorne().isNegative() || line.managerLiability().isNegative()
                    || line.retained().isNegative()) {
                throw new IllegalStateException("stored liquidation allocation contains a negative component");
            }
            if (contractType != SyirkahType.MUDHARABAH && line.managerLiability().isPositive()) {
                throw new IllegalStateException("stored liquidation records manager liability outside a mudharabah contract");
            }
            amounts.add(line.capitalRestored());
            amounts.add(line.profitShare());
            amounts.add(line.lossBorne());
            amounts.add(line.managerLiability());
            amounts.add(line.retained());
        }
        if (!allocatedPartners.equals(knownPartners)) {
            throw new IllegalStateException("stored liquidation allocation lines do not cover every partner in current akad");
        }
        Set<PartnerId> payoutPartners = new java.util.HashSet<>();
        for (LiquidationResult.Payout payout : result.payouts()) {
            if (payout == null || payout.partner() == null || payout.cashReceived() == null || payout.owed() == null) {
                throw new IllegalStateException("stored liquidation result has incomplete payout");
            }
            if (!knownPartners.contains(payout.partner())) {
                throw new IllegalStateException("stored liquidation payout references a partner absent from current akad");
            }
            if (!payoutPartners.add(payout.partner())) {
                throw new IllegalStateException("stored liquidation result contains duplicate payouts for partner "
                        + payout.partner().value());
            }
            if (payout.cashReceived().isNegative() || payout.owed().isNegative()) {
                throw new IllegalStateException("stored liquidation payout contains a negative amount");
            }
            amounts.add(payout.cashReceived());
            amounts.add(payout.owed());
        }
        if (!payoutPartners.equals(knownPartners)) {
            throw new IllegalStateException("stored liquidation payouts do not cover every partner in current akad");
        }
        Money allocatedCapitalRestored = Money.zero(currency);
        Money allocatedProfit = Money.zero(currency);
        Money allocatedLoss = Money.zero(currency);
        Money allocatedManagerLiability = Money.zero(currency);
        for (AllocationLine line : result.lines()) {
            allocatedCapitalRestored = allocatedCapitalRestored.add(line.capitalRestored());
            allocatedProfit = allocatedProfit.add(line.profitShare());
            allocatedLoss = allocatedLoss.add(line.lossBorne());
            allocatedManagerLiability = allocatedManagerLiability.add(line.managerLiability());
            if (!line.retained().isZero()) {
                throw new IllegalStateException("stored liquidation cannot retain partner profit");
            }
        }
        if (result.netResult().isPositive()) {
            if (allocatedLoss.isPositive() || allocatedManagerLiability.isPositive()
                    || allocatedCapitalRestored.add(allocatedProfit).compareTo(result.netResult()) != 0) {
                throw new IllegalStateException("stored liquidation profit allocation does not reconcile to net result");
            }
        } else if (result.netResult().isNegative()) {
            if (allocatedCapitalRestored.isPositive() || allocatedProfit.isPositive()
                    || allocatedLoss.add(allocatedManagerLiability).add(result.unallocatedLoss())
                            .compareTo(result.netResult().negate()) != 0) {
                throw new IllegalStateException("stored liquidation loss allocation does not reconcile to net result");
            }
        } else if (!allocatedCapitalRestored.isZero() || !allocatedProfit.isZero()
                || !allocatedLoss.isZero() || !allocatedManagerLiability.isZero()) {
            throw new IllegalStateException("stored zero-result liquidation contains non-zero allocations");
        }
        for (Money amount : amounts) {
            if (!amount.currency().equals(currency)) {
                throw new IllegalStateException("stored liquidation amount currency does not match current akad");
            }
            try {
                amount.amount().setScale(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException invalidScale) {
                throw new IllegalStateException("stored liquidation amount exceeds the aggregate accounting scale",
                        invalidScale);
            }
        }
        Money expectedResidual = result.input().assetProceeds().subtract(result.input().externalDebts());
        if (result.residual().compareTo(expectedResidual) != 0) {
            throw new IllegalStateException("stored liquidation residual does not equal asset proceeds minus external debts");
        }
    }

    // ------------------------------------------------------------------ queries

    public TenantId tenantId() {
        return tenantId;
    }

    public int scale() {
        return scale;
    }

    public SyirkahStatus status() {
        return status;
    }

    public SyirkahContract contract() {
        return contracts.get(contracts.size() - 1);
    }

    public List<SyirkahContract> contractHistory() {
        return Collections.unmodifiableList(contracts);
    }

    public List<PartnerMovement> movements() {
        return Collections.unmodifiableList(movements);
    }

    public List<CloseSnapshot> closes() {
        return Collections.unmodifiableList(closes);
    }

    public LiquidationResult liquidation() {
        return liquidation;
    }

    public CloseSnapshot close(CloseId id) {
        return closes.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolation("unknown close " + id.value()));
    }

    public PartnerPosition position(PartnerId partner) {
        long[] b = buckets.getOrDefault(partner, new long[4]);
        var cur = contract().currency();
        return new PartnerPosition(partner, units(b[0], cur), units(b[1], cur), units(b[2], cur), units(b[3], cur));
    }

    public Map<PartnerId, PartnerPosition> positions() {
        Map<PartnerId, PartnerPosition> m = new TreeMap<>();
        for (PartnerId p : buckets.keySet()) {
            m.put(p, position(p));
        }
        return m;
    }

    private Money units(long unitsValue, tech.kayys.numou.foundation.domain.valueobject.Currency cur) {
        return Money.of(BigDecimal.valueOf(unitsValue, scale), cur);
    }

    // ------------------------------------------------------------------ period close

    public CloseSnapshot closePeriod(CloseId closeId, LocalDate periodStart, LocalDate periodEnd,
                                     FinancialResult result, ManagerFault fault, ProfitDisposition disposition,
                                     Instant now) {
        requireActive();
        Objects.requireNonNull(closeId, "closeId");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(now, "now");
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleViolation("period end before start");
        }
        CloseSnapshot last = lastActiveClose();
        if (last != null && !periodStart.isAfter(last.periodEnd())) {
            throw new BusinessRuleViolation("period overlaps previous close ending " + last.periodEnd());
        }
        if (closes.stream().anyMatch(c -> c.id().equals(closeId))) {
            throw new BusinessRuleViolation("close id already used");
        }
        return doClose(closeId, periodStart, periodEnd, result, fault, disposition, null, now);
    }

    private CloseSnapshot doClose(CloseId closeId, LocalDate start, LocalDate end, FinancialResult result,
                                  ManagerFault fault, ProfitDisposition disposition, CloseId supersedes,
                                  Instant now) {
        SyirkahContract c = contract();
        result = normalize(result);
        Money net = result.netResult();
        ManagerFault f = normalizeFault(fault);
        if (f.negligentOrBreach()) {
            if (c.type() != SyirkahType.MUDHARABAH) {
                throw new BusinessRuleViolation("manager fault applies only to mudharabah");
            }
            if (!net.isNegative()) {
                throw new BusinessRuleViolation("manager fault requires a loss in the period");
            }
            if (f.attributableLoss().compareTo(net.negate()) > 0) {
                throw new BusinessRuleViolation("attributable loss exceeds the period loss");
            }
        }
        Map<PartnerId, PartnerCapitalState> states = new LinkedHashMap<>();
        for (Partner p : c.partners()) {
            states.put(p.id(), position(p.id()).capitalState());
        }
        List<AllocationLine> lines = ProfitLossAllocator.allocate(c, states, net, f, disposition, scale);

        CloseSnapshot prev = closes.isEmpty() ? null : closes.get(closes.size() - 1);
        long first = movements.size() + 1;
        String ref = "close:" + closeId.value();
        for (AllocationLine l : lines) {
            addIfNonZero(l.partner(), MovementType.CAPITAL_RESTORED, l.capitalRestored(), closeId, ref, now);
            addIfNonZero(l.partner(), MovementType.PROFIT_ALLOCATED, l.profitShare(), closeId, ref, now);
            addIfNonZero(l.partner(), MovementType.PROFIT_RETAINED, l.retained(), closeId, ref, now);
            addIfNonZero(l.partner(), MovementType.LOSS_ALLOCATED, l.lossBorne(), closeId, ref, now);
            addIfNonZero(l.partner(), MovementType.MANAGER_LOSS_LIABILITY, l.managerLiability(), closeId, ref, now);
        }
        long lastSeq = movements.size();
        int seq = closes.size() + 1;
        String prevHash = prev == null ? AuditHash.GENESIS : prev.hash();
        String hash = CloseSnapshot.computeHashV3(closeId, seq, start, end, c.version(), c.canonical(), result,
                disposition, f, lines, supersedes, prevHash, first, lastSeq);
        CloseSnapshot snap = new CloseSnapshot(closeId, seq, start, end, c.version(), c.canonical(), result,
                disposition, f, lines, supersedes, CloseStatus.ACTIVE, first, lastSeq, now, prevHash, hash);
        closes.add(snap);
        closedSinceRevision = true;
        raise(new SyirkahEvents.PeriodClosed(UUID.randomUUID(), now, id(), closeId, net, disposition, hash));
        touch(now);
        return snap;
    }

    // ------------------------------------------------------------------ distribution / settlement

    /** Pays out allocated profit. Idempotent per {@code reference}. */
    public void distributeProfit(PartnerId partner, Money amount, String reference, Instant now) {
        requireActive();
        if (settle(partner, amount, reference, MovementType.PROFIT_DISTRIBUTED, now)) {
            raise(new SyirkahEvents.ProfitDistributed(UUID.randomUUID(), now, id(), partner, norm(amount), reference));
        }
    }

    /** Records that a partner paid back what he owes (manager fault / deficit). Idempotent per reference. */
    public void settleLiability(PartnerId partner, Money amount, String reference, Instant now) {
        requireStatusNot(SyirkahStatus.TERMINATED);
        if (settle(partner, amount, reference, MovementType.LIABILITY_SETTLED, now)) {
            raise(new SyirkahEvents.LiabilitySettled(UUID.randomUUID(), now, id(), partner, norm(amount), reference));
        }
    }

    private boolean settle(PartnerId partner, Money amount, String reference, MovementType type, Instant now) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessRuleViolation("a settlement needs an idempotency reference");
        }
        Money a = norm(amount);
        if (!a.isPositive()) {
            throw new BusinessRuleViolation("amount must be positive");
        }
        String key = "pay:" + reference;
        for (PartnerMovement m : movements) {
            if (m.type() == type && m.partner().equals(partner) && key.equals(m.reference())) {
                if (m.amount().compareTo(a) == 0) {
                    return false; // idempotent replay: no new movement or domain event
                }
                throw new BusinessRuleViolation("reference " + reference + " already used with a different amount");
            }
        }
        PartnerPosition pos = position(partner);
        Money open = type == MovementType.PROFIT_DISTRIBUTED ? pos.payable() : pos.liability();
        if (a.compareTo(open) > 0) {
            throw new BusinessRuleViolation("amount " + a.amount() + " exceeds open balance " + open.amount());
        }
        append(partner, type, a, null, key, now);
        touch(now);
        return true;
    }

    // ------------------------------------------------------------------ revision of akad

    /**
     * New akad version (capital addition, new nisbah, partner replacement). Only after a close,
     * with unanimous approval of old and new partners; retained profit is already partner capital.
     * The new contract's capital per partner is compared with the current capital balance and the
     * difference is contributed or returned; impaired capital is reset to the balance.
     */
    public void reviseContract(SyirkahContract next, Set<PartnerId> approvals, Instant now) {
        Objects.requireNonNull(now, "now");
        requireActive();
        SyirkahContract cur = contract();
        next.requireValid();
        if (next.version() != cur.version() + 1) {
            throw new BusinessRuleViolation("next contract version must be " + (cur.version() + 1));
        }
        if (next.type() != cur.type()) {
            throw new BusinessRuleViolation("changing the syirkah type requires a new syirkah");
        }
        if (!next.currency().equals(cur.currency())) {
            throw new BusinessRuleViolation("currency cannot change");
        }
        CloseSnapshot last = lastActiveClose();
        if (last == null || !closedSinceRevision) {
            throw new BusinessRuleViolation("books must be closed (tutup buku) before the akad can change");
        }
        if (!next.effectiveFrom().isAfter(last.periodEnd())) {
            throw new BusinessRuleViolation("new akad must take effect after the closed period");
        }
        Set<PartnerId> required = new java.util.HashSet<>(cur.partnerIds());
        required.addAll(next.partnerIds());
        if (approvals == null || !approvals.containsAll(required)) {
            throw new BusinessRuleViolation("all current and new partners must approve (unanimity)");
        }
        String ref = "akad:v" + next.version();
        Set<PartnerId> all = new java.util.LinkedHashSet<>(buckets.keySet());
        all.addAll(next.partnerIds());

        // Preflight the entire revision before appending any movement. A departing partner
        // may still have an open payable/liability even when earlier partners in iteration
        // order would otherwise receive capital adjustments. Rejecting halfway through the
        // loop would leave the aggregate partially revised despite the akad not changing.
        Map<PartnerId, Money> balances = new LinkedHashMap<>();
        Map<PartnerId, Money> targets = new LinkedHashMap<>();
        for (PartnerId p : all) {
            boolean stays = next.hasPartner(p);
            PartnerPosition pos = buckets.containsKey(p) ? position(p) : null;
            if (!stays && pos != null && (pos.payable().isPositive() || pos.liability().isPositive())) {
                throw new BusinessRuleViolation("departing partner has open payable/liability; settle first");
            }
            balances.put(p, pos == null ? Money.zero(cur.currency()) : pos.balance());
            targets.put(p, stays ? norm(next.capitalOf(p)) : Money.zero(cur.currency()));
        }

        // All caller-controlled business-rule checks are complete; now apply the planned
        // capital/basis movements and only then publish the new contract version.
        for (PartnerId p : all) {
            PartnerPosition pos = buckets.containsKey(p) ? position(p) : null;
            Money balance = balances.get(p);
            Money target = targets.get(p);
            if (pos != null && pos.basis().compareTo(pos.balance()) > 0) {
                append(p, MovementType.BASIS_CLOSED, pos.basis().subtract(pos.balance()), null,
                        ref + ":reset:" + p.value(), now);
            }
            Money diff = target.subtract(balance);
            if (diff.isPositive()) {
                append(p, MovementType.CONTRIBUTION, diff, null, ref + ":" + p.value(), now);
            } else if (diff.isNegative()) {
                append(p, MovementType.CAPITAL_RETURN, diff.negate(), null, ref + ":" + p.value(), now);
            }
        }
        for (Partner p : next.partners()) {
            partnerNames.put(p.id(), p.name());
        }
        contracts.add(next);
        closedSinceRevision = false;
        raise(new SyirkahEvents.ContractRevised(UUID.randomUUID(), now, id(), next.version()));
        touch(now);
    }

    // ------------------------------------------------------------------ correction

    /**
     * Corrects the latest effective close: reverses its movements and recomputes with the corrected
     * result. Allowed after distributions: profit paid beyond the corrected entitlement becomes a liability of that partner
     * ({@link MovementType#OVERPAYMENT_TO_LIABILITY}); any other later movement (e.g. a settlement) refuses it.
     */
    public CloseSnapshot correctLatestClose(CloseId target, CloseId replacementId, FinancialResult corrected,
                                            ManagerFault fault, ProfitDisposition disposition, Instant now) {
        requireActive();
        Objects.requireNonNull(replacementId, "replacementId");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(now, "now");
        CloseSnapshot last = lastActiveClose();
        if (last == null || !last.id().equals(target)) {
            throw new BusinessRuleViolation("only the latest effective close can be corrected");
        }
        if (last.contractVersion() != contract().version()) {
            throw new BusinessRuleViolation("the akad changed after that close; it can no longer be corrected");
        }
        if (closes.stream().anyMatch(c -> c.id().equals(replacementId))) {
            throw new BusinessRuleViolation("replacement close id already used");
        }
        // Validate all caller-controlled accounting inputs before reversing any existing
        // movements. doClose validates the same rules, but doing it after reversal would
        // leave the aggregate partially mutated if the replacement close is rejected.
        FinancialResult normalizedCorrected = normalize(corrected);
        ManagerFault normalizedFault = normalizeFault(fault);
        Money correctedNet = normalizedCorrected.netResult();
        if (normalizedFault.negligentOrBreach()) {
            if (contract().type() != SyirkahType.MUDHARABAH) {
                throw new BusinessRuleViolation("manager fault applies only to mudharabah");
            }
            if (!correctedNet.isNegative()) {
                throw new BusinessRuleViolation("manager fault requires a loss in the period");
            }
            if (normalizedFault.attributableLoss().compareTo(correctedNet.negate()) > 0) {
                throw new BusinessRuleViolation("attributable loss exceeds the period loss");
            }
        }
        for (PartnerMovement m : movements) {
            boolean correctionGeneratedLiability = m.type() == MovementType.OVERPAYMENT_TO_LIABILITY
                    && last.id().equals(m.closeId());
            if (m.sequence() > last.lastMovementSequence()
                    && m.type() != MovementType.PROFIT_DISTRIBUTED
                    && !correctionGeneratedLiability) {
                throw new BusinessRuleViolation("only profit distributions or correction-generated overpayment "
                        + "liabilities may follow a close that is corrected; found "
                        + m.type() + " at " + m.sequence());
            }
        }
        Money oldNet = last.netResult();
        List<PartnerMovement> toReverse = new ArrayList<>();
        for (PartnerMovement m : movements) {
            if (target.equals(m.closeId())) {
                toReverse.add(m);
            }
        }
        for (int i = toReverse.size() - 1; i >= 0; i--) {
            PartnerMovement m = toReverse.get(i);
            append(m.partner(), m.type(), m.amount().negate(), m.closeId(), "reversal:" + m.sequence(), now);
        }
        replaceClose(last.withStatus(CloseStatus.REVERSED));
        CloseSnapshot snap = doClose(replacementId, last.periodStart(), last.periodEnd(),
                normalizedCorrected, normalizedFault, disposition, target, now);
        // Profit already paid beyond the corrected entitlement is owed back (no negative payables).
        for (Partner p : contract().partners()) {
            Money payable = position(p.id()).payable();
            if (payable.isNegative()) {
                append(p.id(), MovementType.OVERPAYMENT_TO_LIABILITY, payable.negate(), replacementId,
                        "overpayment:" + replacementId.value(), now);
            }
        }
        raise(new SyirkahEvents.CloseCorrected(UUID.randomUUID(), now, id(), target, replacementId,
                snap.netResult().subtract(oldNet)));
        return snap;
    }

    // ------------------------------------------------------------------ liquidation

    public void startLiquidation(Instant now) {
        Objects.requireNonNull(now, "now");
        requireActive();
        status = SyirkahStatus.LIQUIDATING;
        touch(now);
    }

    /**
     * Completes liquidation: debts first, then partners' claims (capital + undistributed profit);
     * the remainder is profit (by nisbah) or loss (by capital).
     */
    public LiquidationResult liquidate(LiquidationInput input, ManagerFault fault, Instant now) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(now, "now");
        requireStatusNot(SyirkahStatus.TERMINATED);
        if (status != SyirkahStatus.LIQUIDATING) {
            throw new InvalidStateException("liquidation must be started first");
        }
        if (!input.shufahOffered()) {
            throw new BusinessRuleViolation("hak syuf'ah: partners must be offered the assets before sale to outsiders");
        }
        // A prior close may have recorded a manager-fault liability. Do not terminate the
        // partnership while such a receivable is still open: otherwise the liability would
        // remain on a terminated aggregate without being recovered or explicitly carried forward.
        for (Partner p : contract().partners()) {
            if (position(p.id()).liability().isPositive()) {
                throw new BusinessRuleViolation("outstanding partner liability must be settled before liquidation: "
                        + p.id().value());
            }
        }
        SyirkahContract c = contract();
        Money proceeds = norm(input.assetProceeds());
        Money debts = norm(input.externalDebts());
        Money residual = proceeds.subtract(debts);

        Money claims = Money.zero(c.currency());
        Map<PartnerId, PartnerCapitalState> states = new LinkedHashMap<>();
        Map<PartnerId, Money> claim = new LinkedHashMap<>();
        for (Partner p : c.partners()) {
            PartnerPosition pos = position(p.id());
            states.put(p.id(), pos.capitalState());
            Money cl = pos.balance().add(pos.payable());
            claim.put(p.id(), cl);
            claims = claims.add(cl);
        }
        Money net = residual.subtract(claims);
        ManagerFault f = normalizeFault(fault);
        if (f.negligentOrBreach() && (c.type() != SyirkahType.MUDHARABAH || !net.isNegative()
                || f.attributableLoss().compareTo(net.negate()) > 0)) {
            throw new BusinessRuleViolation("invalid manager fault for this liquidation");
        }
        List<AllocationLine> lines = ProfitLossAllocator.allocate(c, states, net, f, ProfitDisposition.DISTRIBUTE,
                scale);
        String ref = "liquidation";
        List<LiquidationResult.Payout> payouts = new ArrayList<>();
        for (AllocationLine l : lines) {
            PartnerId p = l.partner();
            addIfNonZero(p, MovementType.CAPITAL_RESTORED, l.capitalRestored(), null, ref, now);
            addIfNonZero(p, MovementType.PROFIT_ALLOCATED, l.profitShare(), null, ref, now);
            addIfNonZero(p, MovementType.LOSS_ALLOCATED, l.lossBorne(), null, ref, now);
            addIfNonZero(p, MovementType.MANAGER_LOSS_LIABILITY, l.managerLiability(), null, ref, now);
            PartnerPosition pos = position(p);
            Money cash = Money.zero(c.currency());
            Money owed = Money.zero(c.currency());
            if (pos.payable().isPositive()) {
                append(p, MovementType.PROFIT_DISTRIBUTED, pos.payable(), null, ref + ":profit", now);
                cash = cash.add(pos.payable());
            }
            if (pos.balance().isPositive()) {
                append(p, MovementType.CAPITAL_RETURN, pos.balance(), null, ref + ":capital", now);
                cash = cash.add(pos.balance());
            } else if (pos.balance().isNegative()) {
                append(p, MovementType.DEFICIT_TO_LIABILITY, pos.balance().negate(), null, ref + ":deficit", now);
                owed = owed.add(pos.balance().negate());
            }
            PartnerPosition after = position(p);
            if (!after.basis().isZero()) {
                append(p, MovementType.BASIS_CLOSED, after.basis(), null, ref + ":basis", now);
            }
            owed = owed.add(l.managerLiability());
            payouts.add(new LiquidationResult.Payout(p, cash, owed));
        }
        String liquidationHash = LiquidationResult.computeHash(input, residual, net, lines, payouts, now);
        liquidation = new LiquidationResult(input, residual, net, lines, payouts, now, liquidationHash);
        status = SyirkahStatus.TERMINATED;
        raise(new SyirkahEvents.LiquidationCompleted(UUID.randomUUID(), now, id(), net));
        touch(now);
        return liquidation;
    }

    // ------------------------------------------------------------------ reports

    /** Statement of one partner with running capital balance, payable and liability after each movement. */
    public PartnerStatement statement(PartnerId partner) {
        var cur = contract().currency();
        long[] run = new long[4];
        List<StatementLine> lines = new ArrayList<>();
        for (PartnerMovement m : movements) {
            if (!m.partner().equals(partner)) {
                continue;
            }
            long u = unitsOf(m.amount());
            run[0] += u * m.type().basisSign();
            run[1] += u * m.type().capitalSign();
            run[2] += u * m.type().payableSign();
            run[3] += u * m.type().liabilitySign();
            lines.add(new StatementLine(m.sequence(), m.occurredAt(), m.type(), m.reference(), m.amount(),
                    units(run[1], cur), units(run[2], cur), units(run[3], cur)));
        }
        return new PartnerStatement(partner, partnerNames.get(partner), lines, position(partner));
    }

    /** Report of one close (any status), including per-partner outcome. */
    public CloseReport closeReport(CloseId id) {
        CloseSnapshot c = close(id);
        var cur = contract().currency();
        Money zero = Money.zero(cur);
        Money profit = zero;
        Money restored = zero;
        Money loss = zero;
        Money liability = zero;
        List<CloseReport.Row> rows = new ArrayList<>();
        for (AllocationLine l : c.lines()) {
            profit = profit.add(l.profitShare());
            restored = restored.add(l.capitalRestored());
            loss = loss.add(l.lossBorne());
            liability = liability.add(l.managerLiability());
            rows.add(new CloseReport.Row(l.partner(), partnerNames.get(l.partner()), l.capitalRestored(),
                    l.profitShare(), l.retained(), l.payable(), l.lossBorne(), l.managerLiability()));
        }
        return new CloseReport(c.id(), c.sequence(), c.periodStart(), c.periodEnd(), c.contractVersion(), c.status(),
                c.result(), c.netResult(), c.disposition(), rows, profit, restored, loss, liability);
    }

    /** Capital roll-forward per partner for movements in (from, to]; opening is the balance at {@code from}. */
    public CapitalRollForward capitalRollForward(Instant from, Instant to) {
        var cur = contract().currency();
        Map<PartnerId, long[]> acc = new LinkedHashMap<>(); // opening, contrib, returns, retained, restored, loss, deficit
        for (PartnerId p : buckets.keySet()) {
            acc.put(p, new long[7]);
        }
        for (PartnerMovement m : movements) {
            long u = unitsOf(m.amount());
            long[] a = acc.get(m.partner());
            boolean before = !m.occurredAt().isAfter(from);
            boolean inside = !before && !m.occurredAt().isAfter(to);
            if (before) {
                a[0] += u * m.type().capitalSign();
            } else if (inside) {
                switch (m.type()) {
                    case CONTRIBUTION -> a[1] += u;
                    case CAPITAL_RETURN -> a[2] += u;
                    case PROFIT_RETAINED -> a[3] += u;
                    case CAPITAL_RESTORED -> a[4] += u;
                    case LOSS_ALLOCATED -> a[5] += u;
                    case DEFICIT_TO_LIABILITY -> a[6] += u;
                    default -> { }
                }
            }
        }
        List<CapitalRollForward.Row> rows = new ArrayList<>();
        for (Map.Entry<PartnerId, long[]> e : acc.entrySet()) {
            long[] a = e.getValue();
            long closing = a[0] + a[1] - a[2] + a[3] + a[4] - a[5] + a[6];
            rows.add(new CapitalRollForward.Row(e.getKey(), partnerNames.get(e.getKey()), units(a[0], cur),
                    units(a[1], cur), units(a[2], cur), units(a[3], cur), units(a[4], cur), units(a[5], cur),
                    units(a[6], cur), units(closing, cur)));
        }
        return new CapitalRollForward(from, to, rows);
    }

    /** See {@link EquityReconciliation}. */
    public EquityReconciliation reconcile() {
        var cur = contract().currency();
        long equity = 0;
        for (long[] b : buckets.values()) {
            if (b.length == 4) {
                equity += b[1] + b[2] - b[3];
            }
        }
        long cash = 0;
        for (PartnerMovement m : movements) {
            long u = unitsOf(m.amount());
            switch (m.type()) {
                case CONTRIBUTION, LIABILITY_SETTLED -> cash += u;
                case CAPITAL_RETURN, PROFIT_DISTRIBUTED -> cash -= u;
                default -> { }
            }
        }
        long result = 0;
        for (CloseSnapshot c : closes) {
            if (c.status() == CloseStatus.ACTIVE) {
                result += unitsOf(c.netResult());
            }
        }
        if (liquidation != null) {
            result += unitsOf(liquidation.netResult());
        }
        // Unallocated losses remain at entity level; they are not partner liabilities.
        // Subtract them from the reconciliation difference so losses beyond capital are
        // represented without inventing negative partner equity or manager debt.
        long entityDeficit = 0;
        for (CloseSnapshot c : closes) {
            if (c.status() == CloseStatus.ACTIVE) {
                entityDeficit += unitsOf(c.unallocatedLoss());
            }
        }
        if (liquidation != null) {
            entityDeficit += unitsOf(liquidation.unallocatedLoss());
        }
        return new EquityReconciliation(units(equity, cur), units(cash, cur), units(result, cur),
                units(equity - cash - result - entityDeficit, cur));
    }

    // ------------------------------------------------------------------ integrity

    /** Verifies the movement hash chain and close hash chain; returns problems found (empty = intact). */
    public List<String> verifyIntegrity() {
        List<String> problems = new ArrayList<>();
        String prev = AuditHash.GENESIS;
        long seq = 0;
        for (PartnerMovement m : movements) {
            if (m.sequence() != ++seq) {
                problems.add("movement sequence gap at " + m.sequence());
            }
            if (!m.previousHash().equals(prev)) {
                problems.add("movement chain broken at " + m.sequence());
            }
            if (!m.hashIsValid()) {
                problems.add("movement hash mismatch at " + m.sequence());
            }
            prev = m.hash();
        }
        String prevClose = AuditHash.GENESIS;
        for (CloseSnapshot c : closes) {
            if (!c.previousHash().equals(prevClose)) {
                problems.add("close chain broken at " + c.sequence());
            }
            if (!c.hashIsValid()) {
                problems.add("close hash mismatch at " + c.sequence());
            }
            prevClose = c.hash();
        }
        if (liquidation != null) {
            String movementHead = movements.isEmpty() ? AuditHash.GENESIS
                    : movements.get(movements.size() - 1).hash();
            if (!liquidation.hashIsValid(movementHead)) {
                problems.add("liquidation hash mismatch");
            }
        }
        EquityReconciliation rec = reconcile();
        if (!rec.balanced()) {
            problems.add("equity reconciliation off by " + rec.difference().amount().toPlainString());
        }
        long[] total = new long[4];
        for (PartnerMovement m : movements) {
            long u = unitsOf(m.amount());
            total[0] += u * m.type().basisSign();
            total[1] += u * m.type().capitalSign();
            total[2] += u * m.type().payableSign();
            total[3] += u * m.type().liabilitySign();
        }
        long[] held = new long[4];
        buckets.values().forEach(b -> {
            for (int i = 0; i < 4 && b.length == 4; i++) {
                held[i] += b[i];
            }
        });
        for (int i = 0; i < 4; i++) {
            if (total[i] != held[i]) {
                problems.add("position cache disagrees with movements in bucket " + i);
            }
        }
        return problems;
    }

    // ------------------------------------------------------------------ internals

    private void addIfNonZero(PartnerId p, MovementType t, Money amount, CloseId closeId, String ref, Instant now) {
        if (!amount.isZero()) {
            append(p, t, amount, closeId, ref, now);
        }
    }

    private void append(PartnerId p, MovementType t, Money amount, CloseId closeId, String ref, Instant now) {
        long seq = movements.size() + 1L;
        String prev = movements.isEmpty() ? AuditHash.GENESIS : movements.get(movements.size() - 1).hash();
        PartnerMovement m = PartnerMovement.create(seq, p, t, amount, closeId, ref, now, prev);
        movements.add(m);
        long[] b = buckets.computeIfAbsent(p, k -> new long[4]);
        if (b.length != 4) {
            b = new long[4];
            buckets.put(p, b);
        }
        long u = unitsOf(amount);
        b[0] += u * t.basisSign();
        b[1] += u * t.capitalSign();
        b[2] += u * t.payableSign();
        b[3] += u * t.liabilitySign();
    }

    private long unitsOf(Money m) {
        return m.amount().setScale(scale, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    private Money norm(Money m) {
        if (!m.currency().equals(contract().currency())) {
            throw new BusinessRuleViolation("amount currency " + m.currency().code()
                    + " does not match akad currency " + contract().currency().code());
        }
        try {
            return Money.of(m.amount().setScale(scale, RoundingMode.UNNECESSARY), m.currency());
        } catch (ArithmeticException e) {
            throw new BusinessRuleViolation("amount " + m.amount() + " has more than " + scale + " decimals");
        }
    }

    private ManagerFault normalizeFault(ManagerFault fault) {
        ManagerFault f = fault == null ? ManagerFault.none() : fault;
        if (!f.negligentOrBreach()) {
            return f;
        }
        return ManagerFault.of(norm(f.attributableLoss()), f.reason());
    }

    private FinancialResult normalize(FinancialResult r) {
        return FinancialResult.of(norm(r.revenue()), norm(r.directCosts()), norm(r.operatingCosts()),
                norm(r.otherCosts()));
    }

    private CloseSnapshot lastActiveClose() {
        for (int i = closes.size() - 1; i >= 0; i--) {
            if (closes.get(i).status() == CloseStatus.ACTIVE) {
                return closes.get(i);
            }
        }
        return null;
    }

    private void replaceClose(CloseSnapshot updated) {
        for (int i = 0; i < closes.size(); i++) {
            if (closes.get(i).id().equals(updated.id())) {
                closes.set(i, updated);
                return;
            }
        }
    }

    private void requireActive() {
        if (status != SyirkahStatus.ACTIVE) {
            throw new InvalidStateException("syirkah is " + status);
        }
    }

    private void requireStatusNot(SyirkahStatus s) {
        if (status == s) {
            throw new InvalidStateException("syirkah is " + status);
        }
    }

    private void touch(Instant now) {
        setUpdatedAt(now);
    }

    public String partnerName(PartnerId id) {
        return partnerNames.get(id);
    }
}
