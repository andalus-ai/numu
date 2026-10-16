package tech.kayys.syirkah.accounting.application.legal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.legal.LegalContract;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LegalService")
class LegalServiceTest {

    private LegalService svc;

    @BeforeEach
    void setUp() {
        svc = new LegalService();
    }

    @Test
    @DisplayName("registerContract returns DRAFT contract")
    void registerContract_draft() {
        var contract = svc.registerContract("CTR-001", "Vendor Supply Agreement",
                "VENDOR-42", LocalDate.now(), LocalDate.now().plusYears(1));
        assertNotNull(contract);
        assertEquals(LegalContract.Status.DRAFT, contract.status());
        assertEquals("CTR-001", contract.contractId());
    }

    @Test
    @DisplayName("activate contract transitions to ACTIVE")
    void activateContract() {
        var contract = svc.registerContract("CTR-002", "Service Level Agreement",
                "VENDOR-05", LocalDate.now(), LocalDate.now().plusMonths(6));
        contract.activate();
        assertEquals(LegalContract.Status.ACTIVE, contract.status());
    }

    @Test
    @DisplayName("terminate active contract transitions to TERMINATED")
    void terminateContract() {
        var contract = svc.registerContract("CTR-003", "NDA",
                "PARTNER-01", LocalDate.now(), LocalDate.now().plusYears(2));
        contract.activate();
        contract.terminate();
        assertEquals(LegalContract.Status.TERMINATED, contract.status());
    }

    @Test
    @DisplayName("terminate draft contract throws")
    void terminateDraft_throws() {
        var contract = svc.registerContract("CTR-004", "Draft Agreement",
                "PARTNER-02", LocalDate.now(), LocalDate.now().plusYears(1));
        assertThrows(IllegalStateException.class, contract::terminate);
    }

    @Test
    @DisplayName("addObligation attaches obligation to contract")
    void addObligation() {
        var contract = svc.registerContract("CTR-005", "Supply Agreement",
                "VENDOR-99", LocalDate.now(), LocalDate.now().plusYears(1));
        contract.activate();
        var obligation = svc.addObligation(contract.contractId(),
                "Monthly delivery report", LocalDate.now().plusMonths(1));
        assertNotNull(obligation);
        assertEquals("CTR-005", obligation.contractId());
        assertEquals("Monthly delivery report", obligation.description());
    }

    @Test
    @DisplayName("addObligation to terminated contract throws")
    void addObligation_terminated_throws() {
        var contract = svc.registerContract("CTR-006", "Old Agreement",
                "VENDOR-10", LocalDate.now(), LocalDate.now().plusMonths(3));
        contract.activate();
        contract.terminate();
        assertThrows(IllegalStateException.class,
                () -> svc.addObligation("CTR-006", "Post-term obligation", LocalDate.now()));
    }

    @Test
    @DisplayName("getContract throws for unknown ID")
    void getContract_notFound() {
        assertThrows(IllegalArgumentException.class, () -> svc.getContract("MISSING"));
    }
}
