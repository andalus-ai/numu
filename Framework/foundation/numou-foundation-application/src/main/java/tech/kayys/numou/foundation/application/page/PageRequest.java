package tech.kayys.numou.foundation.application.page;

/**
 * Zero-based page request for a query.
 */
public record PageRequest(int page, int size) {

    private static final int MAX_SIZE = 200;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        if (size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size cannot exceed " + MAX_SIZE
            );
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }

    public static PageRequest first(int size) {
        return new PageRequest(0, size);
    }

    public long offset() {
        return (long) page * size;
    }

}
