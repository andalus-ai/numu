package tech.kayys.numou.mobility.ride.application;

import java.math.BigDecimal;
import java.time.Instant;

/** One bookable option shown to the rider. {@code token} must be sent back to book at exactly this price. */
public record QuoteView(String token, String vehicle, BigDecimal total, BigDecimal subtotal, BigDecimal bookingFee, BigDecimal discount,
                        BigDecimal surgeMultiplier, double distanceMeters, long durationSeconds, long pickupEtaSeconds, String currency,
                        Instant expiresAt, String promoStatus) { }
