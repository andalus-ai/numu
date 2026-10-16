package tech.kayys.numou.billing.saas;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.billing.domain.model.Invoice;
import tech.kayys.numou.billing.domain.plugin.*;
import tech.kayys.numou.billing.domain.valueobject.*;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class SaasBillingPlugin implements BillingPlugin {

    @Override
    public String getPluginId() {
        return "plugin-billing-saas";
    }

    @Override
    public Set<ProductType> getSupportedProductTypes() {
        return Set.of(
            ProductType.SAAS_SUBSCRIPTION,
            ProductType.SAAS_USAGE_BASED,
            ProductType.SAAS_TIERED
        );
    }

    @Override
    public boolean canHandle(BillingContext context) {
        return context != null && (
            context.getDomainTag() == DomainTag.SAAS ||
            context.getPartyType() == PartyType.TENANT
        );
    }

    @Override
    public CompletionStage<PriceCalculation> calculatePrice(PricingContext context) {
        String cur = context.context() != null ? context.context().getCurrencyCode() : "USD";
        Money subtotal = Money.zero(cur);

        for (BillingItem item : context.items()) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
            Money itemPrice = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);
            subtotal = subtotal.add(itemPrice.multiply(qty));
        }

        Money tax = Money.zero(cur);
        Money disc = Money.zero(cur);
        Money total = subtotal.add(tax).subtract(disc);

        return CompletableFuture.completedFuture(
            new PriceCalculation(subtotal, tax, disc, total, Map.of("plugin", getPluginId(), "domain", "SAAS"))
        );
    }

    @Override
    public CompletionStage<Invoice> enrichInvoice(Invoice invoice, BillingContext context) {
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + "Processed by SaaS Billing Extension for Tenant: " + context.getTenantId());
        return CompletableFuture.completedFuture(invoice);
    }
}
