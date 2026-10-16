package tech.kayys.syirkah.accounting.application.hardening;

import tech.kayys.syirkah.accounting.domain.hardening.SoDViolationException;

import java.util.Objects;

/**
 * Segregation of Duties (Maker-Checker) rule enforcer.
 */
public final class SoDEnforcer {

    public static void verifySeparateUsers(String creatorUserId, String approverUserId, String operation) {
        Objects.requireNonNull(creatorUserId, "creatorUserId");
        Objects.requireNonNull(approverUserId, "approverUserId");

        if (creatorUserId.equalsIgnoreCase(approverUserId)) {
            throw new SoDViolationException(
                    "Segregation of Duties violation on " + operation + ": Creator and Approver cannot be the same user (" + creatorUserId + ")");
        }
    }
}
