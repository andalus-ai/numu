package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class Fixtures {
    static final Currency IDR = Currency.of("IDR");
    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");
    static final PartnerId C = PartnerId.of("00000000-0000-0000-0000-00000000000c");

    private Fixtures() {
    }

    static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), IDR);
    }

    static ProfitSharingRatio ratio(Object... kv) {
        Map<PartnerId, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((PartnerId) kv[i], new BigDecimal(kv[i + 1].toString()));
        }
        return ProfitSharingRatio.ofPercent(m);
    }

    static SyirkahContract inan(long capA, long capB, ProfitSharingRatio nisbah) {
        return new SyirkahContract(1, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(capA)), CapitalContribution.cash(B, idr(capB))),
                nisbah, ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                LocalDate.parse("2026-01-01"));
    }

    static SyirkahContract mudharabah(long capital, ProfitSharingRatio nisbah) {
        return new SyirkahContract(1, SyirkahType.MUDHARABAH, IDR,
                List.of(new Partner(A, "Owner", PartnerRole.CAPITAL_PROVIDER, false),
                        new Partner(B, "Manager", PartnerRole.MANAGER, true)),
                List.of(CapitalContribution.cash(A, idr(capital))),
                nisbah, ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                LocalDate.parse("2026-01-01"));
    }

    static Syirkah open(SyirkahContract c) {
        return Syirkah.open(SyirkahId.generate(), TenantId.generate(), c, 0, NOW);
    }

    static FinancialResult result(long revenue, long direct, long operating, long other) {
        return FinancialResult.of(idr(revenue), idr(direct), idr(operating), idr(other));
    }

    static FinancialResult profit(long net) {
        return result(net, 0, 0, 0);
    }

    static FinancialResult loss(long net) {
        return result(0, net, 0, 0);
    }
}
