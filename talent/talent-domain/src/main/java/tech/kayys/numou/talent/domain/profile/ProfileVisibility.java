package tech.kayys.numou.talent.domain.profile;

/** Controls the intended audience; authorization is still enforced by the application boundary. */
public enum ProfileVisibility {
    PRIVATE,
    NETWORK,
    PUBLIC
}
