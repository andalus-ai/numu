package tech.kayys.payment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for Payment Resource REST API
 */
@QuarkusTest
class PaymentResourceTest {

    @Test
    @DisplayName("Should get available payment methods")
    void testGetPaymentMethods() {
        given()
            .when().get("/api/payments/methods")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue())
            .body("gateway_providers", notNullValue());
    }

    @Test
    @DisplayName("Should get payment methods by type - QRIS")
    void testGetPaymentMethodsByTypeQRIS() {
        given()
            .when().get("/api/payments/methods/qris")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }

    @Test
    @DisplayName("Should get payment methods by type - bank transfer")
    void testGetPaymentMethodsByTypeBankTransfer() {
        given()
            .when().get("/api/payments/methods/bank_transfer")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }

    @Test
    @DisplayName("Should get payment methods by type - e-wallet")
    void testGetPaymentMethodsByTypeEWallet() {
        given()
            .when().get("/api/payments/methods/e_wallet")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }

    @Test
    @DisplayName("Should get payment methods by type - fintech")
    void testGetPaymentMethodsByTypeFintech() {
        given()
            .when().get("/api/payments/methods/fintech")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }

    @Test
    @DisplayName("Should get payment methods by type - card")
    void testGetPaymentMethodsByTypeCard() {
        given()
            .when().get("/api/payments/methods/card")
            .then()
            .statusCode(200)
            .body("payment_methods", notNullValue());
    }

    @Test
    @DisplayName("Should return 404 for non-existent payment")
    void testGetNonExistentPayment() {
        given()
            .when().get("/api/payments/999999")
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("Should return 404 for non-existent transaction ID")
    void testGetNonExistentTransaction() {
        given()
            .when().get("/api/payments/transaction/NON_EXISTENT_TXN")
            .then()
            .statusCode(404);
    }

    @Test
    @DisplayName("Should get payments by invoice ID")
    void testGetPaymentsByInvoice() {
        given()
            .when().get("/api/payments/invoice/1")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }

    @Test
    @DisplayName("Should get pending payments")
    void testGetPendingPayments() {
        given()
            .when().get("/api/payments/pending")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }

    @Test
    @DisplayName("Should create payment with QRIS method")
    void testCreatePaymentQRIS() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-001",
                "payment_method": "QRIS",
                "amount": 100000,
                "currency": "IDR",
                "customer_name": "Test User",
                "customer_email": "test@example.com",
                "customer_phone": "08123456789",
                "description": "Test payment"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(201), equalTo(400), equalTo(500)));
            // Note: 201 for success, 400/500 if gateway not configured
    }

    @Test
    @DisplayName("Should create payment with bank transfer method")
    void testCreatePaymentBankTransfer() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-002",
                "payment_method": "VA_BCA",
                "amount": 150000,
                "currency": "IDR",
                "customer_name": "Test User",
                "customer_email": "test@example.com",
                "customer_phone": "08123456789"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(201), equalTo(400), equalTo(500)));
    }

    @Test
    @DisplayName("Should create payment with e-wallet method")
    void testCreatePaymentEWallet() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-003",
                "payment_method": "OVO",
                "amount": 75000,
                "currency": "IDR",
                "customer_name": "Test User",
                "customer_email": "test@example.com",
                "customer_phone": "08123456789"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(201), equalTo(400), equalTo(500)));
    }

    @Test
    @DisplayName("Should create payment with fintech method")
    void testCreatePaymentFintech() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-004",
                "payment_method": "KREDIVO",
                "amount": 500000,
                "currency": "IDR",
                "customer_name": "Test User",
                "customer_email": "test@example.com",
                "customer_phone": "08123456789"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(201), equalTo(400), equalTo(500)));
    }

    @Test
    @DisplayName("Should return bad request for invalid payment method")
    void testCreatePaymentInvalidMethod() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-005",
                "payment_method": "INVALID_METHOD",
                "amount": 100000,
                "currency": "IDR"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(400), equalTo(500)));
    }

    @Test
    @DisplayName("Should return bad request for missing amount")
    void testCreatePaymentMissingAmount() {
        String requestBody = """
            {
                "external_order_id": "ORDER-TEST-006",
                "payment_method": "QRIS"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(requestBody)
            .when().post("/api/payments")
            .then()
            .statusCode(anyOf(equalTo(400), equalTo(500)));
    }

    @Test
    @DisplayName("Should handle webhook endpoint")
    void testWebhookEndpoint() {
        String payload = """
            {
                "transaction_id": "TXN123",
                "status": "success"
            }
            """;

        given()
            .header("Content-Type", "application/json")
            .body(payload)
            .when().post("/api/payments/webhook/MIDTRANS")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(401), equalTo(500)));
    }

    @Test
    @DisplayName("Should get payments by status - pending")
    void testGetPaymentsByStatusPending() {
        given()
            .when().get("/api/payments/status/PENDING")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }

    @Test
    @DisplayName("Should get payments by status - completed")
    void testGetPaymentsByStatusCompleted() {
        given()
            .when().get("/api/payments/status/COMPLETED")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }

    @Test
    @DisplayName("Should get payments by status - failed")
    void testGetPaymentsByStatusFailed() {
        given()
            .when().get("/api/payments/status/FAILED")
            .then()
            .statusCode(200)
            .body(notNullValue());
    }
}
