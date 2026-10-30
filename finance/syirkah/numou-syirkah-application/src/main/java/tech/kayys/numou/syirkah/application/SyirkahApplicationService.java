package tech.kayys.numou.numouapplication;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numouapplication.port.AkadFormationPort;
import tech.kayys.numou.numouapplication.port.EventPublisherPort;
import tech.kayys.numou.numouapplication.port.JournalPostingPort;
import tech.kayys.numou.numouapplication.port.LifecycleRecordPort;
import tech.kayys.numou.numouapplication.port.SyirkahRepository;
import tech.kayys.numou.numouapplication.port.TransactionPort;
import tech.kayys.numou.numoudomain.AkadFormation;
import tech.kayys.numou.numoudomain.AkadFormationChecker;
import tech.kayys.numou.numoudomain.FormationPolicy;
import tech.kayys.numou.numoudomain.SyirkahNotFoundException;
import tech.kayys.numou.numoudomain.CloseId;
import tech.kayys.numou.numoudomain.CloseSnapshot;
import tech.kayys.numou.numoudomain.LifecycleChange;
import tech.kayys.numou.numoudomain.LifecycleChecker;
import tech.kayys.numou.numoudomain.LifecycleKind;
import tech.kayys.numou.numoudomain.PartnerId;
import tech.kayys.numou.numoudomain.LiquidationResult;
import tech.kayys.numou.numoudomain.PartnerMovement;
import tech.kayys.numou.numoudomain.Syirkah;
import tech.kayys.numou.numoudomain.SyirkahId;
import tech.kayys.numou.numoudomain.SyirkahPostingPlan;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Use cases. Each command runs in one transaction: load -> mutate aggregate -> save with optimistic
 * version -> post journals (idempotent per movement) -> publish events (outbox).
 */
public class SyirkahApplicationService {

    private final SyirkahRepository repository;
    private final JournalPostingPort journal;
    private final EventPublisherPort events;
    private final TransactionPort tx;
    private final SyirkahAccountMap accounts;
    private final Clock clock;
    private final FormationEnforcement enforcement;
    private final AkadFormationPort formations;
    private final LifecycleRecordPort lifecycle;

    public SyirkahApplicationService(SyirkahRepository repository, JournalPostingPort journal,
                                     EventPublisherPort events, TransactionPort tx, SyirkahAccountMap accounts,
                                     Clock clock) {
        this(repository, journal, events, tx, accounts, clock, FormationEnforcement.OFF, null);
    }

    public SyirkahApplicationService(SyirkahRepository repository, JournalPostingPort journal,
                                     EventPublisherPort events, TransactionPort tx, SyirkahAccountMap accounts,
                                     Clock clock, FormationEnforcement enforcement) {
        this(repository, journal, events, tx, accounts, clock, enforcement, null);
    }

    /**
     * @param formations where the akad formation record is kept when a syirkah is opened with
     *                   {@link #openWithFormation}; null keeps no record (the checks still run)
     */
    public SyirkahApplicationService(SyirkahRepository repository, JournalPostingPort journal,
                                     EventPublisherPort events, TransactionPort tx, SyirkahAccountMap accounts,
                                     Clock clock, FormationEnforcement enforcement, AkadFormationPort formations) {
        this(repository, journal, events, tx, accounts, clock, enforcement, formations, null);
    }

    /**
     * @param lifecycle where the records behind {@link #reviseWithLifecycle} are kept; null keeps none (the checks
     *                  still run)
     */
    public SyirkahApplicationService(SyirkahRepository repository, JournalPostingPort journal,
                                     EventPublisherPort events, TransactionPort tx, SyirkahAccountMap accounts,
                                     Clock clock, FormationEnforcement enforcement, AkadFormationPort formations,
                                     LifecycleRecordPort lifecycle) {
        this.repository = Objects.requireNonNull(repository);
        this.journal = Objects.requireNonNull(journal);
        this.events = Objects.requireNonNull(events);
        this.tx = Objects.requireNonNull(tx);
        this.accounts = Objects.requireNonNull(accounts);
        this.clock = Objects.requireNonNull(clock);
        this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
        this.formations = formations;
        this.lifecycle = lifecycle;
    }

    public SyirkahApplicationService(SyirkahRepository repository, JournalPostingPort journal,
                                     EventPublisherPort events, TransactionPort tx, SyirkahAccountMap accounts) {
        this(repository, journal, events, tx, accounts, Clock.system(ZoneOffset.UTC));
    }

    public Syirkah open(Commands.Open c) {
        if (enforcement == FormationEnforcement.REQUIRED) {
            throw new BusinessRuleViolation(
                    "akad formation checks are required; open the syirkah with openWithFormation");
        }
        return openChecked(c);
    }

    private Syirkah openChecked(Commands.Open c) {
        return tx.inTransaction(() -> {
            if (repository.find(c.tenant(), c.id()).isPresent()) {
                throw new BusinessRuleViolation("syirkah already exists: " + c.id().value());
            }
            Syirkah s = Syirkah.open(c.id(), c.tenant(), c.contract(), c.amountScale(), now());
            finish(c.tenant(), s, -1, 0);
            return s;
        });
    }

