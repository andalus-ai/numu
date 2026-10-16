package tech.kayys.syirkah.kiosk.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.syirkah.kiosk.application.api.KioskService;
import tech.kayys.syirkah.kiosk.application.api.command.*;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;
import tech.kayys.syirkah.kiosk.domain.valueobject.*;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for kiosk operations.
 */
// Gated: KioskService has no implementation yet (application layer is
// scaffold only - interface plus DTOs, no handlers). Enabling this bean
// without a provider fails CDI validation at build time. Remove this gate
// once a KioskService implementation is registered as a CDI bean.
@IfBuildProperty(
        name = "syirkah.kiosk.api.enabled",
        stringValue = "false")
@Path("/api/v1/kiosks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Kiosk API", description = "Self-service kiosk operations")
public class KioskResource {

    @Inject
    KioskService kioskService;

    // ============ Kiosk Device Endpoints ============

    @POST
    @Operation(summary = "Register a new kiosk device")
    public CompletionStage<Response> registerKiosk(@Valid RegisterKioskRequest request) {
        RegisterKioskCommand command = RegisterKioskCommand.builder()
            .deviceName(request.getDeviceName())
            .model(request.getModel())
            .location(request.getLocation())
            .storeId(request.getStoreId())
            .mode(request.getMode())
            .cashAccepted(request.isCashAccepted())
            .cardAccepted(request.isCardAccepted())
            .mobilePaymentAccepted(request.isMobilePaymentAccepted())
            .build();

        return kioskService.registerKiosk(command)
            .thenApply(kioskId -> Response
                .created(URI.create("/api/v1/kiosks/" + kioskId.getValue()))
                .entity(new RegisterKioskResponse(kioskId))
                .build()
            );
    }

