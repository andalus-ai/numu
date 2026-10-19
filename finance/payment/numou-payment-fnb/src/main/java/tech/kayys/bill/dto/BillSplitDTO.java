package tech.kayys.bill.dto;


import java.math.BigDecimal;
import java.util.List;

public record BillSplitDTO(
    Long id,
    Integer splitNumber,
    String splitType,
    List<BillSplitItemDTO> items,
    BigDecimal subtotal,
    BigDecimal ppnAmount,
    BigDecimal serviceCharge,
    BigDecimal discount,
    BigDecimal total,
    String paymentMethod,
    BigDecimal paid,
    BigDecimal changeAmount,
    String status,
    String customerName
) {}
