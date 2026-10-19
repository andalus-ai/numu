package tech.kayys.bill.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record SplitDetail(
    String customerName,
    List<SplitItemDetail> items,
    BigDecimal amount,
    
    @NotNull(message = "Metode pembayaran tidak boleh kosong")
    String paymentMethod,
    
    @NotNull(message = "Jumlah pembayaran tidak boleh kosong")
    BigDecimal paid
) {}
