package tech.kayys.payment.processor.bank.config;

import java.math.BigDecimal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration mapping for Bank Transfer payment processor.
 * 
 * @author Numou Platform
 */
@ConfigMapping(prefix = "payment.bank")
public interface BankTransferConfig {

    /**
     * Default bank for bank transfer transactions
     */
    @WithName("default-bank")
    @WithDefault("BCA")
    String getDefaultBank();

    /**
     * Minimum transaction amount for bank transfer payments
     */
    @WithName("min-amount")
    @WithDefault("1000")
    BigDecimal getMinAmount();

    /**
     * Maximum transaction amount for bank transfer payments
     */
    @WithName("max-amount")
    @WithDefault("50000000")
    BigDecimal getMaxAmount();

    /**
     * Default expiry time in minutes for virtual accounts
     */
    @WithName("expiry-minutes")
    @WithDefault("1440")
    int getExpiryMinutes();

    /**
     * Enable virtual account number generation
     */
    @WithName("va-enabled")
    @WithDefault("true")
    boolean isVAEnabled();

    /**
     * Enable standard bank transfer
     */
    @WithName("transfer-enabled")
    @WithDefault("true")
    boolean isTransferEnabled();

    /**
     * Enable bank transfer validation
     */
    @WithName("validation-enabled")
    @WithDefault("true")
    boolean isValidationEnabled();

    /**
     * Supported banks (comma-separated)
     */
    @WithName("supported-banks")
    @WithDefault("BCA,MANDIRI,BNI,BRI,PERMATA")
    String getSupportedBanks();

    /**
     * Enable webhook notifications for bank transfer payments
     */
    @WithName("webhook-enabled")
    @WithDefault("true")
    boolean isWebhookEnabled();

    /**
     * Webhook secret key for signature verification
     */
    @WithName("webhook-secret")
    @WithDefault("default-secret")
    String getWebhookSecret();

    /**
     * VA number prefix for BCA
     */
    @WithName("bca.va-prefix")
    @WithDefault("70013")
    String getBCAVAPrefix();

    /**
     * VA number prefix for Mandiri
     */
    @WithName("mandiri.va-prefix")
    @WithDefault("89008")
    String getMandiriVAPrefix();

    /**
     * VA number prefix for BNI
     */
    @WithName("bni.va-prefix")
    @WithDefault("8881")
    String getBNIVAPrefix();

    /**
     * VA number prefix for BRI
     */
    @WithName("bri.va-prefix")
    @WithDefault("20107")
    String getBRIVAPrefix();

    /**
     * VA number prefix for Permata
     */
    @WithName("permata.va-prefix")
    @WithDefault("90010")
    String getPermataVAPrefix();

    /**
     * Enable automatic status polling for pending payments
     */
    @WithName("auto-poll-enabled")
    @WithDefault("true")
    boolean isAutoPollEnabled();

    /**
     * Polling interval in seconds for pending payments
     */
    @WithName("poll-interval-seconds")
    @WithDefault("60")
    int getPollIntervalSeconds();
}
