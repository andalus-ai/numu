package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;

import java.util.Set;

/**
 * What the reservation context needs to know about a table (supplied by the venue context through an
 * anti-corruption port, so reservation never depends on venue internals).
 *
 * @param combinableWith tables this one can be pushed together with (floor-plan adjacency)
 */
public record TableInfo(DiningTableId id, String label, int minCovers, int maxCovers, Set<DiningTableId> combinableWith) {

    public TableInfo {
        if (minCovers < 1 || maxCovers < minCovers) {
            throw new IllegalArgumentException("Table covers must satisfy 1 <= min <= max");
        }
        combinableWith = combinableWith == null ? Set.of() : Set.copyOf(combinableWith);
    }

    public boolean canJoin(TableInfo other) {
        return combinableWith.contains(other.id()) || other.combinableWith.contains(id);
    }
}
