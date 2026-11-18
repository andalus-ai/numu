package tech.kayys.numou.socialgraph.application;

public final class SocialConnectionNotFoundException extends RuntimeException {
    public SocialConnectionNotFoundException() { super("Social connection was not found in the current tenant"); }
}
