package tech.kayys.numou.foundation.application.page;

import java.util.List;
import java.util.Objects;

/**
 * A page of query results plus enough metadata to paginate further.
 *
 * Generic and technical, not tied to any single read model - this is
 * the kind of primitive that genuinely belongs in a shared foundation
 * (unlike, say, "Order" or "Product").
 *
 * @param <T> the content item type
 */
public record Page<T>(
        List<T> content,
        long totalElements,
        int page,
        int size
) {

    public Page {
        Objects.requireNonNull(content, "content cannot be null");

        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements cannot be negative");
        }

        content = List.copyOf(content);
    }

    public static <T> Page<T> of(
            List<T> content,
            long totalElements,
            PageRequest request
    ) {
        Objects.requireNonNull(request, "request cannot be null");

        return new Page<>(
                content,
                totalElements,
                request.page(),
                request.size()
        );
    }

    public static <T> Page<T> empty(PageRequest request) {
        return of(List.of(), 0L, request);
    }

    public int totalPages() {
        return size == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

}
