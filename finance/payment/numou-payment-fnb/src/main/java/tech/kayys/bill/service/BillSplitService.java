package tech.kayys.bill.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import tech.kayys.bill.domain.BillSplit;
import tech.kayys.bill.domain.BillSplitItem;
import tech.kayys.bill.dto.BillSplitDTO;
import tech.kayys.bill.dto.BillSplitItemDTO;
import tech.kayys.bill.dto.SplitBillRequest;
import tech.kayys.bill.dto.SplitDetail;
import tech.kayys.bill.dto.SplitItemDetail;
import tech.kayys.bill.model.BillSplitStatus;
import tech.kayys.bill.model.BillSplitType;
import tech.kayys.payment.model.PaymentMethod;
import tech.kayys.sy.tenant.TenantContext;
import tech.kayys.transaction.domain.Transaction;
import tech.kayys.transaction.domain.TransactionItem;
import tech.kayys.transaction.maper.TransactionMapper;
import tech.kayys.transaction.model.TransactionStatus;
import tech.kayys.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@ApplicationScoped
public class BillSplitService {
    
    @Inject
    TransactionRepository transactionRepository;
    
    @Inject
    TransactionMapper transactionMapper;
    
    @Inject
    TenantContext tenantContext;
    
    private static final BigDecimal PPN_RATE = new BigDecimal("0.11");
    
    @Transactional
    public List<BillSplitDTO> splitBill(SplitBillRequest request) {
        Transaction transaction = transactionRepository.findByIdAndTenant(request.transactionId())
            .orElseThrow(() -> new NotFoundException("Transaksi tidak ditemukan"));
        
        if (transaction.status != TransactionStatus.COMPLETED && transaction.status != TransactionStatus.PENDING) {
            throw new BadRequestException("Hanya transaksi yang completed atau pending yang bisa di-split");
        }
        
        if (transaction.isSplit) {
            throw new BadRequestException("Transaksi sudah di-split sebelumnya");
        }
        
        BillSplitType splitType;
        try {
            splitType = BillSplitType.valueOf(request.splitType());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipe split tidak valid");
        }
        
        List<BillSplit> billSplits = new ArrayList<>();
        
        switch (splitType) {
            case EQUAL_SPLIT:
                billSplits = createEqualSplit(transaction, request.splits());
                break;
            case BY_ITEM:
                billSplits = createItemSplit(transaction, request.splits());
                break;
            case BY_AMOUNT:
                billSplits = createAmountSplit(transaction, request.splits());
                break;
            case CUSTOM:
                billSplits = createCustomSplit(transaction, request.splits());
                break;
        }
        
        transaction.billSplits.addAll(billSplits);
        transaction.isSplit = true;
        transaction.status = TransactionStatus.PENDING;
        transactionRepository.persist(transaction);
        
        return billSplits.stream()
            .map(this::mapToBillSplitDTO)
            .collect(Collectors.toList());
    }
    
