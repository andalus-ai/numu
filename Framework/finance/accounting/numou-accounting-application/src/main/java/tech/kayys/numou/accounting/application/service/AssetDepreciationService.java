package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.FixedAssetRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.FixedAsset;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AssetDepreciationService {

    private final FixedAssetRepository fixedAssetRepository;
    private final JournalEntryRepository journalEntryRepository;

    public AssetDepreciationService(FixedAssetRepository fixedAssetRepository, JournalEntryRepository journalEntryRepository) {
        this.fixedAssetRepository = Objects.requireNonNull(fixedAssetRepository);
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
    }

    public Uni<List<JournalEntryId>> runMonthlyDepreciation(LocalDate runDate, String postedBy) {
        return fixedAssetRepository.findAll()
                .chain(assets -> {
                    List<JournalEntryId> generatedEntries = new ArrayList<>();
                    Uni<Void> runChain = Uni.createFrom().voidItem();

                    for (FixedAsset asset : assets) {
                        Money monthlyDep = asset.calculateMonthlyDepreciation();
                        if (monthlyDep.isZero()) continue;

                        JournalEntryId jeId = JournalEntryId.generate();
                        JournalEntry je = new JournalEntry(
                                jeId,
                                "DEP-" + asset.getAssetNumber() + "-" + runDate.toString(),
                                Instant.now(),
                                "Monthly Depreciation for Asset " + asset.getName() + " (" + asset.getAssetNumber() + ")"
                        );

                        je.addLine(asset.getDepreciationExpenseAccountId(), monthlyDep, Money.zero(monthlyDep.currency()), "Depreciation Expense");
                        je.addLine(asset.getAccumulatedDepreciationAccountId(), Money.zero(monthlyDep.currency()), monthlyDep, "Accumulated Depreciation");
                        je.post();

                        asset.applyMonthlyDepreciation(monthlyDep);
                        generatedEntries.add(jeId);

                        runChain = runChain
                                .chain(() -> journalEntryRepository.save(je))
                                .chain(() -> fixedAssetRepository.save(asset));
                    }

                    return runChain.map(v -> generatedEntries);
                });
    }
}
