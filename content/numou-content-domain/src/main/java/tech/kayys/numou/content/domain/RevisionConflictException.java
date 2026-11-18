package tech.kayys.numou.content.domain;
public final class RevisionConflictException extends RuntimeException {
 private final int expected; private final int actual;
 public RevisionConflictException(int expected,int actual){super("Revision conflict: expected "+expected+" but current revision is "+actual);this.expected=expected;this.actual=actual;}
 public int expected(){return expected;} public int actual(){return actual;}
}