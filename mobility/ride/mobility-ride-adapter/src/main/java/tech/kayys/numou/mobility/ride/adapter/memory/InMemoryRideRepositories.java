package tech.kayys.numou.mobility.ride.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.kernel.domain.RiderId;
import tech.kayys.numou.mobility.ride.domain.ActiveRideLock;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.spi.ActiveRideLockRepository;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class InMemoryRideRepositories {

    private InMemoryRideRepositories() { }

    public static class Rides extends InMemoryDocumentRepository<Ride, RideId, Ride.Snapshot> implements RideRepository {
        public Rides() { super(Ride::snapshot, Ride::restore); }

        @Override
        public Uni<List<Ride>> findSearching(UUID tenantId, int limit) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(r -> r.status() == RideStatus.SEARCHING)
                    .sorted(Comparator.comparing(Ride::requestedAt)).limit(limit).toList());
        }

        @Override
        public Uni<List<Ride>> history(UUID tenantId, RiderId riderId, int limit) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(r -> r.riderId().equals(riderId))
                    .sorted(Comparator.comparing(Ride::requestedAt).reversed()).limit(limit).toList());
        }

        @Override
        public Uni<java.util.Optional<Ride>> findLiveOfferFor(UUID tenantId, UUID driverId) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream()
                    .filter(r -> r.status() == RideStatus.SEARCHING && r.liveOffer().filter(o -> o.driverId().equals(driverId)).isPresent())
                    .findFirst());
        }

        @Override
        public Uni<List<Ride>> findEndedUnsettled(UUID tenantId, int limit) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream()
                    .filter(r -> !r.settled() && (r.status() == RideStatus.COMPLETED || r.status() == RideStatus.CANCELLED || r.status() == RideStatus.NO_DRIVERS))
                    .sorted(Comparator.comparing(Ride::requestedAt)).limit(limit).toList());
        }
    }

    public static class Chats extends InMemoryDocumentRepository<tech.kayys.numou.mobility.ride.domain.RideChat, RideId, tech.kayys.numou.mobility.ride.domain.RideChat.Snapshot>
            implements tech.kayys.numou.mobility.ride.spi.ChatRepository {
        public Chats() { super(tech.kayys.numou.mobility.ride.domain.RideChat::snapshot, tech.kayys.numou.mobility.ride.domain.RideChat::restore); }
    }

    public static class Locks extends InMemoryDocumentRepository<ActiveRideLock, RiderId, ActiveRideLock.Snapshot> implements ActiveRideLockRepository {
        public Locks() { super(ActiveRideLock::snapshot, ActiveRideLock::restore); }
    }
}
