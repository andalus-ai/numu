package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * One immutable version of the akad. Any change of nisbah, capital, or partners
 * produces a <em>new</em> version (after the books are closed) instead of mutating
 * this one, so every historical close can be traced to the exact terms in force.
 *
 * @param lossSharing explicit loss fractions; only allowed for ABDAN/WUJUH (no cash capital).
 *                    Empty means loss follows capital (the fiqh default).
 */
public record SyirkahContract(
        int version,
        SyirkahType type,
        Currency currency,
        List<Partner> partners,
        List<CapitalContribution> capital,
        ProfitSharingRatio nisbah,
        ProfitBasis profitBasis,
        Map<PartnerId, BigDecimal> lossSharing,
        Set<ForbiddenClause> declaredClauses,
        List<String> witnesses,
        boolean ijabQabulRecorded,
        LocalDate effectiveFrom) implements Serializable {

    public static final int MIN_WITNESSES = 2;

    public SyirkahContract {
        if (version < 1) {
            throw new IllegalArgumentException("contract version must be >= 1");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(nisbah, "nisbah");
        Objects.requireNonNull(profitBasis, "profitBasis");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        partners = List.copyOf(Objects.requireNonNull(partners, "partners"));
        capital = List.copyOf(capital == null ? List.of() : capital);
        lossSharing = lossSharing == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(lossSharing));
        declaredClauses = declaredClauses == null || declaredClauses.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(declaredClauses));
        witnesses = List.copyOf(witnesses == null ? List.of() : witnesses);
    }

    public Partner partner(PartnerId id) {
        return partners.stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown partner " + id));
    }

    public boolean hasPartner(PartnerId id) {
        return partners.stream().anyMatch(p -> p.id().equals(id));
    }

    public Set<PartnerId> partnerIds() {
        Set<PartnerId> ids = new HashSet<>();
        partners.forEach(p -> ids.add(p.id()));
        return ids;
    }

    /** Capital promised by a partner in this version (zero when none). */
    public Money capitalOf(PartnerId id) {
        Money total = Money.zero(currency);
        for (CapitalContribution c : capital) {
            if (c.partner().equals(id)) {
                total = total.add(c.amount());
            }
        }
        return total;
    }

    public Money totalCapital() {
        Money total = Money.zero(currency);
        for (CapitalContribution c : capital) {
            total = total.add(c.amount());
        }
        return total;
    }

    /** Loss weights when loss is not strictly by capital (abdan/wujuh); null means "by capital". */
    public Map<PartnerId, BigDecimal> explicitLossWeights() {
        return lossSharing.isEmpty() ? null : lossSharing;
    }

    /**
     * Canonical, unambiguous representation of every akad term that can affect
     * validity or accounting. Keep ordering deterministic so equivalent contract
     * instances hash identically regardless of collection iteration order.
     */
    public String canonical() {
        StringBuilder sb = new StringBuilder("syirkah-contract-v2|");
        appendCanonical(sb, Integer.toString(version));
        appendCanonical(sb, type.name());
        appendCanonical(sb, currency.code());
        appendCanonical(sb, profitBasis.name());
        appendCanonical(sb, effectiveFrom.toString());
        appendCanonical(sb, Boolean.toString(ijabQabulRecorded));
        appendCanonical(sb, nisbah.canonical());

        partners.stream().sorted(java.util.Comparator.comparing(p -> p.id().value())).forEach(p -> {
            appendCanonical(sb, "partner");
            appendCanonical(sb, p.id().value());
            appendCanonical(sb, p.name());
            appendCanonical(sb, p.role().name());
            appendCanonical(sb, Boolean.toString(p.contributesWork()));
        });

        capital.stream()
                .sorted(java.util.Comparator.comparing((CapitalContribution c) -> c.partner().value())
                        .thenComparing(c -> c.kind().name())
                        .thenComparing(c -> c.amount().amount())
                        .thenComparing(c -> c.valuationNote() == null ? "" : c.valuationNote()))
                .forEach(c -> {
                    appendCanonical(sb, "capital");
                    appendCanonical(sb, c.partner().value());
                    appendCanonical(sb, c.kind().name());
                    appendCanonical(sb, c.amount().currency().code());
                    appendCanonical(sb, c.amount().amount().stripTrailingZeros().toPlainString());
                    appendCanonical(sb, c.valuationNote() == null ? "" : c.valuationNote());
                });

        lossSharing.forEach((partner, share) -> {
            appendCanonical(sb, "loss-share");
            appendCanonical(sb, partner.value());
            appendCanonical(sb, share.stripTrailingZeros().toPlainString());
        });
        declaredClauses.stream().map(Enum::name).sorted().forEach(clause -> {
            appendCanonical(sb, "declared-clause");
            appendCanonical(sb, clause);
        });
        witnesses.stream().sorted().forEach(witness -> {
            appendCanonical(sb, "witness");
            appendCanonical(sb, witness);
        });
        return sb.toString();
    }

    /** Length-prefix fields so delimiters inside names/reasons cannot collide. */
    private static void appendCanonical(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append('|');
    }

    public List<ContractViolation> validate() {
        List<ContractViolation> v = new ArrayList<>();
        Set<PartnerId> ids = new HashSet<>();
        for (Partner p : partners) {
            if (!ids.add(p.id())) {
                v.add(new ContractViolation("PARTNER_DUPLICATE", "partner listed twice: " + p.id().value()));
            }
        }
        if (partners.size() < 2) {
            v.add(new ContractViolation("PARTNERS_MIN", "a syirkah needs at least two parties"));
        }
        if (profitBasis != ProfitBasis.NET_PROFIT) {
            v.add(new ContractViolation("PROFIT_ON_REVENUE",
                    "profit must be shared on net profit (revenue minus all costs), not on revenue"));
        }
        if (!nisbah.shares().keySet().equals(ids)) {
            v.add(new ContractViolation("NISBAH_PARTNERS",
                    "nisbah must name exactly the partners of the contract; every partner needs a share"));
        }
        for (ForbiddenClause c : declaredClauses) {
            v.add(new ContractViolation("FORBIDDEN_" + c.name(), "clause is invalid in syirkah: " + c));
        }
        if (witnesses.size() < MIN_WITNESSES || witnesses.stream().anyMatch(w -> w == null || w.isBlank())) {
            v.add(new ContractViolation("WITNESSES", "at least " + MIN_WITNESSES + " named witnesses are required"));
        }
        if (!ijabQabulRecorded) {
            v.add(new ContractViolation("IJAB_QABUL", "ijab and qabul of every partner must be recorded"));
        }
        Set<PartnerId> seenCapital = new HashSet<>();
        for (CapitalContribution c : capital) {
            if (!ids.contains(c.partner())) {
                v.add(new ContractViolation("CAPITAL_UNKNOWN_PARTNER", "capital from non-partner " + c.partner().value()));
            }
            if (!c.amount().currency().equals(currency)) {
                v.add(new ContractViolation("CAPITAL_CURRENCY", "capital must be in " + currency.code()));
            }
            if (c.kind() == CapitalKind.GOODS_VALUED_AT_AKAD
                    && (c.valuationNote() == null || c.valuationNote().isBlank())) {
                v.add(new ContractViolation("CAPITAL_VALUATION",
                        "goods capital must carry the agreed valuation at akad"));
            }
            seenCapital.add(c.partner());
        }
        validateByType(v, ids);
        validateLossSharing(v, ids);
        return v;
    }

    private void validateByType(List<ContractViolation> v, Set<PartnerId> ids) {
        Money total = totalCapital();
        switch (type) {
            case INAN -> {
                if (!total.isPositive()) {
                    v.add(new ContractViolation("CAPITAL_REQUIRED", "inan needs money capital"));
                }
                for (Partner p : partners) {
                    if (!capitalOf(p.id()).isPositive() && !p.contributesWork()) {
                        v.add(new ContractViolation("NO_CONTRIBUTION",
                                "partner " + p.name() + " contributes neither capital nor work"));
                    }
                }
            }
            case MUFAWADHAH -> {
                if (!total.isPositive()) {
                    v.add(new ContractViolation("CAPITAL_REQUIRED", "mufawadhah needs money capital"));
                }
                Money first = partners.isEmpty() ? total : capitalOf(partners.get(0).id());
                for (Partner p : partners) {
                    if (capitalOf(p.id()).compareTo(first) != 0) {
                        v.add(new ContractViolation("MUFAWADHAH_EQUAL_CAPITAL",
                                "mufawadhah requires equal capital from every partner"));
                        break;
                    }
                    if (!p.contributesWork()) {
                        v.add(new ContractViolation("MUFAWADHAH_EQUAL_WORK",
                                "mufawadhah requires every partner to work"));
                        break;
                    }
                }
            }
            case MUDHARABAH -> {
                long providers = partners.stream().filter(p -> p.role() == PartnerRole.CAPITAL_PROVIDER).count();
                long managers = partners.stream().filter(p -> p.role() == PartnerRole.MANAGER).count();
                if (providers < 1 || managers < 1) {
                    v.add(new ContractViolation("MUDHARABAH_ROLES",
                            "mudharabah needs at least one capital provider and one manager"));
                }
                if (!total.isPositive()) {
                    v.add(new ContractViolation("CAPITAL_REQUIRED", "mudharabah needs money capital from the provider"));
                }
                for (Partner p : partners) {
                    boolean hasCap = capitalOf(p.id()).isPositive();
                    if (p.role() == PartnerRole.MANAGER && hasCap) {
                        v.add(new ContractViolation("MUDHARABAH_MANAGER_CAPITAL",
                                "the mudharib contributes work only, not capital"));
                    }
                    if (p.role() == PartnerRole.MANAGER && !p.contributesWork()) {
                        v.add(new ContractViolation("MUDHARABAH_MANAGER_WORK", "the mudharib must work"));
                    }
                    if (p.role() == PartnerRole.CAPITAL_PROVIDER && !hasCap) {
                        v.add(new ContractViolation("MUDHARABAH_PROVIDER_CAPITAL",
                                "capital provider " + p.name() + " has no capital"));
                    }
                }
            }
            case ABDAN, WUJUH -> {
                if (total.isPositive()) {
                    v.add(new ContractViolation("NO_CASH_CAPITAL", type + " has no cash capital"));
                }
                if (type == SyirkahType.ABDAN) {
                    for (Partner p : partners) {
                        if (!p.contributesWork()) {
                            v.add(new ContractViolation("ABDAN_WORK", "every partner in abdan must work"));
                            break;
                        }
                    }
                }
            }
        }
        if (type != SyirkahType.MUDHARABAH) {
            for (Partner p : partners) {
                if (p.role() != PartnerRole.PARTNER) {
                    v.add(new ContractViolation("ROLE_MISMATCH", type + " partners must have role PARTNER"));
                    break;
                }
            }
        }
    }

    private void validateLossSharing(List<ContractViolation> v, Set<PartnerId> ids) {
        if (lossSharing.isEmpty()) {
            return;
        }
        if (type.requiresCashCapital()) {
            v.add(new ContractViolation("LOSS_NOT_BY_CAPITAL",
                    "loss must follow capital proportion in " + type + "; explicit loss sharing is not allowed"));
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<PartnerId, BigDecimal> e : lossSharing.entrySet()) {
            if (!ids.contains(e.getKey()) || e.getValue() == null || e.getValue().signum() < 0) {
                v.add(new ContractViolation("LOSS_SHARING_INVALID", "invalid loss share for " + e.getKey().value()));
                return;
            }
            sum = sum.add(e.getValue());
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            v.add(new ContractViolation("LOSS_SHARING_SUM", "loss shares must sum to 100%"));
        }
    }

    /** Throws {@link BusinessRuleViolation} listing every violation. */
    public SyirkahContract requireValid() {
        List<ContractViolation> violations = validate();
        if (!violations.isEmpty()) {
            throw new BusinessRuleViolation("Invalid syirkah contract: " + violations);
        }
        return this;
    }

    public Map<PartnerId, Money> capitalByPartner() {
        Map<PartnerId, Money> m = new LinkedHashMap<>();
        for (Partner p : partners) {
            m.put(p.id(), capitalOf(p.id()));
        }
        return m;
    }
}
