package tech.kayys.numou.billing.retail;

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
public class RetailBillingPlugin implements BillingPlugin {

    @Override
    public String getPluginId() {
        return "plugin-billing-retail";
    }

    @Override
    public Set<ProductType> getSupportedProductTypes() {
        return Set.of(
            ProductType.RETAIL_POS,
            ProductType.RETAIL_LAYAWAY
        );
    }

    @Override
    public boolean canHandle(BillingContext context) {
        return context != null && context.getDomainTag() == DomainTag.RETAIL;
    }

    @Override
    public CompletionStage<PriceCalculation> calculatePrice(PricingContext context) {
        String cur = context.context() != null ? context.context().getCurrencyCode() : "USD";
        Money subtotal = Money.zero(cur);
        Money totalTax = Money.zero(cur);
        Money totalDisc = Money.zero(cur);

        for (BillingItem item : context.items()) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
            Money unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : Money.zero(cur);
            subtotal = subtotal.add(unitPrice.multiply(qty));
            totalTax = totalTax.add(item.getTaxAmount());
            totalDisc = totalDisc.add(item.getDiscountAmount());
        }

        Money total = subtotal.add(totalTax).subtract(totalDisc);
        return CompletableFuture.completedFuture(
            new PriceCalculation(subtotal, totalTax, totalDisc, total, Map.of("plugin", getPluginId(), "domain", "RETAIL"))
        );
    }

    @Override
    public CompletionStage<Invoice> enrichInvoice(Invoice invoice, BillingContext context) {
        String posTerminal = context.getAttribute("posTerminalId");
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + 
            "Retail POS Receipt - Terminal: " + (posTerminal != null ? posTerminal : "POS-01"));
        return CompletableFuture.completedFuture(invoice);
    }
}
