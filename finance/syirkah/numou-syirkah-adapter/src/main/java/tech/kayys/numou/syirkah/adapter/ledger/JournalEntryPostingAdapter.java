package tech.kayys.numou.numouadapter.ledger;

import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.numouapplication.port.JournalPostingPort;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Function;

/**
 * Posts syirkah movements as numou {@link JournalEntry} aggregates (balance
 * invariant enforced by the
 * entry itself). Idempotent per source key through {@link Sink#exists}.
 */
public class JournalEntryPostingAdapter implements JournalPostingPort {

    /** Persistence of generated entries plus the idempotency record. */
    public interface Sink {
        boolean exists(String tenant, String sourceKey);

        void save(String tenant, String sourceKey, JournalEntry entry);
    }

    private final Function<String, AccountId> accountResolver;
    private final Sink sink;
    private final LedgerId ledger;

    public JournalEntryPostingAdapter(Function<String, AccountId> accountResolver, Sink sink, LedgerId ledger) {
        this.accountResolver = Objects.requireNonNull(accountResolver);
        this.sink = Objects.requireNonNull(sink);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public void post(Request r) {
        String tenant = r.tenant().value().toString();
        if (sink.exists(tenant, r.sourceKey())) {
            return;
        }
        JournalEntry e = new JournalEntry(JournalEntryId.generate(), new TenantRef(tenant), ledger,
                r.sourceKey(), r.date().atStartOfDay().toInstant(ZoneOffset.UTC), r.description());
        for (Line l : r.lines()) {
            e.addLine(accountResolver.apply(l.accountCode()), l.debit(), l.credit(), l.memo());
        }
        e.post("syirkah");
        sink.save(tenant, r.sourceKey(), e);
    }
}
