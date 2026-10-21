package tech.kayys.numou.mobility.ride.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.domain.TracePoint;
import tech.kayys.numou.mobility.ride.spi.TripTraceRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryTripTraces implements TripTraceRepository {

    private record Key(UUID tenant, UUID ride) { }

    private final Map<Key, List<TracePoint>> traces = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> append(UUID tenantId, RideId rideId, List<TracePoint> points) {
        return Uni.createFrom().item(() -> {
            traces.computeIfAbsent(new Key(tenantId, rideId.value()), k -> new CopyOnWriteArrayList<>()).addAll(points);
            return null;
        });
    }

    @Override
    public Uni<List<TracePoint>> load(UUID tenantId, RideId rideId) {
        return Uni.createFrom().item(() -> traces.getOrDefault(new Key(tenantId, rideId.value()), List.of()).stream()
                .sorted(Comparator.comparing(TracePoint::time)).toList());
    }
}
