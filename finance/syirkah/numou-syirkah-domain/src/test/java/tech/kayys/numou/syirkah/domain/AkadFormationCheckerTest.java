package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.A;
import static tech.kayys.numou.numoudomain.Fixtures.B;
import static tech.kayys.numou.numoudomain.Fixtures.inan;
import static tech.kayys.numou.numoudomain.Fixtures.ratio;

class AkadFormationCheckerTest {

    private static final LocalDate SIGNED = LocalDate.parse("2026-01-01");

    private static SyirkahContract contract() {
        return inan(100_000_000, 0, ratio(A, 40, B, 60));
    }

    private static PartyRecord party(PartnerId id) {
        return new PartyRecord(id, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true,
                "I offer to join this syirkah and I accept it");
    }

    private static AkadFormation complete() {
        return new AkadFormation(List.of(party(A), party(B)), "Retail of household goods",
                new AkadFormation.LawfulnessReview("Sharia adviser", SIGNED, true), Set.of(), List.of(), true, false,
                EnumSet.allOf(AkadSection.class), "Jakarta", SIGNED,
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE)));
    }

    private static AkadFormation with(UnaryOperator<AkadFormation> change) {
        return change.apply(complete());
    }

    private static AkadFormation witnesses(Witness... w) {
        AkadFormation c = complete();
        return new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(), c.combinedAkad(),
                c.unresolvedAmbiguities(), c.sameSession(), c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(),
                c.signedOn(), List.of(w));
    }

    private static SyirkahContract contractWithWitnesses(String... names) {
        SyirkahContract c = contract();
        return new SyirkahContract(c.version(), c.type(), c.currency(), c.partners(), c.capital(), c.nisbah(),
                c.profitBasis(), c.lossSharing(), c.declaredClauses(), List.of(names), c.ijabQabulRecorded(),
                c.effectiveFrom());
    }

    private static FormationFinding find(List<FormationFinding> findings, String code) {
        return findings.stream().filter(f -> f.code().equals(code)).findFirst().orElse(null);
    }

    private static List<FormationFinding> check(AkadFormation f) {
        return AkadFormationChecker.check(contract(), f, FormationPolicy.bookDefault());
    }

    @Test
    void aCompleteAkadPassesUnderTheBookDefaults() {
        assertEquals(List.of(), check(complete()));
        AkadFormationChecker.requirePassing(contract(), complete(), FormationPolicy.bookDefault());
    }

    @Test
    void missingDateOfBirthIsIncompleteBecauseCapacityCannotBeAssessed() {
        PartyRecord noDob = new PartyRecord(A, null, "Jakarta", true, true, true, "ijab and qabul");
        AkadFormation f = with(c -> new AkadFormation(List.of(noDob, party(B)), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.INCOMPLETE, find(check(f), "PARTY_DOB_MISSING").defect());
    }

    @Test
    void aPartyNotOfSoundMindMakesTheAkadBatil() {
        PartyRecord p = new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", false, true, true, "ijab");
        AkadFormation f = with(c -> new AkadFormation(List.of(p, party(B)), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.BATIL, find(check(f), "PARTY_NOT_OF_SOUND_MIND").defect());
    }

    @Test
    void aCapitalContributorWithoutAuthorityOverTheCapitalMakesTheAkadBatil() {
        PartyRecord p = new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", true, false, true, "ijab");
        AkadFormation f = with(c -> new AkadFormation(List.of(p, party(B)), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.BATIL, find(check(f), "PARTY_NO_AUTHORITY_OVER_CAPITAL").defect());
        // B contributes no capital, so the same flag on B is not a defect.
        PartyRecord b = new PartyRecord(B, LocalDate.parse("1990-05-17"), "Jakarta", true, false, true, "ijab");
        AkadFormation g = with(c -> new AkadFormation(List.of(party(A), b), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(null, find(check(g), "PARTY_NO_AUTHORITY_OVER_CAPITAL"));
    }

    @Test
    void coercionMakesTheAkadFasadNotBatil() {
        PartyRecord p = new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", true, true, false, "ijab");
        AkadFormation f = with(c -> new AkadFormation(List.of(p, party(B)), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.FASAD, find(check(f), "PARTY_COERCED").defect());
    }

    @Test
    void minimumAgeIsOnlyCheckedWhenThePolicyNamesOne() {
        PartyRecord young = new PartyRecord(A, LocalDate.parse("2015-01-01"), "Jakarta", true, true, true, "ijab");
        AkadFormation f = with(c -> new AkadFormation(List.of(young, party(B)), c.businessActivity(),
                c.lawfulnessReview(), c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(),
                c.ijabWithdrawn(), c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(null, find(check(f), "PARTY_BELOW_MINIMUM_AGE"));
        FormationPolicy withAge = new FormationPolicy(WitnessRule.NISHAB, true, 18);
        List<FormationFinding> found = AkadFormationChecker.check(contract(), f, withAge);
        assertEquals(AkadDefect.BATIL, find(found, "PARTY_BELOW_MINIMUM_AGE").defect());
    }

    @Test
    void separateSessionsAndWithdrawnIjabAreBatil() {
        AkadFormation f = with(c -> new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(),
                c.combinedAkad(), c.unresolvedAmbiguities(), false, true, c.sectionsPresent(), c.signedPlace(),
                c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.BATIL, find(check(f), "SESSION_NOT_UNIFIED").defect());
        assertEquals(AkadDefect.BATIL, find(check(f), "IJAB_WITHDRAWN").defect());
    }

    @Test
    void theLawfulnessOfTheActivityMustBeRecordedAndNotNegative() {
        AkadFormation none = with(c -> new AkadFormation(c.parties(), c.businessActivity(), null,
                c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(), c.ijabWithdrawn(),
                c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.INCOMPLETE, find(check(none), "LAWFULNESS_REVIEW_MISSING").defect());
        AkadFormation no = with(c -> new AkadFormation(c.parties(), c.businessActivity(),
                new AkadFormation.LawfulnessReview("Sharia adviser", SIGNED, false), c.combinedAkad(),
                c.unresolvedAmbiguities(), c.sameSession(), c.ijabWithdrawn(), c.sectionsPresent(),
                c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.BATIL, find(check(no), "OBJECT_NOT_LAWFUL").defect());
    }

    @Test
    void multiAkadIsBatilUnderTheBookDefaultAndAllowedWhenThePolicyAllowsIt() {
        AkadFormation f = with(c -> new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(),
                Set.of("ijarah"), c.unresolvedAmbiguities(), c.sameSession(), c.ijabWithdrawn(),
                c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.BATIL, find(check(f), "MULTI_AKAD").defect());
        FormationPolicy lenient = new FormationPolicy(WitnessRule.NISHAB, false, null);
        assertEquals(List.of(), AkadFormationChecker.check(contract(), f, lenient));
    }

    @Test
    void unclearTermsAreFasad() {
        AkadFormation f = with(c -> new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(),
                c.combinedAkad(), List.of("clause 4 can be read two ways"), c.sameSession(), c.ijabWithdrawn(),
                c.sectionsPresent(), c.signedPlace(), c.signedOn(), c.witnesses()));
        assertEquals(AkadDefect.FASAD, find(check(f), "AMBIGUOUS_TERMS").defect());
    }

    @Test
    void everyMissingSectionIsReported() {
        AkadFormation f = with(c -> new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(),
                c.combinedAkad(), c.unresolvedAmbiguities(), c.sameSession(), c.ijabWithdrawn(),
                EnumSet.of(AkadSection.PARTIES), c.signedPlace(), c.signedOn(), c.witnesses()));
        List<FormationFinding> found = check(f);
        for (AkadSection s : AkadSection.values()) {
            boolean reported = find(found, "SECTION_MISSING_" + s.name()) != null;
            assertEquals(s != AkadSection.PARTIES, reported, s.name());
        }
    }

    @Test
    void nishabAcceptsTheThreeCombinationsOfTheBook() {
        assertEquals(null, find(AkadFormationChecker.check(contractWithWitnesses("M1", "M2"),
                witnesses(new Witness("M1", WitnessSex.MALE), new Witness("M2", WitnessSex.MALE)),
                FormationPolicy.bookDefault()), "WITNESS_NISHAB"));
        assertEquals(null, find(AkadFormationChecker.check(contractWithWitnesses("M1", "F1", "F2"),
                witnesses(new Witness("M1", WitnessSex.MALE), new Witness("F1", WitnessSex.FEMALE),
                        new Witness("F2", WitnessSex.FEMALE)), FormationPolicy.bookDefault()), "WITNESS_NISHAB"));
        assertEquals(null, find(AkadFormationChecker.check(contractWithWitnesses("F1", "F2", "F3", "F4"),
                witnesses(new Witness("F1", WitnessSex.FEMALE), new Witness("F2", WitnessSex.FEMALE),
                        new Witness("F3", WitnessSex.FEMALE), new Witness("F4", WitnessSex.FEMALE)),
                FormationPolicy.bookDefault()), "WITNESS_NISHAB"));
    }

    @Test
    void nishabRejectsOtherCombinationsThatCountOnlyWouldAccept() {
        SyirkahContract c = contractWithWitnesses("M1", "F1");
        AkadFormation f = witnesses(new Witness("M1", WitnessSex.MALE), new Witness("F1", WitnessSex.FEMALE));
        assertEquals(AkadDefect.INCOMPLETE,
                find(AkadFormationChecker.check(c, f, FormationPolicy.bookDefault()), "WITNESS_NISHAB").defect());
        FormationPolicy countOnly = new FormationPolicy(WitnessRule.COUNT_ONLY, true, null);
        assertEquals(null, find(AkadFormationChecker.check(c, f, countOnly), "WITNESS_NISHAB"));

        SyirkahContract three = contractWithWitnesses("F1", "F2", "F3");
        AkadFormation threeWomen = witnesses(new Witness("F1", WitnessSex.FEMALE),
                new Witness("F2", WitnessSex.FEMALE), new Witness("F3", WitnessSex.FEMALE));
        assertTrue(find(AkadFormationChecker.check(three, threeWomen, FormationPolicy.bookDefault()),
                "WITNESS_NISHAB") != null);
    }

    @Test
    void witnessesOfTheContractAndOfTheSignedAkadMustAgree() {
        AkadFormation f = witnesses(new Witness("X1", WitnessSex.MALE), new Witness("X2", WitnessSex.MALE));
        assertTrue(find(check(f), "WITNESS_MISMATCH") != null);
    }

    @Test
    void contractViolationsAreCarriedAsUnclassifiedFindings() {
        SyirkahContract oneWitness = contractWithWitnesses("W1");
        List<FormationFinding> found = AkadFormationChecker.check(oneWitness, complete(),
                FormationPolicy.bookDefault());
        assertEquals(AkadDefect.UNCLASSIFIED, find(found, "CONTRACT_WITNESSES").defect());
    }

    @Test
    void requirePassingListsEveryFinding() {
        AkadFormation f = with(c -> new AkadFormation(c.parties(), c.businessActivity(), c.lawfulnessReview(),
                Set.of("ijarah"), c.unresolvedAmbiguities(), false, c.ijabWithdrawn(), c.sectionsPresent(),
                c.signedPlace(), c.signedOn(), c.witnesses()));
        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class,
                () -> AkadFormationChecker.requirePassing(contract(), f, FormationPolicy.bookDefault()));
        assertTrue(e.getMessage().contains("MULTI_AKAD"));
        assertTrue(e.getMessage().contains("SESSION_NOT_UNIFIED"));
        assertFalse(AkadFormationChecker.check(contract(), f, FormationPolicy.bookDefault()).isEmpty());
    }

    @Test
    void theContractCanonicalFormIsUnchangedByFormationData() {
        // Formation facts live outside the contract, so checking never alters its hash input.
        SyirkahContract c = contract();
        String before = c.canonical();
        check(complete());
        assertEquals(before, c.canonical());
    }
}
