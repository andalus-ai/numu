package tech.kayys.numou.socialgraph.persistence.jdbc;

public final class SocialGraphOptimisticConflictException extends RuntimeException {
    public SocialGraphOptimisticConflictException(String message) { super(message); }
}
