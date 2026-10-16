package tech.kayys.syirkah.kiosk.application.api;

import tech.kayys.syirkah.kiosk.application.api.command.*;
import tech.kayys.syirkah.kiosk.application.api.query.*;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Public API for kiosk operations.
 */
public interface KioskService {

    // ============ Device Operations ============

    /**
     * Registers a new kiosk device.
     */
    CompletionStage<KioskId> registerKiosk(RegisterKioskCommand command);

    /**
     * Updates kiosk status.
     */
    CompletionStage<KioskId> updateKioskStatus(UpdateKioskStatusCommand command);

    /**
     * Gets kiosk status.
     */
    CompletionStage<KioskStatusView> getKioskStatus(KioskId kioskId);

    /**
     * Gets all kiosk devices.
     */
    CompletionStage<List<KioskStatusView>> getAllKiosks();

    // ============ Session Operations ============

    /**
     * Starts a new kiosk session.
     */
    CompletionStage<KioskSessionId> startSession(StartKioskSessionCommand command);

    /**
     * Adds an item to the kiosk session cart.
     */
    CompletionStage<KioskSessionId> addItemToSession(AddItemToSessionCommand command);

    /**
     * Adds a weighted item to the kiosk session cart.
     */
    CompletionStage<KioskSessionId> addWeightedItemToSession(AddWeightedItemToSessionCommand command);

    /**
     * Removes an item from the kiosk session cart.
     */
    CompletionStage<KioskSessionId> removeItemFromSession(RemoveItemFromSessionCommand command);

    /**
     * Starts checkout for a kiosk session.
     */
    CompletionStage<KioskSessionId> startCheckout(StartCheckoutCommand command);

    /**
     * Processes payment for a kiosk session.
     */
    CompletionStage<KioskSessionId> processPayment(ProcessKioskPaymentCommand command);

    /**
     * Ends a kiosk session.
     */
    CompletionStage<KioskSessionId> endSession(EndKioskSessionCommand command);

    /**
     * Requests assistance at a kiosk.
     */
    CompletionStage<KioskSessionId> requestAssistance(RequestAssistanceCommand command);

    /**
     * Verifies age at a kiosk.
     */
    CompletionStage<KioskSessionId> verifyAge(VerifyAgeCommand command);

    // ============ Session Queries ============

    /**
     * Gets session status.
     */
    CompletionStage<KioskSessionView> getSessionStatus(KioskSessionId sessionId);

    /**
     * Gets active sessions for a kiosk.
     */
    CompletionStage<List<KioskSessionView>> getActiveSessions(KioskId kioskId);

    /**
     * Gets session history.
     */
    CompletionStage<KioskSessionHistory> getSessionHistory(KioskSessionId sessionId);

    /**
     * Gets checkout summary for a session.
     */
    CompletionStage<CheckoutSummaryView> getCheckoutSummary(KioskSessionId sessionId);
}