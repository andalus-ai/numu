package tech.kayys.payment.processor.qris.config;

import java.math.BigDecimal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration mapping for QRIS payment processor.
 * 
 * @author Numou Platform
 */
@ConfigMapping(prefix = "payment.qris")
public interface QRISConfig {

    /**
     * Default acquirer bank for QRIS transactions
     */
    @WithName("default-acquirer")
    @WithDefault("MANDIRI")
    String getDefaultAcquirer();

    /**
     * Minimum transaction amount for QRIS payments
     */
    @WithName("min-amount")
    @WithDefault("1000")
    BigDecimal getMinAmount();

    /**
     * Maximum transaction amount for QRIS payments
     */
    @WithName("max-amount")
    @WithDefault("10000000")
    BigDecimal getMaxAmount();

    /**
     * Default expiry time in minutes for QRIS QR codes
     */
    @WithName("expiry-minutes")
    @WithDefault("1440")
    int getExpiryMinutes();

    /**
     * Enable dynamic QR code generation
     */
    @WithName("dynamic-qr")
    @WithDefault("true")
    boolean isDynamicQREnabled();

    /**
     * Enable static QR code support
     */
    @WithName("static-qr")
    @WithDefault("false")
    boolean isStaticQREnabled();

    /**
     * Default QR code image width in pixels
     */
    @WithName("qr-width")
    @WithDefault("300")
    int getQrWidth();

    /**
     * Default QR code image height in pixels
     */
    @WithName("qr-height")
    @WithDefault("300")
    int getQrHeight();

    /**
     * Enable QRIS validation
     */
    @WithName("validation-enabled")
    @WithDefault("true")
    boolean isValidationEnabled();

    /**
     * Supported QRIS channels (comma-separated)
     */
    @WithName("supported-channels")
    @WithDefault("GOPAY,OVO,DANA,LINKAJA,SHOPEEPAY")
    String getSupportedChannels();

    /**
     * Enable webhook notifications for QRIS payments
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
}
