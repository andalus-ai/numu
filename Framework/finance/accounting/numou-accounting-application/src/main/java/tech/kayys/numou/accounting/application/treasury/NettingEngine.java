
package tech.kayys.syirkah.accounting.application.treasury;

import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multilateral netting engine. Resolves intercompany trade payables and receivables
 * into single net debtor/creditor settlements.
 */
public class NettingEngine {

    public record IntercompanyObligation(
            TenantRef debtor,
            TenantRef creditor,
            Money amount
    ) {}

    public record NettingSettlement(
            TenantRef entity,
            Money netAmount // positive = net receiver (creditor), negative = net payer (debtor)
    ) {}

    public List<NettingSettlement> calculateMultilateralNetting(List<IntercompanyObligation> obligations, String currencyCode) {
        Map<TenantRef, BigDecimal> balances = new HashMap<>();

        for (IntercompanyObligation ob : obligations) {
            // Debtor owes money (-), Creditor receives money (+)
            balances.merge(ob.debtor(), ob.amount().amount().negate(), BigDecimal::add);
            balances.merge(ob.creditor(), ob.amount().amount(), BigDecimal::add);
        }

        List<NettingSettlement> settlements = new ArrayList<>();
        balances.forEach((tenant, net) -> {
            settlements.add(new NettingSettlement(tenant, Money.of(net, currencyCode)));
        });

        return settlements;
    }
}
