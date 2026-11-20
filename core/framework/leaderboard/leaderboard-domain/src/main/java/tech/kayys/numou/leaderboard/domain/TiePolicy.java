package tech.kayys.numou.leaderboard.domain;

/** Explicit tie semantics; never rely on database row ordering. */
public enum TiePolicy {
    /** 1, 1, 3 */
    COMPETITION,
    /** 1, 1, 2 */
    DENSE,
    /** Stable ordinal positions after deterministic participant-id ordering. */
    ORDINAL
}