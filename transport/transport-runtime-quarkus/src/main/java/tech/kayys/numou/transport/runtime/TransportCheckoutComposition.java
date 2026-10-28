package tech.kayys.numou.transport.runtime;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.transport.application.DepartureBookingService;
import tech.kayys.numou.transport.application.TransportCheckoutService;
import tech.kayys.numou.transport.postgres.PgTransportCheckoutRepository;
import tech.kayys.numou.transport.spi.TransportCheckoutRepository;
import tech.kayys.numou.transport.spi.TransportFareQuotePort;
import tech.kayys.numou.transport.spi.TransportPaymentPort;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Checkout is opt-in. Enabling it requires a trusted fare quote provider and the optional
 * PaymentApplicationPort adapter to be supplied by the host composition.
 */
@ApplicationScoped
@IfBuildProperty(name = "numou.transport.checkout.enabled", stringValue = "true")
public class TransportCheckoutComposition {
    @Inject
    DataSource dataSource;

    @Produces
    @ApplicationScoped
    @DefaultBean
    TransportCheckoutRepository checkoutRepository() {
        return new PgTransportCheckoutRepository(dataSource);
    }

    @Produces
    @ApplicationScoped
    TransportCheckoutService transportCheckoutService(
            DepartureBookingService bookings,
            TransportCheckoutRepository checkouts,
            TransportFareQuotePort fares,
            TransportPaymentPort payments,
            Clock clock,
            @ConfigProperty(name = "numou.transport.hold.max-duration-seconds", defaultValue = "900")
            long holdDurationSeconds) {
        return new TransportCheckoutService(bookings, checkouts, fares, payments, clock, holdDurationSeconds);
    }
}
