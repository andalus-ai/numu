package tech.kayys.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.payment.model.PaymentMethod;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;

@ApplicationScoped
public class FeeCalculationService {
    
    @ConfigProperty(name = "fee.credit.card.percentage", defaultValue = "2.9")
    BigDecimal creditCardFeePercentage;
    
    @ConfigProperty(name = "fee.debit.card.percentage", defaultValue = "1.5")
    BigDecimal debitCardFeePercentage;
    
    @ConfigProperty(name = "fee.ewallet.flat", defaultValue = "2500")
    BigDecimal eWalletFlatFee;
    
    @ConfigProperty(name = "fee.virtual.account.flat", defaultValue = "4000")
    BigDecimal virtualAccountFlatFee;
    
    @ConfigProperty(name = "fee.convenience.store.flat", defaultValue = "2500")
    BigDecimal convenienceStoreFlatFee;
    
    public BigDecimal calculateFee(BigDecimal amount, PaymentMethod paymentMethod) {
        switch (paymentMethod) {
            case CREDIT_CARD:
                return amount.multiply(creditCardFeePercentage.divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
            
            case DEBIT_CARD:
                return amount.multiply(debitCardFeePercentage.divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
            
            case E_WALLET_GOPAY:
            case E_WALLET_OVO:
            case E_WALLET_DANA:
            case E_WALLET_LINKAJA:
            case E_WALLET_SHOPEEPAY:
                return eWalletFlatFee;
            
            case VIRTUAL_ACCOUNT_BCA:
            case VIRTUAL_ACCOUNT_BNI:
            case VIRTUAL_ACCOUNT_BRI:
            case VIRTUAL_ACCOUNT_MANDIRI:
                return virtualAccountFlatFee;
            
            case CONVENIENCE_STORE_INDOMARET:
            case CONVENIENCE_STORE_ALFAMART:
                return convenienceStoreFlatFee;
            
            case PAYLATER_KREDIVO:
            case PAYLATER_AKULAKU:
            case PAYLATER_INDODANA:
                return amount.multiply(new BigDecimal("3.5").divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
            
            case INSTALLMENT_BCA:
            case INSTALLMENT_MANDIRI:
            case INSTALLMENT_BNI:
                return amount.multiply(new BigDecimal("2.5").divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
            
            case QRIS:
                return amount.multiply(new BigDecimal("0.7").divide(new BigDecimal("100")))
                    .setScale(2, RoundingMode.HALF_UP);
            
            default:
                return BigDecimal.ZERO;
        }
    }
}
