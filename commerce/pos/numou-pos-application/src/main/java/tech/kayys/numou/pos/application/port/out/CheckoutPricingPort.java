package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.pos.domain.Sale;

/**
 * Produces the payable checkout amount from a persisted sale snapshot.
 * Implementations own the pricing, discount, rounding and tax policy and must
 * not accept a caller-supplied total as an authority.
 */
@FunctionalInterface
public interface CheckoutPricingPort {
    Money quote(String tenantId, Sale sale);
}
