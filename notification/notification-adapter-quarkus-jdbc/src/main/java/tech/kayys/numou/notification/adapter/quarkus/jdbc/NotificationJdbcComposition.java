package tech.kayys.numou.notification.adapter.quarkus.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.application.port.PushTokenProtector;
import tech.kayys.numou.notification.persistence.jdbc.AesGcmPushTokenProtector;
import tech.kayys.numou.notification.persistence.jdbc.JdbcPushDeviceRepository;
import tech.kayys.numou.notification.persistence.jdbc.PushTokenKeyProvider;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Production CDI composition for encrypted push-device persistence.
 *
 * <p>The host owns the DataSource and key lifecycle. In particular, this
 * adapter intentionally has no default or development key provider.
 */
@ApplicationScoped
public class NotificationJdbcComposition {
    @Produces
    @ApplicationScoped
    public PushTokenProtector pushTokenProtector(PushTokenKeyProvider keyProvider) {
        return new AesGcmPushTokenProtector(
                Objects.requireNonNull(keyProvider, "host PushTokenKeyProvider is required"));
    }

    @Produces
    @ApplicationScoped
    public PushDeviceRepository pushDeviceRepository(
            DataSource dataSource,
            PushTokenProtector tokenProtector) {
        return new JdbcPushDeviceRepository(
                Objects.requireNonNull(dataSource, "host DataSource is required"),
                Objects.requireNonNull(tokenProtector, "PushTokenProtector is required"));
    }
}
