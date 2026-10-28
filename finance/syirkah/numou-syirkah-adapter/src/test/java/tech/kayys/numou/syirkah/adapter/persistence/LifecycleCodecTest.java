package tech.kayys.numou.numouadapter.persistence;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.numoudomain.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleCodecTest {
    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");
    private static final PartnerId C = PartnerId.of("00000000-0000-0000-0000-00000000000c");
    private static final LocalDate D = LocalDate.parse("2026-02-01");

    @Test
    void roundTripsEveryField() {
        LifecycleChange ch = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), D,
                List.of(new LifecycleConsent(A, true, D, "sig-a"), new LifecycleConsent(C, false, D, "refused")),
                ProfitDisposition.RETAIN, List.of(new SyufahResponse(A, false, D)),
                List.of(new Succession(B, C, true, D, "heirship-c")));
        assertEquals(ch, SyirkahJsonCodec.decodeLifecycle(SyirkahJsonCodec.encodeLifecycle(ch)));
    }

    @Test
    void roundTripsMinimal() {
        LifecycleChange ch = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), D, List.of(), null, List.of());
        assertEquals(ch, SyirkahJsonCodec.decodeLifecycle(SyirkahJsonCodec.encodeLifecycle(ch)));
    }
}
