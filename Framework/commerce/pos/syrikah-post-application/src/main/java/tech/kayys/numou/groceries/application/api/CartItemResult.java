package tech.kayys.syirkah.groceries.application.api;

public record CartItemResult(
        String cartId,
        String productId,
        String productType,
        Double weightValue,
        String weightUnit,
        Double unitPrice,
        Double totalPrice,
        boolean success
) {}
