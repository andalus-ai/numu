package tech.kayys.numou.automotiveparts.purchasing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import io.quarkus.security.identity.SecurityIdentity;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.identity.application.port.EffectivePermissionPort;

import javax.sql.DataSource;

/** Host composition: transport and runtime dependencies stay outside the capability adapter. */
@ApplicationScoped
public class ReceiptPostingOperationsProducer {
    @Inject DataSource dataSource;
    @Inject EffectivePermissionPort effectivePermissions;

    @Produces
    @RequestScoped
    public IdentityReceiptPostingOperations receiptPostingOperations(
            RequestContextHolder requestContext, SecurityIdentity securityIdentity) {
        return new IdentityReceiptPostingOperations(
                new RequestContextReceiptPostingOperator(requestContext, securityIdentity),
                effectivePermissions,
                new JdbcReceiptPostingOperationsRepository(dataSource),
                new JdbcReceiptPostingRequeueAuditRepository(dataSource),
                new JdbcReceiptPostingLedger(dataSource));
    }
}
