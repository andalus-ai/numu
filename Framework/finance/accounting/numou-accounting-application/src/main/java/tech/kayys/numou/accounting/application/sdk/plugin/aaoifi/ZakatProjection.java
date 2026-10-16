package tech.kayys.syirkah.accounting.application.sdk.plugin.aaoifi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.projection.Projection;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;
import tech.kayys.syirkah.accounting.domain.event.MurabahaCreated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Murabahah sellingPrice totals per tenant for Zakat calculation.
 * Zakat base = 2.5% of Murabahah financing outstanding (AAOIFI FAS 9 simplified).
 */
public final class ZakatProjection implements Projection<AccountingEvent> {

    /** Key: tenantId string value. */
    private final Map<String, BigDecimal> totalByTenant = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> project(AccountingEvent event) {
        if (event instanceof MurabahaCreated mc) {
            totalByTenant.merge(
                    mc.tenantId().value(),
                    mc.sellingPrice(),
                    BigDecimal::add);
        }
        return Uni.createFrom().voidItem();
    }

    /** Returns the Zakat obligation (2.5%) for the given tenant id string. */
    public BigDecimal zakatObligation(String tenantId) {
        BigDecimal total = totalByTenant.getOrDefault(tenantId, BigDecimal.ZERO);
        return total.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);
    }

    /** Returns the total Murabahah financing portfolio for the given tenant id string. */
    public BigDecimal portfolioTotal(String tenantId) {
        return totalByTenant.getOrDefault(tenantId, BigDecimal.ZERO);
    }
}
