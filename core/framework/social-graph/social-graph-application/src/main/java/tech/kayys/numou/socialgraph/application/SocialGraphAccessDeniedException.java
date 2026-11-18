package tech.kayys.numou.socialgraph.application;

/** Raised when the authenticated actor lacks tenant or capability access. */
public final class SocialGraphAccessDeniedException extends RuntimeException {
    public SocialGraphAccessDeniedException() {
        super("Actor is not authorized to access Social Graph in this tenant");
    }
}
