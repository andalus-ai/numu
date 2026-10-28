package tech.kayys.numou.numouadapter.persistence;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.numoudomain.*;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AkadFormationCodecTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    @Test
    void aFullFormationRoundTripsExactly() {
        AkadFormation f = new AkadFormation(
                List.of(new PartyRecord(A, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true,
                                "I offer \"this\" syirkah\nand accept"),
                        new PartyRecord(B, null, null, false, false, false, null)),
                "Retail", new AkadFormation.LawfulnessReview("Sharia adviser", LocalDate.parse("2026-01-01"), true),
                Set.of("ijarah", "wakalah"), List.of("clause 4 is unclear"), true, false,
                EnumSet.allOf(AkadSection.class), "Jakarta", LocalDate.parse("2026-01-01"),
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.FEMALE)));
        assertEquals(f, SyirkahJsonCodec.decodeFormation(SyirkahJsonCodec.encodeFormation(f)));
    }

    @Test
    void aSparseFormationWithNullsRoundTrips() {
        AkadFormation f = new AkadFormation(List.of(), null, null, Set.of(), List.of(), false, true, Set.of(), null,
                null, List.of());
        assertEquals(f, SyirkahJsonCodec.decodeFormation(SyirkahJsonCodec.encodeFormation(f)));
    }

    @Test
    void policyRoundTripsWithAndWithoutMinimumAge() {
        for (FormationPolicy p : List.of(FormationPolicy.bookDefault(),
                new FormationPolicy(WitnessRule.COUNT_ONLY, false, 18))) {
            assertEquals(p, SyirkahJsonCodec.decodePolicy(SyirkahJsonCodec.encodePolicy(p)));
        }
    }
}
