package tech.kayys.numou.transport.runtime;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.transport.application.DepartureBookingService;
import tech.kayys.numou.transport.postgres.PgDepartureInventoryRepository;
import tech.kayys.numou.transport.spi.DepartureInventoryRepository;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Default runtime composition. Hosts can replace these default beans to use another repository
 * implementation or a controlled clock in tests without changing the application service.
 */
@ApplicationScoped
public class TransportRuntimeComposition {
    @Inject
    DataSource dataSource;

    @Produces
    @ApplicationScoped
    @DefaultBean
    DepartureInventoryRepository departureInventoryRepository() {
        return new PgDepartureInventoryRepository(dataSource);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    Clock transportClock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    DepartureBookingService departureBookingService(DepartureInventoryRepository inventories, Clock transportClock) {
        return new DepartureBookingService(inventories, transportClock);
    }
}
