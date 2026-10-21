package tech.kayys.syirkah.restaurant.kitchen.domain;

import tech.kayys.syirkah.restaurant.kernel.domain.StationId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Splits a fired course into one ticket per station and estimates when each will be ready.
 *
 * <p>Estimate: {@code fired + longest prep on the ticket + queue delay}, where the queue delay assumes the
 * station works {@code parallelism} items at once and, on average, is halfway through the current wave:
 * {@code maxPrep x floor(activeItems / parallelism) / 2}. Deliberately simple and explainable to a head chef;
 * the promise is what raises the "late" alarm, so it should be defensible rather than clever.
 */
public final class TicketRouter {

    /** @param activeItems items currently queued or being cooked at the station; @param parallelism items cooked at once */
    public record StationLoad(int activeItems, int parallelism) {
        public StationLoad {
            if (activeItems < 0 || parallelism < 1) {
                throw new IllegalArgumentException("activeItems >= 0 and parallelism >= 1 required");
            }
        }
    }

    /** One order line to be cooked, with the station that makes it. */
    public record RoutedLine(TicketItem item, StationId station) { }

    /** A ticket-to-be: the lines for one station and its promised time. */
    public record TicketDraft(StationId station, List<TicketItem> items, Instant promisedAt) { }

    private TicketRouter() { }

    public static List<TicketDraft> route(List<RoutedLine> lines, Instant firedAt, Function<StationId, StationLoad> loadOf) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nothing to route");
        }
        final var byStation = new LinkedHashMap<StationId, List<TicketItem>>();
        lines.forEach(l -> byStation.computeIfAbsent(l.station(), s -> new ArrayList<>()).add(l.item()));
        final var drafts = new ArrayList<TicketDraft>();
        for (final Map.Entry<StationId, List<TicketItem>> e : byStation.entrySet()) {
            drafts.add(new TicketDraft(e.getKey(), List.copyOf(e.getValue()), promise(e.getValue(), firedAt, loadOf.apply(e.getKey()))));
        }
        drafts.sort(Comparator.comparing((TicketDraft d) -> d.station().value()));
        return drafts;
    }

    static Instant promise(List<TicketItem> items, Instant firedAt, StationLoad load) {
        final Duration longest = items.stream().map(TicketItem::prepTime).max(Comparator.naturalOrder()).orElse(Duration.ZERO);
        final long waves = load.activeItems() / load.parallelism();
        return firedAt.plus(longest).plus(longest.multipliedBy(waves).dividedBy(2));
    }
}
