package tech.kayys.payment.gateway;

/**
 * Enumeration of supported payment gateway providers in Indonesia
 */
public enum GatewayProvider {
    // Major Indonesian Payment Gateways
    MIDTRANS("Midtrans", "Midtrans Payment Gateway", GatewayType.AGGREGATOR),
    XENDIT("Xendit", "Xendit Payment Gateway", GatewayType.AGGREGATOR),
    DOKU("Doku", "Doku Payment Gateway", GatewayType.AGGREGATOR),
    OY("OY! Indonesia", "OY! Indonesia Payment Gateway", GatewayType.AGGREGATOR),
    
    // Bank Direct Gateways
    BCA("BCA", "Bank Central Asia", GatewayType.BANK),
    MANDIRI("Mandiri", "Bank Mandiri", GatewayType.BANK),
    BNI("BNI", "Bank Negara Indonesia", GatewayType.BANK),
    BRI("BRI", "Bank Rakyat Indonesia", GatewayType.BANK),
    PERMATA("Permata", "Bank Permata", GatewayType.BANK),
    DANAMON("Danamon", "Bank Danamon", GatewayType.BANK),
    CIMB("CIMB Niaga", "CIMB Niaga", GatewayType.BANK),
    OCBC("OCBC NISP", "OCBC NISP", GatewayType.BANK),
    BSI("BSI", "Bank Syariah Indonesia", GatewayType.BANK),
    
    // E-Wallet Providers
    OVO("OVO", "OVO E-Wallet", GatewayType.E_WALLET),
    GOPAY("GoPay", "GoPay E-Wallet", GatewayType.E_WALLET),
    DANA("DANA", "DANA E-Wallet", GatewayType.E_WALLET),
    LINKAJA("LinkAja", "LinkAja E-Wallet", GatewayType.E_WALLET),
    SHOPEEPAY("ShopeePay", "ShopeePay E-Wallet", GatewayType.E_WALLET),
    
    // QRIS Providers
    QRIS_MIDTRANS("QRIS Midtrans", "QRIS via Midtrans", GatewayType.QRIS),
    QRIS_XENDIT("QRIS Xendit", "QRIS via Xendit", GatewayType.QRIS),
    QRIS_DOKU("QRIS Doku", "QRIS via Doku", GatewayType.QRIS),
    QRIS_BI("QRIS BI", "Bank Indonesia QRIS", GatewayType.QRIS),
    
    // Fintech/P2P Lending
    KREDIVO("Kredivo", "Kredivo Paylater", GatewayType.FINTECH),
    AKULAKU("Akulaku", "Akulaku Paylater", GatewayType.FINTECH),
    INDODANA("Indodana", "Indodana Paylater", GatewayType.FINTECH),
    
    // Card Processors
    VISA("Visa", "Visa Card Network", GatewayType.CARD),
    MASTERCARD("Mastercard", "Mastercard Network", GatewayType.CARD),
    JCB("JCB", "JCB Card Network", GatewayType.CARD),
    AMEX("American Express", "American Express", GatewayType.CARD),
    
    // Retail/OTC
    ALFAMART("Alfamart", "Alfamart Store Payment", GatewayType.RETAIL),
    INDOMARET("Indomaret", "Indomaret Store Payment", GatewayType.RETAIL);

    private final String shortName;
    private final String fullName;
    private final GatewayType type;

    GatewayProvider(String shortName, String fullName, GatewayType type) {
        this.shortName = shortName;
        this.fullName = fullName;
        this.type = type;
    }

    public String getShortName() {
        return shortName;
    }

    public String getFullName() {
        return fullName;
    }

    public GatewayType getType() {
        return type;
    }

    /**
     * Check if this is an aggregator gateway (supports multiple payment methods)
     */
    public boolean isAggregator() {
        return this.type == GatewayType.AGGREGATOR;
    }

    /**
     * Check if this is a direct bank gateway
     */
    public boolean isBank() {
        return this.type == GatewayType.BANK;
    }

    /**
     * Check if this is an e-wallet provider
     */
    public boolean isEWallet() {
        return this.type == GatewayType.E_WALLET;
    }

    /**
     * Check if this is a QRIS provider
     */
    public boolean isQRIS() {
        return this.type == GatewayType.QRIS;
    }

    /**
     * Check if this is a fintech/paylater provider
     */
    public boolean isFintech() {
        return this.type == GatewayType.FINTECH;
    }

    /**
     * Check if this is a card processor
     */
    public boolean isCard() {
        return this.type == GatewayType.CARD;
    }

    /**
     * Check if this is a retail/over-the-counter payment
     */
    public boolean isRetail() {
        return this.type == GatewayType.RETAIL;
    }
}
