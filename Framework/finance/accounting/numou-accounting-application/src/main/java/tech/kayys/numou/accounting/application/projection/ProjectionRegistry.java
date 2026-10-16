package tech.kayys.syirkah.accounting.application.projection;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProjectionRegistry {
    private final List<Projection<AccountingEvent>> projections = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unchecked")
    public void register(Projection<? extends AccountingEvent> projection) {
        projections.add((Projection<AccountingEvent>) projection);
    }

    public List<Projection<AccountingEvent>> all() {
        return List.copyOf(projections);
    }

    public Uni<Void> publishToProjections(AccountingEvent event) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (Projection<AccountingEvent> proj : projections) {
            chain = chain.chain(() -> proj.project(event));
        }
        return chain;
    }
}