    private List<BillSplit> createEqualSplit(Transaction transaction, List<SplitDetail> splits) {
        List<BillSplit> billSplits = new ArrayList<>();
        int splitCount = splits.size();
        
        BigDecimal splitSubtotal = transaction.subtotal.divide(new BigDecimal(splitCount), 2, RoundingMode.HALF_UP);
        BigDecimal splitPpn = transaction.ppnAmount.divide(new BigDecimal(splitCount), 2, RoundingMode.HALF_UP);
        BigDecimal splitService = transaction.serviceCharge.divide(new BigDecimal(splitCount), 2, RoundingMode.HALF_UP);
        BigDecimal splitDiscount = transaction.discount.divide(new BigDecimal(splitCount), 2, RoundingMode.HALF_UP);
        
        for (int i = 0; i < splitCount; i++) {
            SplitDetail detail = splits.get(i);
            BillSplit billSplit = new BillSplit();
            billSplit.tenantId = tenantContext.getTenantId();
            billSplit.transaction = transaction;
            billSplit.splitNumber = i + 1;
            billSplit.splitType = BillSplitType.EQUAL_SPLIT;
            billSplit.customerName = detail.customerName();
            
            billSplit.subtotal = splitSubtotal;
            billSplit.ppnAmount = splitPpn;
            billSplit.serviceCharge = splitService;
            billSplit.discount = splitDiscount;
            billSplit.total = splitSubtotal.add(splitPpn).add(splitService).subtract(splitDiscount);
            
            try {
                billSplit.paymentMethod = PaymentMethod.valueOf(detail.paymentMethod());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Metode pembayaran tidak valid");
            }
            
            billSplit.paid = detail.paid();
            
            if (billSplit.paymentMethod == PaymentMethod.CASH) {
                if (billSplit.paid.compareTo(billSplit.total) < 0) {
                    throw new BadRequestException("Pembayaran kurang dari total untuk split " + (i + 1));
                }
                billSplit.changeAmount = billSplit.paid.subtract(billSplit.total);
            } else {
                billSplit.changeAmount = BigDecimal.ZERO;
            }
            
            billSplit.status = BillSplitStatus.PAID;
            billSplit.paidAt = LocalDateTime.now();
            
            billSplits.add(billSplit);
        }
        
        return billSplits;
    }
    