    /**
     * Opens a syirkah only if the akad was validly formed and properly written (Hukum Muamalah Bab X, XI, XVIII;
     * see {@link AkadFormationChecker}). Nothing is created when any finding exists.
     *
     * <p>{@link #open(Commands.Open)} does not run these checks unless the service is built with
     * {@link FormationEnforcement#REQUIRED}, in which case it refuses and this method is the only way in
     * (roadmap gate B). The default stays {@code OFF} so existing callers are not broken.
     */
    public Syirkah openWithFormation(Commands.Open c, AkadFormation formation, FormationPolicy policy) {
        AkadFormationChecker.requirePassing(c.contract(), formation, policy);
        return tx.inTransaction(() -> {
            Syirkah s = openChecked(c);
            if (formations != null) {
                formations.record(c.tenant(), c.id(), formation, policy, now());
            }
            return s;
        });
    }

    public CloseSnapshot closePeriod(Commands.ClosePeriod c) {
        return mutate(c.tenant(), c.id(), s -> s.closePeriod(c.closeId(), c.start(), c.end(), c.result(),
                c.fault(), c.disposition(), now()));
    }

    public void distributeProfit(Commands.DistributeProfit c) {
        mutate(c.tenant(), c.id(), s -> {
            s.distributeProfit(c.partner(), c.amount(), c.reference(), now());
            return null;
        });
    }

    public void settleLiability(Commands.SettleLiability c) {
        mutate(c.tenant(), c.id(), s -> {
            s.settleLiability(c.partner(), c.amount(), c.reference(), now());
            return null;
        });
    }

    public void reviseContract(Commands.ReviseContract c) {
        mutate(c.tenant(), c.id(), s -> {
            s.reviseContract(c.next(), c.approvals(), now());
            return null;
        });
    }

    /**
     * Revises the akad for a new-capital, partner-replacement or partner-death event. Runs
     * {@link LifecycleChecker} first (recorded consents, profit decision, hak syuf'ah, closed books) and only
     * then the normal revision, in the same transaction. For a death the deceased cannot sign, so the aggregate
     * is given the deceased as approving once every surviving and incoming partner has consented.
     */
    public void reviseWithLifecycle(Commands.ReviseContract c, LifecycleChange change) {
        Objects.requireNonNull(change, "change");
        mutate(c.tenant(), c.id(), s -> {
            LifecycleChecker.requirePassing(s, c.next(), change);
            java.util.Set<PartnerId> approvals = new java.util.HashSet<>(change.approvals());
            if (change.kind() == LifecycleKind.PARTNER_DEATH) {
                approvals.addAll(change.affected());
            }
            Instant at = now();
            if (change.kind() == LifecycleKind.PARTNER_DEATH) {
                for (PartnerId dead : change.affected()) {
                    s.recordPartnerDeath(dead, change.eventDate(), at);
                }
            }
            s.reviseContract(c.next(), approvals, at);
            if (lifecycle != null) {
                lifecycle.record(c.tenant(), c.id(), c.next().version(), change, at);
            }
            return null;
        });
    }

    public CloseSnapshot correctClose(Commands.CorrectClose c) {
        return mutate(c.tenant(), c.id(), s -> {
            List<CloseId> ids = new ArrayList<>();
            ids.add(c.replacement());
            ids.addAll(c.laterReplacements());
            return s.correctClose(c.target(), ids, c.corrected(), c.fault(), c.disposition(), now()).get(0);
        });
    }

    public void startLiquidation(Commands.StartLiquidation c) {
        mutate(c.tenant(), c.id(), s -> {
            s.startLiquidation(now());
            return null;
        });
    }

    public LiquidationResult liquidate(Commands.Liquidate c) {
        return mutate(c.tenant(), c.id(), s -> s.liquidate(c.input(), c.fault(), now()));
    }

    /** The records behind each lifecycle change of a syirkah, oldest akad version first. */
    public java.util.List<LifecycleRecordPort.Recorded> lifecycleRecords(TenantId tenant, SyirkahId id) {
        load(tenant, id);
        return lifecycle == null ? java.util.List.of() : lifecycle.list(tenant, id);
    }

    public Syirkah get(TenantId tenant, SyirkahId id) {
        return load(tenant, id);
    }

    private <T> T mutate(TenantId tenant, SyirkahId id, Function<Syirkah, T> action) {
        return tx.inTransaction(() -> {
            Syirkah s = load(tenant, id);
            int version = s.getVersion();
            int before = s.movements().size();
            T result = action.apply(s);
            finish(tenant, s, version, before);
            return result;
        });
    }

    private void finish(TenantId tenant, Syirkah s, int expectedVersion, int movementsBefore) {
        repository.save(s, expectedVersion);
        List<PartnerMovement> fresh = new ArrayList<>(s.movements().subList(movementsBefore, s.movements().size()));
        for (PartnerMovement m : fresh) {
            List<SyirkahPostingPlan.Line> plan = SyirkahPostingPlan.forMovement(m);
            if (plan.isEmpty()) {
                continue;
            }
            List<JournalPostingPort.Line> lines = new ArrayList<>();
            for (SyirkahPostingPlan.Line l : plan) {
                lines.add(new JournalPostingPort.Line(accounts.codeOf(l.account()), l.debit(), l.credit(),
                        m.type() + " " + m.partner().value()));
            }
            journal.post(new JournalPostingPort.Request(tenant, SyirkahPostingPlan.sourceKey(s.id(), m),
                    m.occurredAt().atZone(ZoneOffset.UTC).toLocalDate(),
                    "Syirkah " + m.type() + " (" + m.reference() + ")", lines));
        }
        events.publish(tenant, s.pullDomainEvents());
    }

    private Syirkah load(TenantId tenant, SyirkahId id) {
        return repository.find(tenant, id)
                .orElseThrow(() -> new SyirkahNotFoundException("syirkah not found: " + id.value()));
    }

    private Instant now() {
        // microsecond precision: the persisted timestamps (and the audit hashes over them) must survive a store
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
