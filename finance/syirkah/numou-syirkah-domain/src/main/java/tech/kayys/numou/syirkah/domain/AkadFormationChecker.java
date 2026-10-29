package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks that an akad was validly formed and properly written, against Hukum Muamalah Bab X (rukun, the seven
 * in'iqad conditions, batil and fasad), Bab XI (multi-akad) and Bab XVIII (the written akad).
 *
 * <p>It is the single owner of these formation rules. {@link SyirkahContract#validate()} still owns the rules
 * about the terms themselves (nisbah, capital, forbidden clauses); its violations are passed through here as
 * {@link AkadDefect#UNCLASSIFIED} findings so that one call gives the whole picture.
 *
 * <p>Software cannot decide whether a business activity is lawful or whether a party is of sound mind. It can
 * require that the determination is recorded, and that a negative one blocks the akad.
 */
public final class AkadFormationChecker {

    private AkadFormationChecker() {
    }

    public static List<FormationFinding> check(SyirkahContract contract, AkadFormation formation,
                                               FormationPolicy policy) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(formation, "formation");
        Objects.requireNonNull(policy, "policy");
        List<FormationFinding> f = new ArrayList<>();

        for (ContractViolation v : contract.validate()) {
            f.add(new FormationFinding("CONTRACT_" + v.code(), AkadDefect.UNCLASSIFIED, v.message(),
                    "Bab XIV to XV (terms of the syirkah)"));
        }
        checkParties(contract, formation, policy, f);
        checkSession(formation, f);
        checkObject(formation, f);
        checkMultiAkad(formation, policy, f);
        checkAmbiguity(formation, f);
        checkWrittenAkad(formation, f);
        checkWitnesses(contract, formation, policy, f);
        return List.copyOf(f);
    }

    /** Throws {@link BusinessRuleViolation} listing every finding; returns normally only when there are none. */
    public static void requirePassing(SyirkahContract contract, AkadFormation formation, FormationPolicy policy) {
        List<FormationFinding> findings = check(contract, formation, policy);
        if (!findings.isEmpty()) {
            throw new BusinessRuleViolation("Akad is not validly formed: " + findings);
        }
    }

    private static void checkParties(SyirkahContract contract, AkadFormation formation, FormationPolicy policy,
                                     List<FormationFinding> f) {
        Set<PartnerId> recorded = new HashSet<>();
        for (PartyRecord p : formation.parties()) {
            if (!recorded.add(p.partner())) {
                f.add(new FormationFinding("PARTY_RECORD_DUPLICATE", AkadDefect.INCOMPLETE,
                        "party recorded twice: " + p.partner().value(), "Bab XVIII D.1"));
            }
            if (!contract.hasPartner(p.partner())) {
                f.add(new FormationFinding("PARTY_RECORD_UNKNOWN", AkadDefect.INCOMPLETE,
                        "party record for someone who is not a partner: " + p.partner().value(), "Bab XVIII D.1"));
            }
        }
        for (Partner partner : contract.partners()) {
            PartyRecord p = formation.parties().stream().filter(r -> r.partner().equals(partner.id()))
                    .findFirst().orElse(null);
            if (p == null) {
                f.add(new FormationFinding("PARTY_RECORD_MISSING", AkadDefect.INCOMPLETE,
                        "no party record for " + partner.name(), "Bab XVIII D.1"));
                continue;
            }
            String who = partner.name();
            if (p.dateOfBirth() == null) {
                f.add(new FormationFinding("PARTY_DOB_MISSING", AkadDefect.INCOMPLETE,
                        "date of birth of " + who + " is not recorded, so capacity (mumayyiz) cannot be assessed",
                        "Bab XVIII D.1"));
            } else if (formation.signedOn() != null && p.dateOfBirth().isAfter(formation.signedOn())) {
                f.add(new FormationFinding("PARTY_DOB_INVALID", AkadDefect.INCOMPLETE,
                        "date of birth of " + who + " is after the signing date", "Bab XVIII D.1"));
            } else if (policy.minimumAgeYears() != null && formation.signedOn() != null
                    && Period.between(p.dateOfBirth(), formation.signedOn()).getYears() < policy.minimumAgeYears()) {
                f.add(new FormationFinding("PARTY_BELOW_MINIMUM_AGE", AkadDefect.BATIL,
                        who + " is below the minimum age of " + policy.minimumAgeYears() + " at signing",
                        "Bab X, syarat in'iqad 1 (kelayakan pihak yang berakad)"));
            }
            if (isBlank(p.residence())) {
                f.add(new FormationFinding("PARTY_RESIDENCE_MISSING", AkadDefect.INCOMPLETE,
                        "residence of " + who + " is not recorded", "Bab XVIII D.1"));
            }
            if (!p.soundMind()) {
                f.add(new FormationFinding("PARTY_NOT_OF_SOUND_MIND", AkadDefect.BATIL,
                        who + " is not recorded as of sound mind (berakal)",
                        "Bab X, syarat in'iqad 1 (kelayakan pihak yang berakad)"));
            }
            if (contract.capitalOf(partner.id()).isPositive() && !p.authorityOverCapital()) {
                f.add(new FormationFinding("PARTY_NO_AUTHORITY_OVER_CAPITAL", AkadDefect.BATIL,
                        who + " is not recorded as having authority over the capital they contribute",
                        "Bab X, syarat in'iqad 1 (kewenangan atas barangnya)"));
            }
            if (!p.freeConsent()) {
                f.add(new FormationFinding("PARTY_COERCED", AkadDefect.FASAD,
                        who + " did not consent freely (ikrah)", "Bab X, akad fasad (ikrah)"));
            }
            if (isBlank(p.ijabQabulStatement())) {
                f.add(new FormationFinding("PARTY_IJAB_QABUL_MISSING", AkadDefect.INCOMPLETE,
                        "the written ijab and qabul wording of " + who + " is missing", "Bab XVIII D.2"));
            }
        }
    }

    private static void checkSession(AkadFormation formation, List<FormationFinding> f) {
        if (!formation.sameSession()) {
            f.add(new FormationFinding("SESSION_NOT_UNIFIED", AkadDefect.BATIL,
                    "ijab and qabul did not take place in one session", "Bab X, syarat in'iqad 6 (kesatuan majelis)"));
        }
        if (formation.ijabWithdrawn()) {
            f.add(new FormationFinding("IJAB_WITHDRAWN", AkadDefect.BATIL,
                    "the ijab was withdrawn before qabul, so the akad is treated as never having happened",
                    "Bab X, syarat in'iqad 7"));
        }
    }

    private static void checkObject(AkadFormation formation, List<FormationFinding> f) {
        if (isBlank(formation.businessActivity())) {
            f.add(new FormationFinding("OBJECT_NOT_STATED", AkadDefect.INCOMPLETE,
                    "the business activity (object of the akad) is not stated", "Bab XVIII C.4 b"));
        }
        f.addAll(AgricultureChecker.scan(formation.businessActivity(), formation.combinedAkad()));
        AkadFormation.LawfulnessReview r = formation.lawfulnessReview();
        if (r == null || isBlank(r.reviewedBy()) || r.reviewedOn() == null) {
            f.add(new FormationFinding("LAWFULNESS_REVIEW_MISSING", AkadDefect.INCOMPLETE,
                    "no recorded review that the business activity is lawful",
                    "Bab X, syarat in'iqad 2 and 3 (objek akad; bukan akad yang dilarang nash)"));
        } else if (!r.lawful()) {
            f.add(new FormationFinding("OBJECT_NOT_LAWFUL", AkadDefect.BATIL,
                    "the business activity was reviewed as not lawful",
                    "Bab X, syarat in'iqad 2 and 3 (objek akad; bukan akad yang dilarang nash)"));
        }
    }

    private static void checkMultiAkad(AkadFormation formation, FormationPolicy policy, List<FormationFinding> f) {
        if (policy.blockMultiAkad() && !formation.combinedAkad().isEmpty()) {
            f.add(new FormationFinding("MULTI_AKAD", AkadDefect.BATIL,
                    "this akad combines other akad in one contract: " + formation.combinedAkad(),
                    "Bab X C.1.a and Bab XI (shafqatain fi shafqatin; khilafiyah, see roadmap decision 8)"));
        }
    }

    private static void checkAmbiguity(AkadFormation formation, List<FormationFinding> f) {
        if (!formation.unresolvedAmbiguities().isEmpty()) {
            f.add(new FormationFinding("AMBIGUOUS_TERMS", AkadDefect.FASAD,
                    "terms are unclear or open to more than one meaning: " + formation.unresolvedAmbiguities(),
                    "Bab X, akad fasad (majhul) and Bab XVIII D.4"));
        }
    }

    private static void checkWrittenAkad(AkadFormation formation, List<FormationFinding> f) {
        for (AkadSection s : AkadSection.values()) {
            if (!formation.sectionsPresent().contains(s)) {
                f.add(new FormationFinding("SECTION_MISSING_" + s.name(), AkadDefect.INCOMPLETE,
                        "the written akad lacks the section " + s, "Bab XVIII C.4 (items a to j)"));
            }
        }
        if (isBlank(formation.signedPlace())) {
            f.add(new FormationFinding("SIGNING_PLACE_MISSING", AkadDefect.INCOMPLETE,
                    "place of signing is not recorded", "Bab XVIII C.5 a"));
        }
        if (formation.signedOn() == null) {
            f.add(new FormationFinding("SIGNING_DATE_MISSING", AkadDefect.INCOMPLETE,
                    "date of signing is not recorded", "Bab XVIII C.5 a"));
        }
    }

    private static void checkWitnesses(SyirkahContract contract, AkadFormation formation, FormationPolicy policy,
                                       List<FormationFinding> f) {
        List<Witness> named = formation.witnesses().stream().filter(w -> !w.name().isEmpty()).toList();
        if (named.size() != formation.witnesses().size()) {
            f.add(new FormationFinding("WITNESS_NAME_BLANK", AkadDefect.INCOMPLETE,
                    "every witness must be named", "Bab XVIII C.5 c"));
        }
        Set<String> inFormation = new HashSet<>();
        named.forEach(w -> inFormation.add(w.name()));
        Set<String> inContract = new HashSet<>();
        contract.witnesses().stream().filter(w -> w != null && !w.isBlank()).forEach(w -> inContract.add(w.trim()));
        if (!inFormation.equals(inContract)) {
            f.add(new FormationFinding("WITNESS_MISMATCH", AkadDefect.INCOMPLETE,
                    "the witnesses of the contract and of the signed akad differ", "Bab XVIII C.5 c"));
        }
        long men = named.stream().filter(w -> w.sex() == WitnessSex.MALE).count();
        long women = named.stream().filter(w -> w.sex() == WitnessSex.FEMALE).count();
        boolean enough = switch (policy.witnessRule()) {
            case COUNT_ONLY -> named.size() >= SyirkahContract.MIN_WITNESSES;
            case NISHAB -> men >= 2 || (men >= 1 && women >= 2) || women >= 4;
        };
        if (!enough) {
            f.add(new FormationFinding("WITNESS_NISHAB", AkadDefect.INCOMPLETE,
                    policy.witnessRule() == WitnessRule.NISHAB
                            ? "witnesses must be two men, or one man and two women, or four women; found "
                                    + men + " men and " + women + " women"
                            : "at least " + SyirkahContract.MIN_WITNESSES + " named witnesses are required",
                    "Bab XVIII C.5 c and D.6"));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
