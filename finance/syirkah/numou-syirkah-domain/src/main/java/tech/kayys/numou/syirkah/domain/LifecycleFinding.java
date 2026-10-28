package tech.kayys.numou.numoudomain;

/** One reason a lifecycle change cannot go ahead yet. {@code source} points into Hukum Muamalah. */
public record LifecycleFinding(String code, String message, String source) {
    @Override
    public String toString() {
        return code + ": " + message + " (" + source + ")";
    }
}
