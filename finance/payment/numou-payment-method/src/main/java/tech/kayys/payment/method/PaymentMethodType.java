package tech.kayys.payment.method;

/**
 * Enumeration of supported payment methods in the Indonesian market
 */
public enum PaymentMethodType {
    // Bank Transfer Methods
    BANK_TRANSFER("Bank Transfer", "transfer"),
    VA_BCA("Virtual Account BCA", "va_bca"),
    VA_MANDIRI("Virtual Account Mandiri", "va_mandiri"),
    VA_BNI("Virtual Account BNI", "va_bni"),
    VA_BRI("Virtual Account BRI", "va_bri"),
    VA_PERMATA("Virtual Account Permata", "va_permata"),
    
    // QRIS Payment Methods
    QRIS("QRIS Standard", "qris"),
    QRIS_DANA("QRIS via DANA", "qris_dana"),
    QRIS_OVO("QRIS via OVO", "qris_ovo"),
    QRIS_GOPAY("QRIS via GoPay", "qris_gopay"),
    QRIS_LINKAJA("QRIS via LinkAja", "qris_linkaja"),
    
    // E-Wallet Methods
    E_WALLET("E-Wallet", "e_wallet"),
    OVO("OVO", "ovo"),
    GOPAY("GoPay", "gopay"),
    DANA("DANA", "dana"),
    LINKAJA("LinkAja", "linkaja"),
    SHOPEEPAY("ShopeePay", "shopeepay"),
    
    // Fintech & P2P Methods
    FINTECH("Fintech", "fintech"),
    KREDIVO("Kredivo", "kredivo"),
    AKULAKU("Akulaku", "akulaku"),
    INDODANA("Indodana", "indodana"),
    
    // Credit/Debit Card
    CREDIT_CARD("Credit Card", "credit_card"),
    DEBIT_CARD("Debit Card", "debit_card"),
    
    // Direct Debit
    DIRECT_DEBIT("Direct Debit", "direct_debit"),
    
    // Installment
    INSTALLMENT("Installment", "installment"),
    
    // Cash Payment
    ALFAMART("Alfamart", "alfamart"),
    INDOMARET("Indomaret", "indomaret");

    private final String displayName;
    private final String code;

    PaymentMethodType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    /**
     * Check if this payment method is a QRIS variant
     */
    public boolean isQRIS() {
        return this.name().startsWith("QRIS");
    }

    /**
     * Check if this payment method is a bank transfer variant
     */
    public boolean isBankTransfer() {
        return this.name().startsWith("VA_") || this == BANK_TRANSFER;
    }

    /**
     * Check if this payment method is an e-wallet
     */
    public boolean isEWallet() {
        return this == OVO || this == GOPAY || this == DANA || 
               this == LINKAJA || this == SHOPEEPAY || this == E_WALLET;
    }

    /**
     * Check if this payment method is a fintech/P2P lending
     */
    public boolean isFintech() {
        return this == KREDIVO || this == AKULAKU || this == INDODANA || this == FINTECH;
    }

    /**
     * Check if this payment method is a card payment
     */
    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }

    /**
     * Check if this payment method is over-the-counter cash payment
     */
    public boolean isCashPayment() {
        return this == ALFAMART || this == INDOMARET;
    }
}
