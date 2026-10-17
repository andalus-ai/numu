package tech.kayys.numou.accounting.domain.islamic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahCalculatorTest {
    private final SyirkahCalculator calculator = new SyirkahCalculator();

    @Test
    void profitUsesAgreedNisbahAndLargestRemainder() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "1000", "60"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "40"));
        SyirkahCalculation result = calculator.calculate(contract, result("101", "0", "0", "0"));
        assertEquals(new BigDecimal("60.60"), allocation(result, "capital"));
        assertEquals(new BigDecimal("40.40"), allocation(result, "manager"));
        assertEquals(0, result.allocations().stream().map(SyirkahAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("101")));
    }

    @Test
    void ordinaryMudharabahLossIsAllocatedToCapitalProvidersOnly() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital-a", SyirkahPartyRole.CAPITAL_PROVIDER, "600", "50"),
                party("capital-b", SyirkahPartyRole.CAPITAL_PROVIDER, "400", "30"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "20"));
        SyirkahCalculation result = calculator.calculate(contract, result("0", "101", "0", "0"));
        assertEquals(new BigDecimal("60.60"), allocation(result, "capital-a"));
        assertEquals(new BigDecimal("40.40"), allocation(result, "capital-b"));
        assertEquals(new BigDecimal("0.00"), allocation(result, "manager"));
    }

    @Test
    void ordinaryMudharabahLossIncludesZeroAllocationForManagingPartner() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "1000", "60"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "40"));
        SyirkahCalculation result = calculator.calculate(contract, result("0", "0", "0", "100"));
        assertEquals(2, result.allocations().size());
        assertEquals(new BigDecimal("100.00"), allocation(result, "capital"));
        assertEquals(new BigDecimal("0.00"), allocation(result, "manager"));
    }

    @Test
    void abdanProfitUsesAgreedNisbah() {
        SyirkahContractVersion contract = contract(SyirkahType.ABDAN,
                party("a", SyirkahPartyRole.WORKING_PARTNER, "0", "40"),
                party("b", SyirkahPartyRole.WORKING_PARTNER, "0", "60"));
        SyirkahCalculation result = calculator.calculate(contract, result("100", "0", "0", "0"));
        assertEquals(new BigDecimal("40.00"), allocation(result, "a"));
        assertEquals(new BigDecimal("60.00"), allocation(result, "b"));
    }

    @Test
    void wujuhProfitUsesAgreedNisbah() {
        SyirkahContractVersion contract = contract(SyirkahType.WUJUH,
                party("a", SyirkahPartyRole.WORKING_PARTNER, "0", "25"),
                party("b", SyirkahPartyRole.WORKING_PARTNER, "0", "75"));
        SyirkahCalculation result = calculator.calculate(contract, result("100", "0", "0", "0"));
        assertEquals(new BigDecimal("25.00"), allocation(result, "a"));
        assertEquals(new BigDecimal("75.00"), allocation(result, "b"));
    }

    @Test
    void mufawadahProfitUsesAgreedNisbah() {
        SyirkahContractVersion contract = contract(SyirkahType.MUFAWADHAH,
                party("a", SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER, "600", "65"),
                party("b", SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER, "400", "35"));
        SyirkahCalculation result = calculator.calculate(contract, result("100", "0", "0", "0"));
        assertEquals(new BigDecimal("65.00"), allocation(result, "a"));
        assertEquals(new BigDecimal("35.00"), allocation(result, "b"));
    }

    @Test
    void musyarakahLossUsesCapitalContributions() {
        SyirkahContractVersion contract = contract(SyirkahType.MUSYARAKAH,
                party("a", SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER, "600", "50"),
                party("b", SyirkahPartyRole.CAPITAL_PROVIDER, "400", "50"));
        SyirkahCalculation result = calculator.calculate(contract, result("0", "0", "0", "101"));
        assertEquals(new BigDecimal("60.60"), allocation(result, "a"));
        assertEquals(new BigDecimal("40.40"), allocation(result, "b"));
    }

    @Test
    void breakEvenCreatesZeroAllocations() {
        SyirkahCalculation result = calculator.calculate(contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "100", "50"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "50")), result("100", "20", "30", "50"));
        assertEquals(0, result.netResult().signum());
        assertTrue(result.allocations().stream().allMatch(a -> a.amount().signum() == 0));
    }

    @Test
    void unsupportedLossPolicyFailsClosed() {
        SyirkahContractVersion contract = contract(SyirkahType.ABDAN,
                party("a", SyirkahPartyRole.WORKING_PARTNER, "0", "50"),
                party("b", SyirkahPartyRole.WORKING_PARTNER, "0", "50"));
        assertThrows(UnsupportedOperationException.class,
                () -> calculator.calculate(contract, result("0", "0", "10", "0")));
    }

    @Test
    void largestRemainderAllocationIsDeterministicForOneMinorUnit() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "100", "33.34"),
                party("manager-a", SyirkahPartyRole.MANAGING_PARTNER, "0", "33.33"),
                party("manager-b", SyirkahPartyRole.MANAGING_PARTNER, "0", "33.33"));
        SyirkahCalculation result = calculator.calculate(contract, result("0.01", "0", "0", "0"));
        assertEquals(new BigDecimal("0.01"), allocation(result, "capital"));
        assertEquals(new BigDecimal("0.00"), allocation(result, "manager-a"));
        assertEquals(new BigDecimal("0.00"), allocation(result, "manager-b"));
    }

    @Test
    void rejectsMudharabahWithoutPositiveProviderCapital() {
        assertThrows(IllegalArgumentException.class, () -> contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "0", "50"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "50")));
    }

    @Test
    void allocatesLargeAmountsWithoutLongOverflow() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "1000", "60"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "40"));
        SyirkahCalculation result = calculator.calculate(contract,
                result("1000000000000000000.00", "0", "0", "0"));
        assertEquals(new BigDecimal("600000000000000000.00"), allocation(result, "capital"));
        assertEquals(new BigDecimal("400000000000000000.00"), allocation(result, "manager"));
    }

    @Test
    void unsupportedPartnershipProfitPolicyFailsClosed() {
        SyirkahContractVersion contract = contract(SyirkahType.ABDAN,
                party("a", SyirkahPartyRole.WORKING_PARTNER, "0", "50"),
                party("b", SyirkahPartyRole.WORKING_PARTNER, "0", "50"));
        assertThrows(UnsupportedOperationException.class,
                () -> calculator.calculate(contract, result("100", "0", "0", "0")));
    }

    @Test
    void inanOrdinaryLossUsesCapitalContributionWeights() {
        SyirkahContractVersion contract = contract(SyirkahType.INAN,
                party("a", SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER, "700", "50"),
                party("b", SyirkahPartyRole.CAPITAL_AND_MANAGING_PARTNER, "300", "50"));
        SyirkahCalculation result = calculator.calculate(contract, result("0", "100", "0", "0"));
        assertEquals(new BigDecimal("70.00"), allocation(result, "a"));
        assertEquals(new BigDecimal("30.00"), allocation(result, "b"));
    }

    @Test
    void rejectsBusinessAmountsBeyondCurrencyMinorUnitPrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> result("1.001", "0", "0", "0"));
    }

    @Test
    void rejectsCapitalContributionBeyondCurrencyMinorUnitPrecision() {
        assertThrows(IllegalArgumentException.class, () -> contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "100.001", "50"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "50")));
    }

    @Test
    void calculationFingerprintIncludesPartyTerms() {
        SyirkahContractVersion first = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "1000", "60"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "40"));
        SyirkahContractVersion changedTerms = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "1000", "70"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "30"));
        assertNotEquals(calculator.calculate(first, result("100", "0", "0", "0")).calculationHash(),
                calculator.calculate(changedTerms, result("100", "0", "0", "0")).calculationHash());
    }

    @Test
    void rejectsNisbahThatDoesNotTotalOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> new SyirkahContractVersion("c", 1, "t", "co", "USD",
                SyirkahType.MUDHARABAH, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), "policy-1",
                List.of(party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "100", "50"),
                        party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "40"))));
    }

    @Test
    void rejectsCalculationForDifferentContractVersion() {
        SyirkahContractVersion contract = contract(SyirkahType.MUDHARABAH,
                party("capital", SyirkahPartyRole.CAPITAL_PROVIDER, "100", "50"),
                party("manager", SyirkahPartyRole.MANAGING_PARTNER, "0", "50"));
        SyirkahBusinessResult result = new SyirkahBusinessResult("r", "contract-1", 2,
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"),
                bd("100"), bd("0"), bd("0"), bd("0"), "USD", "evidence-1");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(contract, result));
    }

    private static BigDecimal allocation(SyirkahCalculation result, String partyId) {
        return result.allocations().stream().filter(a -> a.partyId().equals(partyId)).findFirst().orElseThrow().amount();
    }

    private static SyirkahContractVersion contract(SyirkahType type, SyirkahParty... parties) {
        return new SyirkahContractVersion("contract-1", 1, "tenant-1", "company-1", "USD", type,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), "policy-2026-1", List.of(parties));
    }

    private static SyirkahParty party(String id, SyirkahPartyRole role, String capital, String nisbah) {
        return new SyirkahParty(id, id, role, bd(capital), bd(nisbah));
    }

    private static SyirkahBusinessResult result(String revenue, String direct, String operating, String other) {
        return new SyirkahBusinessResult("result-1", "contract-1", 1,
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"),
                bd(revenue), bd(direct), bd(operating), bd(other), "USD", "evidence-1");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
