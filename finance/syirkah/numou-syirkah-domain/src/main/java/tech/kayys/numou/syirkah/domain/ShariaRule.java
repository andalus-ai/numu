package tech.kayys.numou.numoudomain;

import java.util.List;
import java.util.Objects;

/**
 * One Sharia rule as Numou implements it, with where it comes from, how it is enforced and what proves it.
 *
 * @param id           the rule number in the roadmap's compliance register
 * @param title        the rule in plain words
 * @param source       where the book states it (Hukum Muamalah, by chapter)
 * @param enforcement  how Numou enforces it
 * @param ruling       whether a Sharia adviser has to rule on it
 * @param codes        the codes of the findings or violations that enforce it; a trailing {@code *} matches a prefix
 * @param testedBy     test classes (in the domain module) that prove behaviour no finding code shows
 * @param note         limits and caveats, stated plainly
 */
public record ShariaRule(int id, String title, String source, Enforcement enforcement, Ruling ruling,
                         List<String> codes, List<String> testedBy, String note) {

    /** How a rule is enforced. */
    public enum Enforcement {
        /** A finding or violation stops the action. */
        BLOCKS,
        /** Software cannot judge it; it requires that a determination is recorded, and a negative one blocks. */
        RECORDED,
        /** Built into a calculation, so no finding is needed. */
        BEHAVIOUR,
        /** Not a ledger concern. */
        OUT_OF_SCOPE
    }

    /** Whether the rule needs the Sharia adviser. */
    public enum Ruling {
        /** The book states the rule plainly. */
        BOOK_STATED,
        /** A matter on which scholars differ, or a setting the owner chose; waits for the adviser's written ruling. */
        PENDING_ADVISER,
        /** The adviser has ruled; the sign-off is recorded in {@link ShariaRuleCatalog#ADVISER_SIGNOFF}. */
        CONFIRMED
    }

    public ShariaRule {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(ruling, "ruling");
        codes = List.copyOf(codes);
        testedBy = List.copyOf(testedBy);
        note = note == null ? "" : note;
    }

    /** True when {@code code} is enforced by this rule. */
    public boolean covers(String code) {
        for (String c : codes) {
            if (c.endsWith("*") ? code.startsWith(c.substring(0, c.length() - 1)) : c.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
