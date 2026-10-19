package tech.kayys.bill.dto;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SplitBillRequest(
    @NotNull(message = "ID transaksi tidak boleh kosong")
    Long transactionId,
    
    @NotNull(message = "Tipe split tidak boleh kosong")
    String splitType,
    
    @NotEmpty(message = "Split detail tidak boleh kosong")
    @Valid
    List<SplitDetail> splits
) {}