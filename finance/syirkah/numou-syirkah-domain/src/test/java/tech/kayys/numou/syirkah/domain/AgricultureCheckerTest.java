package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.A;
import static tech.kayys.numou.numoudomain.Fixtures.B;
import static tech.kayys.numou.numoudomain.Fixtures.inan;
import static tech.kayys.numou.numoudomain.Fixtures.ratio;

class AgricultureCheckerTest {

    private static final LocalDate SIGNED = LocalDate.parse("2026-01-01");

    @Test
    void muzaraahAndMukhabarahAreBarred() {
        for (var k : List.of(AgricultureChecker.Kind.MUZARAAH, AgricultureChecker.Kind.MUKHABARAH)) {
            var f = AgricultureChecker.check(new AgricultureChecker.Arrangement(k, true));
            assertEquals("BARRED_AGRICULTURE_AKAD", f.get(0).code());
            assertEquals(AkadDefect.BATIL, f.get(0).defect());
        }
    }

    @Test
    void musaqahIsAllowedOnlyOnLandThatAlreadyHasTrees() {
        assertEquals(List.of(), AgricultureChecker.check(
                new AgricultureChecker.Arrangement(AgricultureChecker.Kind.MUSAQAH, true)));
        assertEquals("MUSAQAH_NOT_PLANTED", AgricultureChecker.check(
                new AgricultureChecker.Arrangement(AgricultureChecker.Kind.MUSAQAH, false)).get(0).code());
    }

    @Test
    void scanRecognisesSpellingsAndIgnoresMusaqah() {
        assertEquals(1, AgricultureChecker.scan("Muzara'ah of rice fields", Set.of()).size());
        assertEquals(1, AgricultureChecker.scan("Farming", Set.of("Mukhabarah")).size());
        assertEquals(2, AgricultureChecker.scan("muzaraah", Set.of("mukhabarah")).size());
        assertEquals(0, AgricultureChecker.scan("Musaqah of an orchard", Set.of()).size());
        assertEquals(0, AgricultureChecker.scan(null, null).size());
    }

    @Test
    void formationCheckBlocksABarredAgricultureAkad() {
        SyirkahContract contract = inan(100_000_000, 0, ratio(A, 40, B, 60));
        AkadFormation f = new AkadFormation(
                List.of(new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true, "I accept"),
                        new PartyRecord(B, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true, "I accept")),
                "Muzara'ah of rice fields", new AkadFormation.LawfulnessReview("Adviser", SIGNED, true), Set.of(),
                List.of(), true, false, EnumSet.allOf(AkadSection.class), "Jakarta", SIGNED,
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE)));
        var findings = AkadFormationChecker.check(contract, f, FormationPolicy.bookDefault());
        assertTrue(findings.stream().anyMatch(x -> x.code().equals("BARRED_AGRICULTURE_AKAD")), findings.toString());
    }
}
