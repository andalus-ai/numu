package tech.kayys.syirkah.identity.application.port;

import tech.kayys.syirkah.identity.domain.valueobject.PasswordHash;

/**
 * Synchronous by design - hashing is CPU-bound, not I/O-bound, so
 * there's no reactive benefit to wrapping it in a Uni. An adapter
 * that needs to offload it onto a worker pool can do so internally
 * without changing this contract.
 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);

}
