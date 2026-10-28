package tech.kayys.numou.mobility.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.aggregate.persistence.PgDocumentRepository;
import tech.kayys.numou.mobility.driver.domain.Driver;
import tech.kayys.numou.mobility.driver.spi.DriverRepository;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.ride.domain.ActiveRideLock;
import tech.kayys.numou.mobility.ride.domain.Offer;
import tech.kayys.numou.mobility.ride.domain.RideChat;
import tech.kayys.numou.mobility.ride.spi.ChatRepository;
import tech.kayys.numou.wallet.domain.Payout;
import tech.kayys.numou.wallet.domain.PayoutId;
import tech.kayys.numou.wallet.domain.TopUpId;
import tech.kayys.numou.wallet.domain.TopUpIntent;
import tech.kayys.numou.wallet.spi.PaymentRepositories;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.spi.ActiveRideLockRepository;
import tech.kayys.numou.mobility.ride.spi.RideRepository;
import tech.kayys.numou.wallet.domain.Wallet;
import tech.kayys.numou.wallet.domain.WalletId;
import tech.kayys.numou.wallet.spi.WalletRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Document repositories for the mobility aggregates (table {@code aggregate_document}). For rides the generic index columns hold:
 * {@code venue_id} = driver with the live offer, {@code ref_id} = rider, {@code starts_at} = requested-at, {@code status} = status;
 * V300 adds partial indexes for exactly the queries below. NOT COMPILED in sandbox.
 */
public final class PgMobilityRepositories {

    private PgMobilityRepositories() { }

    @ApplicationScoped
    public static class Rides extends PgDocumentRepository<Ride, RideId, Ride.Snapshot> implements RideRepository {
        @Inject
        public Rides(ObjectMapper m) {
            super(m, "ride", Ride.Snapshot.class, Ride::snapshot, Ride::restore, r -> new Index(
                    r.liveOffer().map(Offer::driverId).orElse(null), r.riderId().value(), r.requestedAt(), r.status().name()));
        }

        @Override
        public Uni<List<Ride>> findSearching(UUID tenantId, int limit) {
            return query(tenantId, "status = 'SEARCHING'", "starts_at LIMIT " + Math.min(Math.max(limit, 1), 500));
        }

        @Override
        public Uni<List<Ride>> history(UUID tenantId, RiderId riderId, int limit) {
            return query(tenantId, "ref_id = ?3", "starts_at DESC LIMIT " + Math.min(Math.max(limit, 1), 100), riderId.value());
        }

        @Override
        public Uni<Optional<Ride>> findLiveOfferFor(UUID tenantId, UUID driverId) {
            return query(tenantId, "venue_id = ?3 AND status = 'SEARCHING'", "starts_at LIMIT 1", driverId).map(l -> l.stream().findFirst());
        }

        @Override
        public Uni<List<Ride>> findEndedUnsettled(UUID tenantId, int limit) {
            return query(tenantId, "status IN ('COMPLETED','CANCELLED','NO_DRIVERS') AND doc->>'settled' = 'false'",
                    "starts_at LIMIT " + Math.min(Math.max(limit, 1), 500));
        }
    }

    @ApplicationScoped
    public static class Locks extends PgDocumentRepository<ActiveRideLock, RiderId, ActiveRideLock.Snapshot> implements ActiveRideLockRepository {
        @Inject
        public Locks(ObjectMapper m) {
            super(m, "ride-lock", ActiveRideLock.Snapshot.class, ActiveRideLock::snapshot, ActiveRideLock::restore, l -> Index.NONE);
        }
    }

    @ApplicationScoped
    public static class Drivers extends PgDocumentRepository<Driver, DriverId, Driver.Snapshot> implements DriverRepository {
        @Inject
        public Drivers(ObjectMapper m) {
            super(m, "driver", Driver.Snapshot.class, Driver::snapshot, Driver::restore, d -> new Index(null, null, null, d.status().name()));
        }
    }

    @ApplicationScoped
    public static class Wallets extends PgDocumentRepository<Wallet, WalletId, Wallet.Snapshot> implements WalletRepository {
        @Inject
        public Wallets(ObjectMapper m) {
            super(m, "wallet", Wallet.Snapshot.class, Wallet::snapshot, Wallet::restore, w -> Index.NONE);
        }
    }

    @ApplicationScoped
    public static class Chats extends PgDocumentRepository<RideChat, RideId, RideChat.Snapshot> implements ChatRepository {
        @Inject
        public Chats(ObjectMapper m) {
            super(m, "ride-chat", RideChat.Snapshot.class, RideChat::snapshot, RideChat::restore, c -> Index.NONE);
        }
    }

    @ApplicationScoped
    public static class TopUps extends PgDocumentRepository<TopUpIntent, TopUpId, TopUpIntent.Snapshot> implements PaymentRepositories.TopUps {
        @Inject
        public TopUps(ObjectMapper m) {
            super(m, "topup", TopUpIntent.Snapshot.class, TopUpIntent::snapshot, TopUpIntent::restore,
                    t -> new Index(null, t.riderId(), t.getCreatedAt(), t.status().name()));
        }
    }

    @ApplicationScoped
    public static class Payouts extends PgDocumentRepository<Payout, PayoutId, Payout.Snapshot> implements PaymentRepositories.Payouts {
        @Inject
        public Payouts(ObjectMapper m) {
            super(m, "payout", Payout.Snapshot.class, Payout::snapshot, Payout::restore, p -> new Index(null, p.driverId(), p.getCreatedAt(), p.status().name()));
        }

        @Override
        public Uni<List<Payout>> findByStatus(UUID tenantId, Payout.Status status, int limit) {
            return query(tenantId, "status = ?3", "starts_at LIMIT " + Math.min(Math.max(limit, 1), 500), status.name());
        }
    }
}
