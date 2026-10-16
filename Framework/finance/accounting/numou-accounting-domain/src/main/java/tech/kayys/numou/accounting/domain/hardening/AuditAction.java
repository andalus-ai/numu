package tech.kayys.numou.accounting.domain.hardening;

/** Action types captured by the continuous audit trail. */
public enum AuditAction {
    CREATE, UPDATE, APPROVE, POST, REVERSE, DELETE, ACCESS
}
