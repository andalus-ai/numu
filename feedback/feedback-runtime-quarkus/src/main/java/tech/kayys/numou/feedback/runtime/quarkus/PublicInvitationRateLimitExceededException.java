package tech.kayys.numou.feedback.runtime.quarkus;

public final class PublicInvitationRateLimitExceededException extends RuntimeException {
    public PublicInvitationRateLimitExceededException() {
        super("Public invitation request limit reached");
    }
}