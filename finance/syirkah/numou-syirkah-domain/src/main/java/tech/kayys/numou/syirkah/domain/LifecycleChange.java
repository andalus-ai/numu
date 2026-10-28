package tech.kayys.numou.numoudomain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A proposed lifecycle change, with the records the book says must exist before it happens.
 *
 * @param kind           what kind of change
 * @param affected       the partners who die ({@link LifecycleKind#PARTNER_DEATH}) or leave
 *                       ({@link LifecycleKind#PARTNER_REPLACEMENT}); empty for new capital
 * @param eventDate      date of death, sale or capital entry
 * @param consents       each partner's recorded answer
 * @param profitDecision what the old partners decided about profit at the close (new capital and replacement)
 * @param syufah         answers to the first-refusal offer (replacement to an outsider)
 * @param successions    for a death: what each heir decided (continue in the deceased's place, or take the share out)
 */
public record LifecycleChange(LifecycleKind kind, Set<PartnerId> affected, LocalDate eventDate,
                              List<LifecycleConsent> consents, ProfitDisposition profitDecision,
                              List<SyufahResponse> syufah, List<Succession> successions) {
    public LifecycleChange {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(eventDate, "eventDate");
        affected = affected == null ? Set.of() : Set.copyOf(affected);
        consents = consents == null ? List.of() : List.copyOf(consents);
        syufah = syufah == null ? List.of() : List.copyOf(syufah);
        successions = successions == null ? List.of() : List.copyOf(successions);
    }

    public LifecycleChange(LifecycleKind kind, Set<PartnerId> affected, LocalDate eventDate,
                           List<LifecycleConsent> consents, ProfitDisposition profitDecision,
                           List<SyufahResponse> syufah) {
        this(kind, affected, eventDate, consents, profitDecision, syufah, List.of());
    }

    /** Partners whose consent was recorded as given. */
    public Set<PartnerId> approvals() {
        return consents.stream().filter(LifecycleConsent::approved).map(LifecycleConsent::partner)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
