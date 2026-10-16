package tech.kayys.numou.billing.fnb;

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
public class FnbBillingPlugin implements BillingPlugin {

    private static final BigDecimal DEFAULT_SERVICE_CHARGE_RATE = new BigDecimal("0.05");

    @Override
    public String getPluginId() {
        return "plugin-billing-fnb";
    }

    @Override
    public Set<ProductType> getSupportedProductTypes() {
        return Set.of(
            ProductType.FNB_DINE_IN,
            ProductType.FNB_TAKEAWAY
        );
    }

    @Override
    public boolean canHandle(BillingContext context) {
        return context != null && (
            context.getDomainTag() == DomainTag.FNB ||
            context.getPartyType() == PartyType.GUEST
        );
    }

    @Override
    public CompletionStage<PriceCalculation> calculatePrice(PricingContext context) {
        String cur = context.context() != null ? context.context().getCurrencyCode() : "USD";
        Money subtotal = Money.zero(cur);

        for (BillingItem item : context.items()) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
            Money unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);
            subtotal = subtotal.add(unitPrice.multiply(qty));
        }

        Money serviceCharge = subtotal.multiply(DEFAULT_SERVICE_CHARGE_RATE);
        Money tax = Money.zero(cur);
        Money total = subtotal.add(serviceCharge).add(tax);

        return CompletableFuture.completedFuture(
            new PriceCalculation(
                subtotal, tax, Money.zero(cur), total,
                Map.of(
                    "plugin", getPluginId(),
                    "serviceCharge", serviceCharge.getAmount(),
                    "domain", "FNB"
                )
            )
        );
    }

    @Override
    public CompletionStage<Invoice> enrichInvoice(Invoice invoice, BillingContext context) {
        String tableId = context.getAttribute("tableId");
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + 
            "FnB Order - Table: " + (tableId != null ? tableId : "Takeaway"));
        return CompletableFuture.completedFuture(invoice);
    }
}
