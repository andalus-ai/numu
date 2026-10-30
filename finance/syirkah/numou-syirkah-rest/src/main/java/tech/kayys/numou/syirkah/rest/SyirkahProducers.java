package tech.kayys.numou.numourest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.numouadapter.persistence.*;
import tech.kayys.numou.numouapplication.FormationEnforcement;
import tech.kayys.numou.numouapplication.StaffContractService;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.governance.GovernedSyirkahService;
import tech.kayys.numou.numouapplication.ops.SyirkahIntegrityAuditor;
import tech.kayys.numou.numouapplication.port.JournalPostingPort;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Wires the PostgreSQL adapters to the application services. Two beans are deliberately NOT provided here, so a
 * deployment that forgets them fails at start-up instead of silently running without a ledger: a
 * {@link JournalPostingPort} (the real general-ledger gateway) and the tenant's {@link SyirkahAccountMap}.
 */
@ApplicationScoped
public class SyirkahProducers {

    @Inject
    DataSource dataSource;
    @Inject
    JournalPostingPort journal;
    @Inject
    SyirkahAccountMap accounts;
    @Inject
    RequestContextHolder context;

    /** OFF (default) or REQUIRED; see {@link FormationEnforcement}. */
    @ConfigProperty(name = "numou.syirkah.formation-enforcement", defaultValue = "OFF")
    String formationEnforcement;

    @Produces
    @ApplicationScoped
    JdbcUnitOfWork unitOfWork() {
        return new JdbcUnitOfWork(dataSource);
    }

    @Produces
    @ApplicationScoped
    JdbcSyirkahRepository repository(JdbcUnitOfWork uow) {
        return new JdbcSyirkahRepository(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcApprovalPort approvals(JdbcUnitOfWork uow) {
        return new JdbcApprovalPort(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcOutboxEventPublisher events(JdbcUnitOfWork uow) {
        return new JdbcOutboxEventPublisher(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcAkadFormationPort akadFormations(JdbcUnitOfWork uow) {
        return new JdbcAkadFormationPort(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcLifecycleRecordPort lifecycleRecords(JdbcUnitOfWork uow) {
        return new JdbcLifecycleRecordPort(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcTradingRecordPort tradingRecords(JdbcUnitOfWork uow) {
        return new JdbcTradingRecordPort(uow);
    }

    @Produces
    @ApplicationScoped
    JdbcStaffRecordPort staffRecords(JdbcUnitOfWork uow) {
        return new JdbcStaffRecordPort(uow);
    }

    @Produces
    @ApplicationScoped
    StaffContractService staffService(JdbcSyirkahRepository repo, JdbcStaffRecordPort records, JdbcUnitOfWork uow) {
        return new StaffContractService(repo, records, uow, Clock.system(ZoneOffset.UTC));
    }

    @Produces
    @ApplicationScoped
    SyirkahApplicationService service(JdbcSyirkahRepository repo, JdbcOutboxEventPublisher events,
                                      JdbcUnitOfWork uow, JdbcAkadFormationPort formations,
                                      JdbcLifecycleRecordPort lifecycle) {
        return new SyirkahApplicationService(repo, journal, events, uow, accounts, Clock.system(ZoneOffset.UTC),
                FormationEnforcement.valueOf(formationEnforcement.trim().toUpperCase(java.util.Locale.ROOT)),
                formations, lifecycle);
    }

    @Produces
    @ApplicationScoped
    GovernedSyirkahService governed(SyirkahApplicationService service, JdbcApprovalPort approvals,
                                    JdbcUnitOfWork uow) {
        return new GovernedSyirkahService(service, approvals, uow, Clock.system(ZoneOffset.UTC));
    }

    @Produces
    @ApplicationScoped
    JdbcOperationsSupport operations(JdbcUnitOfWork uow) {
        return new JdbcOperationsSupport(uow);
    }

    @Produces
    @ApplicationScoped
    SyirkahIntegrityAuditor auditor(JdbcSyirkahRepository repo, JdbcOperationsSupport ops) {
        return new SyirkahIntegrityAuditor(repo, ops);
    }

    @Produces
    @ApplicationScoped
    SyirkahApi api(SyirkahApplicationService service, GovernedSyirkahService governed,
                   SyirkahIntegrityAuditor auditor, JdbcOperationsSupport ops, StaffContractService staff,
                   JdbcTradingRecordPort trading, JdbcUnitOfWork uow) {
        return new SyirkahApi(service, governed, auditor, ops, context, staff, trading, uow);
    }
}
