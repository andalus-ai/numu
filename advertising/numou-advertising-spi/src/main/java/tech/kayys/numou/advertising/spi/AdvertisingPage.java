package tech.kayys.numou.advertising.spi;

import java.util.List;

/** Immutable page result with total count from the same tenant-scoped query contract. */
public record AdvertisingPage<T>(List<T> items, long totalElements, int page, int size) {
    public AdvertisingPage {
        items = List.copyOf(items);
        if (totalElements < 0) throw new IllegalArgumentException("totalElements cannot be negative");
        if (page < 0 || size < 1) throw new IllegalArgumentException("invalid page metadata");
    }

    public int totalPages() {
        if (totalElements == 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, ((totalElements - 1) / size) + 1);
    }

    public boolean hasNext() { return (long) page + 1 < totalPages(); }
    public boolean hasPrevious() { return page > 0 && totalElements > 0; }
}