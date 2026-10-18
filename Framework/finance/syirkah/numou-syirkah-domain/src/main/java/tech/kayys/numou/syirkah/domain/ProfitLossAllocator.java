package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure allocation engine for a closed period.
 *
 * <ul>
 *   <li>Profit: first repairs impaired capital (profit exists only above intact capital),
 *       the rest is split by nisbah with exact largest-remainder rounding.</li>
 *   <li>Loss: borne by capital proportion. In mudharabah the whole loss is on the capital
 *       provider unless the manager is found negligent/in breach; then the attributable
 *       amount is the manager's liability. ABDAN/WUJUH follow explicit loss sharing or nisbah.</li>
 * </ul>
 */
public final class ProfitLossAllocator {

    private ProfitLossAllocator() {
    }

    public static List<AllocationLine> allocate(SyirkahContract contract,
                                                Map<PartnerId, PartnerCapitalState> capital,
                                                Money netResult,
                                                ManagerFault fault,
                                                ProfitDisposition disposition,
                                                int scale) {
        Currency cur = contract.currency();
        if (!netResult.currency().equals(cur)) {
            throw new BusinessRuleViolation("result currency differs from contract currency");
        }
        Money zero = Money.zero(cur);
        Map<PartnerId, Money> restored = new LinkedHashMap<>();
        Map<PartnerId, Money> profit = new LinkedHashMap<>();
        Map<PartnerId, Money> loss = new LinkedHashMap<>();
        Map<PartnerId, Money> liability = new LinkedHashMap<>();
        for (Partner p : contract.partners()) {
            restored.put(p.id(), zero);
            profit.put(p.id(), zero);
            loss.put(p.id(), zero);
            liability.put(p.id(), zero);
        }

        if (netResult.isPositive()) {
            allocateProfit(contract, capital, netResult, restored, profit, scale);
        } else if (netResult.isNegative()) {
            allocateLoss(contract, capital, netResult.negate(), fault == null ? ManagerFault.none() : fault,
                    loss, liability, scale);
        }

        List<AllocationLine> lines = new ArrayList<>();
        for (Partner p : contract.partners()) {
            Money ps = profit.get(p.id());
            Money retained = disposition == ProfitDisposition.RETAIN ? ps : zero;
            lines.add(new AllocationLine(p.id(), restored.get(p.id()), ps, loss.get(p.id()),
                    liability.get(p.id()), retained));
        }
        return lines;
    }

    private static void allocateProfit(SyirkahContract contract, Map<PartnerId, PartnerCapitalState> capital,
                                       Money net, Map<PartnerId, Money> restored,
                                       Map<PartnerId, Money> profit, int scale) {
        Currency cur = contract.currency();
        Map<PartnerId, BigDecimal> impairment = new TreeMap<>();
        BigDecimal totalImpairment = BigDecimal.ZERO;
        for (Partner p : contract.partners()) {
            PartnerCapitalState st = capital.get(p.id());
            BigDecimal imp = st == null ? BigDecimal.ZERO : st.impairment().amount();
            if (imp.signum() > 0) {
                impairment.put(p.id(), imp);
                totalImpairment = totalImpairment.add(imp);
            }
        }
        BigDecimal restoreTotal = net.amount().min(totalImpairment);
        if (restoreTotal.signum() > 0) {
            Map<PartnerId, BigDecimal> parts = Apportionment.apportion(restoreTotal, impairment, scale);
            // never restore a partner beyond their own impairment
            parts.forEach((k, v) -> restored.put(k, Money.of(v.min(impairment.get(k)), cur)));
            BigDecimal actual = BigDecimal.ZERO;
            for (Money m : restored.values()) {
                actual = actual.add(m.amount());
            }
            restoreTotal = actual;
        }
        BigDecimal distributable = net.amount().subtract(restoreTotal);
        if (distributable.signum() > 0) {
            Map<PartnerId, BigDecimal> parts = Apportionment.apportion(
                    distributable, contract.nisbah().shares(), scale);
            parts.forEach((k, v) -> profit.put(k, Money.of(v, cur)));
        }
    }

    private static void allocateLoss(SyirkahContract contract, Map<PartnerId, PartnerCapitalState> capital,
                                     Money lossAmount, ManagerFault fault,
                                     Map<PartnerId, Money> loss, Map<PartnerId, Money> liability, int scale) {
        Currency cur = contract.currency();
        BigDecimal remaining = lossAmount.amount();

        if (contract.type() == SyirkahType.MUDHARABAH && fault.negligentOrBreach()) {
            BigDecimal onManager = fault.attributableLoss().amount().min(remaining);
            if (onManager.signum() > 0) {
                List<PartnerId> managers = new ArrayList<>();
                for (Partner p : contract.partners()) {
                    if (p.role() == PartnerRole.MANAGER) {
                        managers.add(p.id());
                    }
                }
                Map<PartnerId, BigDecimal> w = new TreeMap<>();
                BigDecimal totalNisbah = BigDecimal.ZERO;
                for (PartnerId m : managers) {
                    BigDecimal s = contract.nisbah().shareOf(m);
                    w.put(m, s);
                    totalNisbah = totalNisbah.add(s);
                }
                if (totalNisbah.signum() == 0) {
                    managers.forEach(m -> w.put(m, BigDecimal.ONE));
                }
                Apportionment.apportion(onManager, w, scale).forEach((k, v) -> liability.put(k, Money.of(v, cur)));
                remaining = remaining.subtract(onManager);
            }
        }
        if (remaining.signum() == 0) {
            return;
        }

        Map<PartnerId, BigDecimal> weights = new TreeMap<>();
        if (contract.explicitLossWeights() != null) {
            // Abdan/wujuh have no cash capital; their agreed loss weights apply.
            weights.putAll(contract.explicitLossWeights());
        } else {
            BigDecimal availableCapital = BigDecimal.ZERO;
            for (Partner p : contract.partners()) {
                PartnerCapitalState st = capital.get(p.id());
                BigDecimal available = st == null ? BigDecimal.ZERO : st.balance().amount().max(BigDecimal.ZERO);
                boolean bearsLoss = contract.type() != SyirkahType.MUDHARABAH
                        || p.role() == PartnerRole.CAPITAL_PROVIDER;
                BigDecimal weight = bearsLoss ? available : BigDecimal.ZERO;
                weights.put(p.id(), weight);
                availableCapital = availableCapital.add(weight);
            }
            // Allocate only the loss that eligible capital can absorb. Any remainder is an
            // entity-level deficit, not a partner debt or negative partner equity.
            remaining = remaining.min(availableCapital);
            if (remaining.signum() == 0) {
                return;
            }
        }
        Apportionment.apportion(remaining, weights, scale).forEach((k, v) -> loss.put(k, Money.of(v, cur)));
    }
}