    @GET
    @Path("/{id}/status")
    @Operation(summary = "Get kiosk status")
    public CompletionStage<Response> getKioskStatus(@PathParam("id") UUID id) {
        KioskId kioskId = KioskId.of(id);
        return kioskService.getKioskStatus(kioskId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @GET
    @Operation(summary = "Get all kiosks")
    public CompletionStage<Response> getAllKiosks() {
        return kioskService.getAllKiosks()
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @PATCH
    @Path("/{id}/status")
    @Operation(summary = "Update kiosk status")
    public CompletionStage<Response> updateKioskStatus(
            @PathParam("id") UUID id,
            @Valid UpdateKioskStatusRequest request) {
        KioskId kioskId = KioskId.of(id);
        UpdateKioskStatusCommand command = new UpdateKioskStatusCommand(
            kioskId,
            request.getStatus(),
            request.getNotes()
        );
        return kioskService.updateKioskStatus(command)
            .thenApply(response -> Response.ok().build());
    }

    // ============ Session Endpoints ============

    @POST
    @Path("/sessions")
    @Operation(summary = "Start a kiosk session")
    public CompletionStage<Response> startSession(@Valid StartKioskSessionRequest request) {
        StartKioskSessionCommand command = StartKioskSessionCommand.builder()
            .kioskId(KioskId.of(request.getKioskId()))
            .language(request.getLanguage())
            .currencyCode(request.getCurrencyCode())
            .customerId(request.getCustomerId())
            .build();

        return kioskService.startSession(command)
            .thenApply(sessionId -> Response
                .ok(new StartSessionResponse(sessionId))
                .build()
            );
    }

    @POST
    @Path("/sessions/{sessionId}/items")
    @Operation(summary = "Add item to session cart")
    public CompletionStage<Response> addItemToSession(
            @PathParam("sessionId") UUID sessionId,
            @Valid AddItemRequest request) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        AddItemToSessionCommand command = new AddItemToSessionCommand(
            kioskSessionId,
            request.getProductId(),
            request.getQuantity(),
            request.getVariationId()
        );
        return kioskService.addItemToSession(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/sessions/{sessionId}/weighted-items")
    @Operation(summary = "Add weighted item to session cart")
    public CompletionStage<Response> addWeightedItemToSession(
            @PathParam("sessionId") UUID sessionId,
            @Valid AddWeightedItemRequest request) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        AddWeightedItemToSessionCommand command = new AddWeightedItemToSessionCommand(
            kioskSessionId,
            request.getGroceryProductId(),
            request.getScaleId(),
            request.getWeight(),
            request.getWeightUnit()
        );
        return kioskService.addWeightedItemToSession(command)
            .thenApply(response -> Response.ok().build());
    }

    @DELETE
    @Path("/sessions/{sessionId}/items/{itemId}")
    @Operation(summary = "Remove item from session cart")
    public CompletionStage<Response> removeItemFromSession(
            @PathParam("sessionId") UUID sessionId,
            @PathParam("itemId") String itemId) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        RemoveItemFromSessionCommand command = new RemoveItemFromSessionCommand(
            kioskSessionId,
            itemId
        );
        return kioskService.removeItemFromSession(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/sessions/{sessionId}/checkout")
    @Operation(summary = "Start checkout process")
    public CompletionStage<Response> startCheckout(@PathParam("sessionId") UUID sessionId) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        StartCheckoutCommand command = new StartCheckoutCommand(kioskSessionId);
        return kioskService.startCheckout(command)
            .thenCompose(response -> kioskService.getCheckoutSummary(kioskSessionId))
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    @POST
    @Path("/sessions/{sessionId}/payments")
    @Operation(summary = "Process payment")
    public CompletionStage<Response> processPayment(
            @PathParam("sessionId") UUID sessionId,
            @Valid ProcessPaymentRequest request) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        ProcessKioskPaymentCommand command = new ProcessKioskPaymentCommand(
            kioskSessionId,
            request.getPaymentMethod(),
            request.getAmount(),
            request.getCurrencyCode(),
            request.getCardToken(),
            request.getTransactionId()
        );
        return kioskService.processPayment(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/sessions/{sessionId}/assistance")
    @Operation(summary = "Request assistance")
    public CompletionStage<Response> requestAssistance(
            @PathParam("sessionId") UUID sessionId,
            @Valid RequestAssistanceRequest request) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        RequestAssistanceCommand command = new RequestAssistanceCommand(
            kioskSessionId,
            request.getReason()
        );
        return kioskService.requestAssistance(command)
            .thenApply(response -> Response.ok().build());
    }

    @POST
    @Path("/sessions/{sessionId}/verify-age")
    @Operation(summary = "Verify age for restricted items")
    public CompletionStage<Response> verifyAge(
            @PathParam("sessionId") UUID sessionId,
            @Valid VerifyAgeRequest request) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        VerifyAgeCommand command = new VerifyAgeCommand(
            kioskSessionId,
            request.isVerified(),
            request.getVerifiedBy()
        );
        return kioskService.verifyAge(command)
            .thenApply(response -> Response.ok().build());
    }

    @DELETE
    @Path("/sessions/{sessionId}")
    @Operation(summary = "End kiosk session")
    public CompletionStage<Response> endSession(@PathParam("sessionId") UUID sessionId) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        EndKioskSessionCommand command = new EndKioskSessionCommand(kioskSessionId);
        return kioskService.endSession(command)
            .thenApply(response -> Response.ok().build());
    }

    @GET
    @Path("/sessions/{sessionId}")
    @Operation(summary = "Get session status")
    public CompletionStage<Response> getSessionStatus(@PathParam("sessionId") UUID sessionId) {
        KioskSessionId kioskSessionId = KioskSessionId.of(sessionId);
        return kioskService.getSessionStatus(kioskSessionId)
            .thenApply(Response::ok)
            .thenApply(Response.ResponseBuilder::build);
    }

    // ============ Request/Response DTOs ============

    public static class RegisterKioskRequest {
        private String deviceName;
        private String model;
        private String location;
        private String storeId;
        private KioskMode mode;
        private boolean cashAccepted = true;
        private boolean cardAccepted = true;
        private boolean mobilePaymentAccepted = true;

        // Getters and setters
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getStoreId() { return storeId; }
        public void setStoreId(String storeId) { this.storeId = storeId; }
        public KioskMode getMode() { return mode; }
        public void setMode(KioskMode mode) { this.mode = mode; }
        public boolean isCashAccepted() { return cashAccepted; }
        public void setCashAccepted(boolean cashAccepted) { this.cashAccepted = cashAccepted; }
        public boolean isCardAccepted() { return cardAccepted; }
        public void setCardAccepted(boolean cardAccepted) { this.cardAccepted = cardAccepted; }
        public boolean isMobilePaymentAccepted() { return mobilePaymentAccepted; }
        public void setMobilePaymentAccepted(boolean mobilePaymentAccepted) { this.mobilePaymentAccepted = mobilePaymentAccepted; }
    }

    public static class RegisterKioskResponse {
        private final KioskId kioskId;

        public RegisterKioskResponse(KioskId kioskId) {
            this.kioskId = kioskId;
        }

        public UUID getKioskId() {
            return kioskId.getValue();
        }
    }

    public static class UpdateKioskStatusRequest {
        private KioskStatus status;
        private String notes;

        public KioskStatus getStatus() { return status; }
        public void setStatus(KioskStatus status) { this.status = status; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class StartKioskSessionRequest {
        private UUID kioskId;
        private String language;
        private String currencyCode;
        private String customerId;

        public UUID getKioskId() { return kioskId; }
        public void setKioskId(UUID kioskId) { this.kioskId = kioskId; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
    }

    public static class StartSessionResponse {
        private final KioskSessionId sessionId;

        public StartSessionResponse(KioskSessionId sessionId) {
            this.sessionId = sessionId;
        }

        public UUID getSessionId() {
            return sessionId.getValue();
        }
    }

    public static class AddItemRequest {
        private UUID productId;
        private int quantity = 1;
        private String variationId;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getVariationId() { return variationId; }
        public void setVariationId(String variationId) { this.variationId = variationId; }
    }

    public static class AddWeightedItemRequest {
        private UUID groceryProductId;
        private UUID scaleId;
        private double weight;
        private String weightUnit;

        public UUID getGroceryProductId() { return groceryProductId; }
        public void setGroceryProductId(UUID groceryProductId) { this.groceryProductId = groceryProductId; }
        public UUID getScaleId() { return scaleId; }
        public void setScaleId(UUID scaleId) { this.scaleId = scaleId; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public String getWeightUnit() { return weightUnit; }
        public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }
    }

    public static class ProcessPaymentRequest {
        private String paymentMethod;
        private String amount;
        private String currencyCode;
        private String cardToken;
        private String transactionId;

        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getAmount() { return amount; }
        public void setAmount(String amount) { this.amount = amount; }
        public String getCurrencyCode() { return currencyCode; }
        public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
        public String getCardToken() { return cardToken; }
        public void setCardToken(String cardToken) { this.cardToken = cardToken; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    }

    public static class RequestAssistanceRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class VerifyAgeRequest {
        private boolean verified;
        private String verifiedBy;

        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        public String getVerifiedBy() { return verifiedBy; }
        public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    }
}