package tech.kayys.numou.automotiveparts.purchasing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.identity.application.port.EffectivePermissionPort;
import io.quarkus.security.identity.SecurityIdentity;

import javax.sql.DataSource;

/** Runtime composition for the HTTP adapter; persistence remains JDBC and request identity remains request-scoped. */
@ApplicationScoped
public class ReceiptPostingOperationsProducer {
    @Inject
    DataSource dataSource;

    @Inject
    EffectivePermissionPort effectivePermissions;

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
