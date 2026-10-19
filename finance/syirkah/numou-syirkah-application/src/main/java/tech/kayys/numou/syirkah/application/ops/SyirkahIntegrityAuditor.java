package tech.kayys.numou.syirkah.application.ops;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.syirkah.application.port.SyirkahCatalogPort;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.application.query.SyirkahSummary;
import tech.kayys.numou.syirkah.domain.EquityReconciliation;
import tech.kayys.numou.syirkah.domain.Syirkah;
import tech.kayys.numou.syirkah.domain.SyirkahId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Scheduled-job building block: re-loads every syirkah of a tenant (which re-verifies all hash chains and structural
 * invariants), then checks the equity identity. Never throws for bad data: a syirkah that cannot be loaded is a
 * finding, so one corrupt ledger cannot hide the others.
 */
public class SyirkahIntegrityAuditor {

    public record Finding(SyirkahId id, List<String> problems) {
        public Finding {
            problems = List.copyOf(problems);
        }
    }

    public record Report(TenantId tenant, int checked, List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public boolean ok() {
            return findings.isEmpty();
        }
    }

    private static final int PAGE = 100;

    private final SyirkahRepository repository;
    private final SyirkahCatalogPort catalog;

    public SyirkahIntegrityAuditor(SyirkahRepository repository, SyirkahCatalogPort catalog) {
        this.repository = Objects.requireNonNull(repository);
        this.catalog = Objects.requireNonNull(catalog);
    }

    public Report audit(TenantId tenant) {
        List<Finding> findings = new ArrayList<>();
        int checked = 0;
        Optional<SyirkahId> after = Optional.empty();
        while (true) {
            List<SyirkahSummary> page = catalog.page(tenant, Optional.empty(), after, PAGE);
            for (SyirkahSummary s : page) {
                checked++;
                List<String> problems = check(tenant, s.id());
                if (!problems.isEmpty()) {
                    findings.add(new Finding(s.id(), problems));
                }
            }
            if (page.size() < PAGE) {
                return new Report(tenant, checked, findings);
            }
            after = Optional.of(page.get(page.size() - 1).id());
        }
    }

    private List<String> check(TenantId tenant, SyirkahId id) {
        List<String> problems = new ArrayList<>();
        try {
            Optional<Syirkah> loaded = repository.find(tenant, id);
            if (loaded.isEmpty()) {
                problems.add("listed in the catalog but not loadable");
                return problems;
            }
            Syirkah s = loaded.get();
            problems.addAll(s.verifyIntegrity());
            EquityReconciliation eq = s.reconcile();
            if (!eq.balanced()) {
                problems.add("partner equity does not reconcile: difference " + eq.difference().amount().toPlainString());
            }
        } catch (RuntimeException e) {
            problems.add("cannot be restored: " + e.getMessage());
        }
        return problems;
    }
}
