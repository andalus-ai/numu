package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a lifecycle change against Hukum Muamalah Bab XVI (all partners decide), Bab XXIV (new capital),
 * Bab XXV (partner replacement and hak syuf'ah) and Bab X (death ends the akad).
 *
 * <p>It does not change the syirkah. {@link Syirkah#reviseContract} still does the money movements and still
 * enforces closed books, a later effective date and unanimity; this checker adds the records those rules need
 * (who agreed, on what evidence, what was decided about profit, who waived first refusal) and the rules that
 * belong to one kind of change.
 */
public final class LifecycleChecker {

    private LifecycleChecker() {
    }

    public static List<LifecycleFinding> check(Syirkah syirkah, SyirkahContract next, LifecycleChange change) {
        Objects.requireNonNull(syirkah, "syirkah");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(change, "change");
        List<LifecycleFinding> f = new ArrayList<>();
        SyirkahContract cur = syirkah.contract();

        if (syirkah.status() != SyirkahStatus.ACTIVE) {
            f.add(new LifecycleFinding("NOT_ACTIVE", "only an active syirkah can change", "Bab XXVI"));
        }
        if (next.version() != cur.version() + 1) {
            f.add(new LifecycleFinding("NEW_AKAD_VERSION", "the change must produce akad version "
                    + (cur.version() + 1), "Bab XXIV to XXV (new akad after the change)"));
        }
        if (!syirkah.snapshot().closedSinceRevision()) {
            f.add(new LifecycleFinding("BOOKS_NOT_CLOSED", "close the books (tutup buku) first", "Bab XXIV to XXV"));
        }
        checkConsent(cur, next, change, f);
        switch (change.kind()) {
            case NEW_CAPITAL -> checkNewCapital(cur, next, change, f);
            case PARTNER_REPLACEMENT -> checkReplacement(cur, next, change, f);
            case PARTNER_DEATH -> checkDeath(syirkah, cur, next, change, f);
        }
        return List.copyOf(f);
    }

    public static void requirePassing(Syirkah syirkah, SyirkahContract next, LifecycleChange change) {
        List<LifecycleFinding> findings = check(syirkah, next, change);
        if (!findings.isEmpty()) {
            throw new BusinessRuleViolation("Lifecycle change is not allowed: " + findings);
        }
    }

    private static void checkConsent(SyirkahContract cur, SyirkahContract next, LifecycleChange ch,
                                     List<LifecycleFinding> f) {
        Set<PartnerId> required = new HashSet<>(cur.partnerIds());
        required.addAll(next.partnerIds());
        if (ch.kind() == LifecycleKind.PARTNER_DEATH) {
            required.removeAll(ch.affected()); // the dead cannot consent
        }
        Set<PartnerId> approved = ch.approvals();
        for (LifecycleConsent c : ch.consents()) {
            if (!c.approved()) {
                f.add(new LifecycleFinding("DISSENT", "partner " + c.partner().value() + " did not agree; the "
                        + "change cannot proceed without every partner, and a partner who will not agree can only "
                        + "leave by dissolution or replacement", "Bab XVI, XXV"));
            }
        }
        for (PartnerId p : required) {
            if (!approved.contains(p) && ch.consents().stream().noneMatch(c -> c.partner().equals(p))) {
                f.add(new LifecycleFinding("CONSENT_MISSING", "no recorded consent from partner " + p.value(),
                        "Bab XVI, XXIV, XXV"));
            }
        }
    }

    private static void checkNewCapital(SyirkahContract cur, SyirkahContract next, LifecycleChange ch,
                                        List<LifecycleFinding> f) {
        if (ch.profitDecision() == null) {
            f.add(new LifecycleFinding("PROFIT_DECISION_MISSING", "the old partners must decide whether the "
                    + "profit at the close is distributed or retained", "Bab XXIV"));
        }
        for (PartnerId p : cur.partnerIds()) {
            if (!next.hasPartner(p)) {
                f.add(new LifecycleFinding("PARTNER_REMOVED", "new capital must not remove partner " + p.value()
                        + "; use partner replacement", "Bab XXIV"));
            } else if (next.capitalOf(p).compareTo(cur.capitalOf(p)) < 0) {
                f.add(new LifecycleFinding("CAPITAL_REDUCED", "new capital must not reduce the capital of partner "
                        + p.value(), "Bab XXIV"));
            }
        }
        boolean increased = next.partnerIds().stream()
                .anyMatch(p -> !cur.hasPartner(p) || next.capitalOf(p).compareTo(cur.capitalOf(p)) > 0);
        if (!increased) {
            f.add(new LifecycleFinding("NO_NEW_CAPITAL", "the new akad adds no capital", "Bab XXIV"));
        }
    }

    private static void checkReplacement(SyirkahContract cur, SyirkahContract next, LifecycleChange ch,
                                         List<LifecycleFinding> f) {
        if (ch.affected().isEmpty()) {
            f.add(new LifecycleFinding("NO_LEAVING_PARTNER", "name the partner who leaves", "Bab XXV"));
        }
        if (ch.profitDecision() == null) {
            f.add(new LifecycleFinding("PROFIT_DECISION_MISSING", "the old partners must decide whether the "
                    + "profit at the close is distributed or retained", "Bab XXV"));
        }
        for (PartnerId p : ch.affected()) {
            if (!cur.hasPartner(p)) {
                f.add(new LifecycleFinding("NOT_A_PARTNER", p.value() + " is not a partner", "Bab XXV"));
            }
            if (next.hasPartner(p)) {
                f.add(new LifecycleFinding("LEAVING_PARTNER_REMAINS", p.value() + " is still in the new akad",
                        "Bab XXV"));
            }
        }
        Set<PartnerId> outsiders = new HashSet<>(next.partnerIds());
        outsiders.removeAll(cur.partnerIds());
        if (outsiders.isEmpty()) {
            return; // sale to an existing partner: no outside buyer, so no first refusal needed
        }
        Set<PartnerId> remaining = new HashSet<>(cur.partnerIds());
        remaining.removeAll(ch.affected());
        for (PartnerId p : remaining) {
            SyufahResponse r = ch.syufah().stream().filter(s -> s.partner().equals(p)).findFirst().orElse(null);
            if (r == null) {
                f.add(new LifecycleFinding("SYUFAH_NOT_OFFERED", "partner " + p.value()
                        + " has not been offered the share first", "Bab XXV, XXVI (hak syuf'ah)"));
            } else if (r.exercised()) {
                f.add(new LifecycleFinding("SYUFAH_EXERCISED", "partner " + p.value() + " chose to buy the share; "
                        + "it cannot go to an outsider", "Bab XXV, XXVI (hak syuf'ah)"));
            }
        }
    }

    private static void checkDeath(Syirkah syirkah, SyirkahContract cur, SyirkahContract next, LifecycleChange ch,
                                   List<LifecycleFinding> f) {
        if (ch.affected().isEmpty()) {
            f.add(new LifecycleFinding("NO_DECEASED", "name the partner who died", "Bab X (akad fasakh)"));
        }
        for (PartnerId p : ch.affected()) {
            if (!cur.hasPartner(p)) {
                f.add(new LifecycleFinding("NOT_A_PARTNER", p.value() + " is not a partner", "Bab X"));
            }
            if (next.hasPartner(p)) {
                f.add(new LifecycleFinding("DECEASED_REMAINS", "a partner who died cannot be in the new akad; "
                        + "the old akad ended with the death", "Bab X (akad fasakh)"));
            }
            if (ch.successions().stream().noneMatch(s -> s.deceased().equals(p))) {
                f.add(new LifecycleFinding("SUCCESSION_UNDECIDED", "the heir of " + p.value() + " has not decided "
                        + "whether to continue in the deceased's place", "Bab X (akad fasakh), XXV"));
            }
        }
        for (Succession s : ch.successions()) {
            if (!ch.affected().contains(s.deceased())) {
                f.add(new LifecycleFinding("SUCCESSION_FOR_LIVING", s.deceased().value() + " is not recorded as "
                        + "deceased", "Bab X"));
            }
            if (s.continues() && !next.hasPartner(s.heir())) {
                f.add(new LifecycleFinding("HEIR_NOT_IN_AKAD", "heir " + s.heir().value() + " decided to continue "
                        + "but is not a partner in the new akad", "Bab X, XXV"));
            }
            if (!s.continues() && next.hasPartner(s.heir())) {
                f.add(new LifecycleFinding("HEIR_DECLINED_BUT_IN_AKAD", "heir " + s.heir().value()
                        + " decided not to continue but is in the new akad", "Bab X, XXV"));
            }
        }
        // The deceased must stop sharing in profit at the date of death, so the books must be closed up to it.
        LocalDate closedTo = syirkah.closes().stream().filter(c -> c.status() == CloseStatus.ACTIVE)
                .map(CloseSnapshot::periodEnd).max(LocalDate::compareTo).orElse(null);
        if (closedTo == null || closedTo.isBefore(ch.eventDate())) {
            f.add(new LifecycleFinding("BOOKS_NOT_CLOSED_TO_DEATH", "the books must be closed up to the date of "
                    + "death (" + ch.eventDate() + ") so the deceased's profit or loss ends there",
                    "Bab XXIV to XXV (tutup buku), Bab X"));
        }
    }
}
