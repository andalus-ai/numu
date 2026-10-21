package tech.kayys.numou.numouadapter.persistence;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numoudomain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahJsonCodecTest {

    private static final Currency IDR = Currency.of("IDR");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final PartnerId OWNER = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId MANAGER = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    private static Money idr(String v) {
        return new Money(new BigDecimal(v), IDR);
    }

    private static SyirkahContract mudharabah() {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(OWNER, new BigDecimal("60"));
        n.put(MANAGER, new BigDecimal("40"));
        return new SyirkahContract(1, SyirkahType.MUDHARABAH, IDR,
                List.of(new Partner(OWNER, "Owner \"quoted\" é\n", PartnerRole.CAPITAL_PROVIDER, false),
                        new Partner(MANAGER, "Manager", PartnerRole.MANAGER, true)),
                List.of(new CapitalContribution(OWNER, CapitalKind.CASH, idr("100000000.00"), null)),
                ProfitSharingRatio.ofPercent(n), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                LocalDate.parse("2026-01-01"));
    }

    private static Syirkah history() {
        Syirkah s = Syirkah.open(SyirkahId.generate(), TenantId.generate(), mudharabah(), 2, T0);
        FinancialResult profit = FinancialResult.of(idr("30000000.00"), idr("5000000.00"), idr("0.00"), idr("0.00"));
        CloseSnapshot c1 = s.closePeriod(CloseId.generate(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                profit, ManagerFault.none(), ProfitDisposition.RETAIN, T0.plusSeconds(1));
        FinancialResult loss = FinancialResult.of(idr("0.00"), idr("12000000.00"), idr("0.00"), idr("0.00"));
        s.closePeriod(CloseId.generate(), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"), loss,
                ManagerFault.of(idr("2000000.00"), "negligent: \\ unsecured sale"), ProfitDisposition.DISTRIBUTE,
                T0.plusSeconds(2));
        // supersede the latest close so a REVERSED close is part of the history
        s.correctLatestClose(s.closes().get(1).id(), CloseId.generate(), loss,
                ManagerFault.of(idr("3000000.00"), "negligent"), ProfitDisposition.DISTRIBUTE, T0.plusSeconds(3));
        s.settleLiability(MANAGER, idr("3000000.00"), "settle-1", T0.plusSeconds(4));
        s.startLiquidation(T0.plusSeconds(4));
        s.liquidate(new LiquidationInput(idr("150000000.00"), idr("10000000.00"), true, LocalDate.parse("2026-03-31")),
                ManagerFault.none(), T0.plusSeconds(5));
        assertNotNull(c1);
        return s;
    }

    @Test
    void contractRoundTripsLosslessly() {
        SyirkahContract c = mudharabah();
        String json = SyirkahJsonCodec.encodeContract(c);
        SyirkahContract back = SyirkahJsonCodec.decodeContract(json);
        assertEquals(c, back);
        assertEquals(c.canonical(), back.canonical());
        assertEquals(json, SyirkahJsonCodec.encodeContract(back));
    }

    @Test
    void closesAndLiquidationRoundTripWithHashesIntact() {
        Syirkah.Snapshot snap = history().snapshot();
        assertTrue(snap.closes().stream().anyMatch(c -> c.status() == CloseStatus.REVERSED));
        assertNotNull(snap.liquidation());
        for (CloseSnapshot c : snap.closes()) {
            CloseSnapshot back = SyirkahJsonCodec.decodeClose(SyirkahJsonCodec.encodeClose(c));
            assertEquals(c, back);
        }
        LiquidationResult l = snap.liquidation();
        assertEquals(l, SyirkahJsonCodec.decodeLiquidation(SyirkahJsonCodec.encodeLiquidation(l)));
    }

    @Test
    void roundTrippedSnapshotRestoresAndVerifies() {
        Syirkah original = history();
        Syirkah.Snapshot a = original.snapshot();
        List<SyirkahContract> contracts = new ArrayList<>();
        a.contracts().forEach(c -> contracts.add(SyirkahJsonCodec.decodeContract(SyirkahJsonCodec.encodeContract(c))));
        List<CloseSnapshot> closes = new ArrayList<>();
        a.closes().forEach(c -> closes.add(SyirkahJsonCodec.decodeClose(SyirkahJsonCodec.encodeClose(c))));
        LiquidationResult liq = SyirkahJsonCodec.decodeLiquidation(SyirkahJsonCodec.encodeLiquidation(a.liquidation()));
        Syirkah.Snapshot b = new Syirkah.Snapshot(a.id(), a.tenantId(), a.scale(), a.status(), a.closedSinceRevision(),
                contracts, a.movements(), closes, liq, a.version(), a.createdAt(), a.updatedAt());
        assertEquals(a, b);
        Syirkah restored = Syirkah.restore(b); // re-verifies every hash chain
        assertEquals(a, restored.snapshot());
    }

    @Test
    void decodingIsStrict() {
        assertThrows(IllegalArgumentException.class, () -> SyirkahJsonCodec.decodeContract("{}"));
        assertThrows(IllegalArgumentException.class, () -> SyirkahJsonCodec.decodeContract("[1]"));
        assertThrows(IllegalArgumentException.class, () -> SyirkahJsonCodec.decodeContract("{\"a\":1} x"));
        assertThrows(IllegalArgumentException.class, () -> SyirkahJsonCodec.decodeClose("{\"id\":"));
    }

    @Test
    void jsonHandlesEscapesAndNumbers() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "a\"b\\c\n\t\u0001é");
        m.put("n", new BigDecimal("-12.3400"));
        m.put("l", List.of(true, false));
        m.put("z", null);
        assertEquals("{\"s\":\"a\\\"b\\\\c\\n\\t\\u0001é\",\"n\":-12.3400,\"l\":[true,false],\"z\":null}",
                Json.write(m));
        Object back = Json.read(Json.write(m));
        assertEquals(m.get("s"), ((Map<?, ?>) back).get("s"));
        assertEquals(0, new BigDecimal("-12.3400").compareTo((BigDecimal) ((Map<?, ?>) back).get("n")));
        assertEquals("-12.3400", ((BigDecimal) ((Map<?, ?>) back).get("n")).toPlainString());
    }

    @Test
    void approvalPayloadsRoundTripExactly() {
        TenantId t = TenantId.generate();
        SyirkahId id = SyirkahId.generate();
        FinancialResult fr = FinancialResult.of(idr("1000.50"), idr("10.00"), idr("0"), idr("0.5"));
        List<Object> payloads = List.of(
                new tech.kayys.numou.numouapplication.command.Commands.ReviseContract(t, id, mudharabah(),
                        new TreeSet<>(Set.of(OWNER, MANAGER))),
                new tech.kayys.numou.numouapplication.command.Commands.CorrectClose(t, id, CloseId.generate(),
                        CloseId.generate(), fr, ManagerFault.of(idr("5.00"), "why"), ProfitDisposition.RETAIN),
                new tech.kayys.numou.numouapplication.command.Commands.CorrectClose(t, id, CloseId.generate(),
                        CloseId.generate(), fr, ManagerFault.none(), ProfitDisposition.DISTRIBUTE),
                new tech.kayys.numou.numouapplication.command.Commands.StartLiquidation(t, id),
                new tech.kayys.numou.numouapplication.command.Commands.Liquidate(t, id,
                        new LiquidationInput(idr("9.00"), idr("1.00"), true, LocalDate.parse("2026-05-05")),
                        ManagerFault.none()));
        var kinds = List.of(tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.REVISE_CONTRACT,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.CORRECT_CLOSE,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.CORRECT_CLOSE,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.START_LIQUIDATION,
                tech.kayys.numou.numouapplication.governance.PendingApproval.Kind.LIQUIDATE);
        for (int i = 0; i < payloads.size(); i++) {
            String json = SyirkahJsonCodec.encodePayload(kinds.get(i), payloads.get(i));
            assertEquals(payloads.get(i), SyirkahJsonCodec.decodePayload(kinds.get(i), t, id, json), "payload " + i);
        }
    }

    @Test
    void eventsSerialiseGenerically() {
        SyirkahId id = SyirkahId.generate();
        var e = new SyirkahEvents.PeriodClosed(UUID.randomUUID(), T0, id, CloseId.generate(), idr("12.50"),
                ProfitDisposition.RETAIN, "abc");
        Map<String, Object> m = JdbcOutboxEventPublisher.toMap(e);
        assertEquals(id.value().toString(), m.get("syirkahId"));
        assertEquals("RETAIN", m.get("disposition"));
        assertEquals(T0.toString(), m.get("occurredAt"));
        String json = Json.write(m);
        assertTrue(json.contains("\"netResult\":{\"amount\":12.50,\"currency\":\"IDR\"}"), json);
    }
}
