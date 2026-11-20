package tech.kayys.numou.leaderboard.adapter.persistence.jdbc;

/** Unchecked boundary exception for failed durable score-ledger operations. */
public final class ScoreLedgerPersistenceException extends RuntimeException {
    public ScoreLedgerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
