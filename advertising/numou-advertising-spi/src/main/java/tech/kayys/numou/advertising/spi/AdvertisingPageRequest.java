package tech.kayys.numou.advertising.spi;

/** Zero-based bounded page request. The persistence adapter must apply a stable order. */
public record AdvertisingPageRequest(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public AdvertisingPageRequest {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        try {
            Math.multiplyExact(page, size);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("page offset is too large", overflow);
        }
    }

    public int offset() { return Math.multiplyExact(page, size); }
    public static AdvertisingPageRequest firstPage() { return new AdvertisingPageRequest(0, DEFAULT_SIZE); }
}