    private List<BillSplit> createItemSplit(Transaction transaction, List<SplitDetail> splits) {
        List<BillSplit> billSplits = new ArrayList<>();
        
        for (int i = 0; i < splits.size(); i++) {
            SplitDetail detail = splits.get(i);
            
            if (detail.items() == null || detail.items().isEmpty()) {
                throw new BadRequestException("Items tidak boleh kosong untuk BY_ITEM split");
            }
            
            BillSplit billSplit = new BillSplit();
            billSplit.tenantId = tenantContext.getTenantId();
            billSplit.transaction = transaction;
            billSplit.splitNumber = i + 1;
            billSplit.splitType = BillSplitType.BY_ITEM;
            billSplit.customerName = detail.customerName();
            
            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal ppnAmount = BigDecimal.ZERO;
            
            for (SplitItemDetail itemDetail : detail.items()) {
                TransactionItem transactionItem = transaction.items.stream()
                    .filter(ti -> ti.id.equals(itemDetail.transactionItemId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Transaction item tidak ditemukan"));
                
                if (itemDetail.quantity() > transactionItem.quantity) {
                    throw new BadRequestException("Quantity melebihi jumlah item asli");
                }
                
                BillSplitItem splitItem = new BillSplitItem();
                splitItem.billSplit = billSplit;
                splitItem.transactionItem = transactionItem;
                splitItem.quantity = itemDetail.quantity();
                splitItem.unitPrice = transactionItem.unitPrice;
                splitItem.subtotal = transactionItem.unitPrice.multiply(new BigDecimal(itemDetail.quantity()));
                
                billSplit.items.add(splitItem);
                subtotal = subtotal.add(splitItem.subtotal);
                
                if (transactionItem.product.ppnApplicable) {
                    ppnAmount = ppnAmount.add(splitItem.subtotal.multiply(PPN_RATE));
                }
            }
            
            billSplit.subtotal = subtotal;
            billSplit.ppnAmount = ppnAmount.setScale(2, RoundingMode.HALF_UP);
            
            if (transaction.serviceChargePercentage != null) {
                billSplit.serviceCharge = subtotal.multiply(transaction.serviceChargePercentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            } else {
                billSplit.serviceCharge = BigDecimal.ZERO;
            }
            
            billSplit.discount = BigDecimal.ZERO;
            billSplit.total = billSplit.subtotal.add(billSplit.ppnAmount).add(billSplit.serviceCharge);
            
            try {
                billSplit.paymentMethod = PaymentMethod.valueOf(detail.paymentMethod());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Metode pembayaran tidak valid");
            }
            
            billSplit.paid = detail.paid();
            
            if (billSplit.paymentMethod == PaymentMethod.CASH) {
                if (billSplit.paid.compareTo(billSplit.total) < 0) {
                    throw new BadRequestException("Pembayaran kurang dari total");
                }
                billSplit.changeAmount = billSplit.paid.subtract(billSplit.total);
            } else {
                billSplit.changeAmount = BigDecimal.ZERO;
            }
            
            billSplit.status = BillSplitStatus.PAID;
            billSplit.paidAt = LocalDateTime.now();
            
            billSplits.add(billSplit);
        }
        
        return billSplits;
    }
    
    private List<BillSplit> createAmountSplit(Transaction transaction, List<SplitDetail> splits) {
        List<BillSplit> billSplits = new ArrayList<>();
        BigDecimal totalAmount = splits.stream()
            .map(SplitDetail::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalAmount.compareTo(transaction.total) != 0) {
            throw new BadRequestException("Total amount split harus sama dengan total transaksi");
        }
        
        for (int i = 0; i < splits.size(); i++) {
            SplitDetail detail = splits.get(i);
            
            BillSplit billSplit = new BillSplit();
            billSplit.tenantId = tenantContext.getTenantId();
            billSplit.transaction = transaction;
            billSplit.splitNumber = i + 1;
            billSplit.splitType = BillSplitType.BY_AMOUNT;
            billSplit.customerName = detail.customerName();
            
            BigDecimal splitRatio = detail.amount().divide(transaction.total, 4, RoundingMode.HALF_UP);
            
            billSplit.subtotal = transaction.subtotal.multiply(splitRatio).setScale(2, RoundingMode.HALF_UP);
            billSplit.ppnAmount = transaction.ppnAmount.multiply(splitRatio).setScale(2, RoundingMode.HALF_UP);
            billSplit.serviceCharge = transaction.serviceCharge.multiply(splitRatio).setScale(2, RoundingMode.HALF_UP);
            billSplit.discount = transaction.discount.multiply(splitRatio).setScale(2, RoundingMode.HALF_UP);
            billSplit.total = detail.amount();
            
            try {
                billSplit.paymentMethod = PaymentMethod.valueOf(detail.paymentMethod());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Metode pembayaran tidak valid");
            }
            
            billSplit.paid = detail.paid();
            
            if (billSplit.paymentMethod == PaymentMethod.CASH) {
                if (billSplit.paid.compareTo(billSplit.total) < 0) {
                    throw new BadRequestException("Pembayaran kurang dari total");
                }
                billSplit.changeAmount = billSplit.paid.subtract(billSplit.total);
            } else {
                billSplit.changeAmount = BigDecimal.ZERO;
            }
            
            billSplit.status = BillSplitStatus.PAID;
            billSplit.paidAt = LocalDateTime.now();
            
            billSplits.add(billSplit);
        }
        
        return billSplits;
    }
    
    private List<BillSplit> createCustomSplit(Transaction transaction, List<SplitDetail> splits) {
        return createItemSplit(transaction, splits);
    }
    
    public List<BillSplitDTO> getBillSplits(Long transactionId) {
        Transaction transaction = transactionRepository.findByIdAndTenant(transactionId)
            .orElseThrow(() -> new NotFoundException("Transaksi tidak ditemukan"));
        
        return transaction.billSplits.stream()
            .map(this::mapToBillSplitDTO)
            .collect(Collectors.toList());
    }
    
    private BillSplitDTO mapToBillSplitDTO(BillSplit billSplit) {
        List<BillSplitItemDTO> items = billSplit.items.stream()
            .map(item -> new BillSplitItemDTO(
                item.transactionItem.product.name,
                item.quantity,
                item.unitPrice,
                item.subtotal
            ))
            .collect(Collectors.toList());
        
        return new BillSplitDTO(
            billSplit.id,
            billSplit.splitNumber,
            billSplit.splitType.name(),
            items,
            billSplit.subtotal,
            billSplit.ppnAmount,
            billSplit.serviceCharge,
            billSplit.discount,
            billSplit.total,
            billSplit.paymentMethod != null ? billSplit.paymentMethod.getDisplayName() : null,
            billSplit.paid,
            billSplit.changeAmount,
            billSplit.status.name(),
            billSplit.customerName
        );
    }
}