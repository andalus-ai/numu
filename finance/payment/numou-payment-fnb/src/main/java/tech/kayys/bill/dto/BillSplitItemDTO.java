package tech.kayys.bill.dto;

import java.math.BigDecimal;

public record BillSplitItemDTO(
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {}
