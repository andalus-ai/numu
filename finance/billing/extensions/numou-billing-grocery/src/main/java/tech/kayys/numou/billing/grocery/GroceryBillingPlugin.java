package tech.kayys.numou.billing.grocery;

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
public class GroceryBillingPlugin implements BillingPlugin {

    @Override
    public String getPluginId() {
        return "plugin-billing-grocery";
    }

    @Override
    public Set<ProductType> getSupportedProductTypes() {
        return Set.of(
            ProductType.GROCERY_WEIGHT_BASED,
            ProductType.GROCERY_BATCH
        );
    }

    @Override
    public boolean canHandle(BillingContext context) {
        return context != null && context.getDomainTag() == DomainTag.GROCERY;
    }

    @Override
    public CompletionStage<PriceCalculation> calculatePrice(PricingContext context) {
        String cur = context.context() != null ? context.context().getCurrencyCode() : "USD";
        Money subtotal = Money.zero(cur);
        Money totalDisc = Money.zero(cur);

        for (BillingItem item : context.items()) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
            Money unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);
            
            Money lineSub = unitPrice.multiply(qty);
            subtotal = subtotal.add(lineSub);

            if ("true".equalsIgnoreCase(item.getMetadata().get("nearExpiry"))) {
                Money expiryDiscount = lineSub.multiply(new BigDecimal("0.30"));
                totalDisc = totalDisc.add(expiryDiscount);
            }
        }

        Money total = subtotal.subtract(totalDisc);
        return CompletableFuture.completedFuture(
            new PriceCalculation(
                subtotal, Money.zero(cur), totalDisc, total,
                Map.of("plugin", getPluginId(), "domain", "GROCERY")
            )
        );
    }

    @Override
    public CompletionStage<Invoice> enrichInvoice(Invoice invoice, BillingContext context) {
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + "Grocery Supermarket Checkout");
        return CompletableFuture.completedFuture(invoice);
    }
}
