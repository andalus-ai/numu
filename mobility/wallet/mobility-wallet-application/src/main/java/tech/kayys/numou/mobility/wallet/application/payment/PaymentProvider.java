package tech.kayys.numou.mobility.wallet.application.payment;

import java.time.Instant;
import java.util.Map;

/**
 * Verifies and parses one provider's payment callback. Never trusts the body until the signature has been checked, compares signatures in
 * constant time, and throws {@link InvalidSignatureException} for anything it cannot authenticate.
 */
public interface PaymentProvider {

    String id();

    /** @param lowerCaseHeaders header names lower-cased */
    PaymentNotification parse(Map<String, String> lowerCaseHeaders, String body, Instant now);
}
