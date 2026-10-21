package tech.kayys.numou.mobility.api;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code numou.mobility.*}. Secrets come from the environment, never from this file. */
@ConfigMapping(prefix = "numou.mobility")
public interface MobilityConfig {

    /** Base64, at least 32 bytes decoded. Signs price quotes and derives pickup PINs. Rotate by deploying a new value (open quotes expire in 5 minutes). */
    String secret();

    /** Optional separate secret for pickup PINs (see MobilityComposition.Config). Defaults to {@link #secret()}; set it in production so quote-secret rotation is harmless. */
    Optional<String> pinSecret();

    ServiceArea area();

    @WithDefault("0.20")
    BigDecimal commission();

    @WithDefault("100000")
    BigDecimal driverCashDebtLimit();

    @WithDefault("50000")
    BigDecimal minPayout();

    /** Tenants whose searching rides the sweeper services. */
    List<UUID> sweepTenants();

    Routing routing();

    interface ServiceArea {
        @WithDefault("Jakarta") String name();
        @WithDefault("-6.2088") double lat();
        @WithDefault("106.8456") double lng();
        @WithDefault("40000") double radiusMeters();
    }

    interface Routing {
        /** OSRM base URL, e.g. http://osrm:5000. Unset = built-in straight-line estimator. */
        Optional<String> osrmUrl();

        @WithDefault("PT0.8S")
        Duration timeout();
    }
}
