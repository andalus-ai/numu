package tech.kayys.numou.syirkah.adapter.memory;

import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.application.port.JournalPostingPort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test double that records journals once per sourceKey and checks each balances. */
public class InMemoryJournalPostingPort implements JournalPostingPort {

    private final Map<String, Request> posted = new LinkedHashMap<>();

    @Override
    public synchronized void post(Request request) {
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        for (Line l : request.lines()) {
            d = d.add(l.debit().amount());
            c = c.add(l.credit().amount());
        }
        if (d.compareTo(c) != 0) {
            throw new IllegalStateException("unbalanced journal " + request.sourceKey());
        }
        posted.putIfAbsent(request.tenant().value() + "|" + request.sourceKey(), request);
    }

    public synchronized List<Request> posted() {
        return new ArrayList<>(posted.values());
    }

    /** Net debit - credit per account code across everything posted. */
    public synchronized Map<String, Money> trialBalance(String currency) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Request r : posted.values()) {
            for (Line l : r.lines()) {
                m.merge(l.accountCode(), l.debit().amount().subtract(l.credit().amount()), BigDecimal::add);
            }
        }
        Map<String, Money> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, Money.of(v, currency)));
        return out;
    }
}
