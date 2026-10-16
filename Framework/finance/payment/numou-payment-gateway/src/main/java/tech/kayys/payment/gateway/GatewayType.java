package tech.kayys.payment.gateway;

/**
 * Type classification for payment gateway providers
 */
public enum GatewayType {
    /**
     * Aggregator gateways that support multiple payment methods
     * Examples: Midtrans, Xendit, Doku
     */
    AGGREGATOR("Payment Aggregator"),
    
    /**
     * Direct bank gateways
     * Examples: BCA, Mandiri, BNI
     */
    BANK("Bank Direct"),
    
    /**
     * E-wallet providers
     * Examples: OVO, GoPay, DANA
     */
    E_WALLET("E-Wallet"),
    
    /**
     * QRIS payment providers
     * Examples: QRIS via Midtrans, QRIS via Xendit
     */
    QRIS("QRIS Provider"),
    
    /**
     * Fintech/P2P lending platforms
     * Examples: Kredivo, Akulaku, Indodana
     */
    FINTECH("Fintech/P2P"),
    
    /**
     * Card processors
     * Examples: Visa, Mastercard, JCB
     */
    CARD("Card Processor"),
    
    /**
     * Retail/Over-the-counter payments
     * Examples: Alfamart, Indomaret
     */
    RETAIL("Retail Payment");

    private final String displayName;

    GatewayType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
