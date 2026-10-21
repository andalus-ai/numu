package tech.kayys.numou.mobility.ride.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.application.ConflictRetry;
import tech.kayys.numou.aggregate.domain.NotFoundException;
import tech.kayys.numou.aggregate.spi.DomainEventSink;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.mobility.kernel.domain.RideId;
import tech.kayys.numou.mobility.ride.domain.Ride;
import tech.kayys.numou.mobility.ride.domain.RideChat;
import tech.kayys.numou.mobility.ride.domain.RideStatus;
import tech.kayys.numou.mobility.ride.spi.ChatRepository;
import tech.kayys.numou.mobility.ride.spi.RideNotifications;
import tech.kayys.numou.mobility.ride.spi.RideRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * In-app chat between the rider and the driver of a ride. Only those two can read or write it (anyone else gets "not found"); it is open
 * from the moment a driver is assigned until 30 minutes after the ride ends (so "I left my bag" can be sorted out), and closed otherwise.
 */
public final class ChatService {

    public static final Duration AFTER_RIDE = Duration.ofMinutes(30);
    private static final int MAX_ATTEMPTS = 5;

    public record MessageView(String sender, String text, Instant at) { }

    private final RideRepository rides;
    private final ChatRepository chats;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final RideNotifications notify;

    public ChatService(RideRepository rides, ChatRepository chats, UnitOfWork uow, DomainEventSink events, DomainClock clock, RideNotifications notify) {
        this.rides = rides;
        this.chats = chats;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.notify = notify;
    }

    public Uni<MessageView> send(UUID tenantId, Actor actor, UUID rideId, String text) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> loadRide(tenantId, rideId).flatMap(ride -> {
            final RideChat.Sender sender = senderOf(ride, actor);
            requireOpen(ride);
            return chats.find(tenantId, ride.id()).map(o -> o.orElseGet(() -> RideChat.open(ride.id(), clock.now()))).flatMap(chat -> {
                final var m = chat.send(sender, text, clock.now());
                return chats.save(tenantId, chat).flatMap(x -> events.publish(tenantId, chat.pullDomainEvents()))
                        .replaceWith(new Object[]{m, ride, sender});
            });
        }))).flatMap(r -> {
            final var m = (RideChat.Message) r[0]; final var ride = (Ride) r[1]; final var sender = (RideChat.Sender) r[2];
            // tell the other party (best effort; the chat is also polled)
            final Uni<Void> push = sender == RideChat.Sender.RIDER
                    ? ride.driverId().map(d -> notify.rideUpdateToDriver(tenantId, d, ride.id().value(), "CHAT_MESSAGE")).orElse(Uni.createFrom().voidItem())
                    : notify.rideUpdateToRider(tenantId, ride.riderId().value(), ride.id().value(), "CHAT_MESSAGE");
            return push.onFailure().recoverWithItem((Void) null).replaceWith(new MessageView(m.sender().name(), m.text(), m.at()));
        });
    }

    /** Messages newer than {@code after} (or all when null), oldest first. */
    public Uni<List<MessageView>> history(UUID tenantId, Actor actor, UUID rideId, Instant after) {
        return loadRide(tenantId, rideId).flatMap(ride -> {
            senderOf(ride, actor);
            return chats.find(tenantId, ride.id()).map(o -> o.map(c -> c.messages().stream()
                    .filter(m -> after == null || m.at().isAfter(after)).map(m -> new MessageView(m.sender().name(), m.text(), m.at())).toList()).orElse(List.of()));
        });
    }

    private void requireOpen(Ride ride) {
        final var s = ride.status();
        final boolean live = s == RideStatus.DRIVER_ASSIGNED || s == RideStatus.DRIVER_ARRIVED || s == RideStatus.IN_PROGRESS;
        final boolean recentlyEnded = (s == RideStatus.COMPLETED || s == RideStatus.CANCELLED) && ride.driverId().isPresent()
                && ride.endedAt() != null && !clock.now().isAfter(ride.endedAt().plus(AFTER_RIDE));
        if (!live && !recentlyEnded) {
            throw new BusinessRuleViolation("Chat is not available for this ride right now");
        }
    }

    private static RideChat.Sender senderOf(Ride ride, Actor actor) {
        if (actor.role() == Actor.Role.RIDER && ride.riderId().value().equals(actor.id())) return RideChat.Sender.RIDER;
        if (actor.role() == Actor.Role.DRIVER && ride.driverId().filter(actor.id()::equals).isPresent()) return RideChat.Sender.DRIVER;
        throw new NotFoundException("Ride", ride.id().value());
    }

    private Uni<Ride> loadRide(UUID tenantId, UUID rideId) {
        return rides.find(tenantId, RideId.of(rideId)).map(o -> o.orElseThrow(() -> new NotFoundException("Ride", rideId)));
    }
}
