package tech.kayys.numou.billing.domain.plugin;

import tech.kayys.numou.billing.domain.valueobject.BillingContext;
import tech.kayys.numou.billing.domain.valueobject.BillingItem;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Map;

public record PricingContext(
    BillingContext context,
    List<BillingItem> items,
    Map<String, Object> parameters
) {}
