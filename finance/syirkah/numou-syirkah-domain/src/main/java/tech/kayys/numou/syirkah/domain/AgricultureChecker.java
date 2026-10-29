package tech.kayys.numou.numoudomain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Agriculture arrangements under Hukum Muamalah Bab XV, section H. Muzara'ah (the land is worked and the crop
 * shared) and mukhabarah (the same with the worker supplying seed and costs) are haram: land may not be let for a
 * share of the harvest. Musaqah, where the owner of land that already has trees has them tended for a share of the
 * fruit, is allowed.
 *
 * <p>Numou has no ledger type for these. This class only keeps the barred ones out: it recognises them by name in
 * the akad's stated activity and combined akad, and checks a stated musaqah.
 */
public final class AgricultureChecker {

    private AgricultureChecker() {
    }

    public enum Kind { MUZARAAH, MUKHABARAH, MUSAQAH }

    /**
     * @param kind                the arrangement
     * @param landAlreadyPlanted  for musaqah: the land already has the trees (tending only); false means planting
     *                            from the start, which the book treats like muzara'ah
     */
    public record Arrangement(Kind kind, boolean landAlreadyPlanted) {
        public Arrangement {
            Objects.requireNonNull(kind, "kind");
        }
    }

    public static List<FormationFinding> check(Arrangement a) {
        Objects.requireNonNull(a, "arrangement");
        List<FormationFinding> f = new ArrayList<>();
        if (a.kind() == Kind.MUZARAAH || a.kind() == Kind.MUKHABARAH) {
            f.add(barred(a.kind().name().toLowerCase(Locale.ROOT)));
        } else if (!a.landAlreadyPlanted()) {
            f.add(new FormationFinding("MUSAQAH_NOT_PLANTED", AkadDefect.BATIL,
                    "musaqah is allowed only for land that already has its trees, so the worker only tends them; "
                            + "planting from the start is like muzara'ah", "Bab XV section H"));
        }
        return List.copyOf(f);
    }

    /** Findings for any barred arrangement named in the stated activity or the combined akad. */
    public static List<FormationFinding> scan(String businessActivity, Collection<String> combinedAkad) {
        List<FormationFinding> f = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        if (businessActivity != null) {
            texts.add(businessActivity);
        }
        if (combinedAkad != null) {
            texts.addAll(combinedAkad);
        }
        boolean muzaraah = false;
        boolean mukhabarah = false;
        for (String t : texts) {
            String n = normalise(t);
            muzaraah |= n.contains("muzaraah") || n.contains("muzara");
            mukhabarah |= n.contains("mukhabarah");
        }
        if (muzaraah) {
            f.add(barred("muzara'ah"));
        }
        if (mukhabarah) {
            f.add(barred("mukhabarah"));
        }
        return f;
    }

    private static FormationFinding barred(String name) {
        return new FormationFinding("BARRED_AGRICULTURE_AKAD", AkadDefect.BATIL,
                name + " is haram: land may not be let for a share of the harvest",
                "Bab XV section H; Bab X syarat in'iqad 3 (akad yang dilarang nash)");
    }

    private static String normalise(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        return d.replaceAll("[^a-z]", "");
    }
}
