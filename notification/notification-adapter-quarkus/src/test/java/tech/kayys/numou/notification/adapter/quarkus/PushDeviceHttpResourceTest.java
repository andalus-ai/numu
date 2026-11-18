package tech.kayys.numou.notification.adapter.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PushDeviceHttpResourceTest {
    @Test
    @TestSecurity(user = "notification-user")
    void registersListsAndRevokesThroughRealHttpBoundaryWithoutReturningSecrets() {
        String token = "web-push-private-subscription-payload";

        String deviceId = given()
                .contentType("application/json")
                .body("""
                        {
                          "token": "%s",
                          "platform": "WEB",
                          "appInstance": "browser-integration-test"
                        }
                        """.formatted(token))
                .when()
                .post("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("platform", is("WEB"))
                .body("appInstance", is("browser-integration-test"))
                .body(not(containsString(token)))
                .extract()
                .path("id");

        given()
                .when()
                .get("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].id", is(deviceId))
                .body("[0].platform", is("WEB"))
                .body(not(containsString(token)));

        given()
                .when()
                .delete("/api/v1/notifications/devices/" + deviceId)
                .then()
                .statusCode(200)
                .body("id", is(deviceId))
                .body(not(containsString(token)));

        given()
                .when()
                .get("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    @TestSecurity(user = "notification-user")
    void rejectsMalformedRegistrationAtHttpBoundary() {
        given()
                .contentType("application/json")
                .body("""
                        {"token":"  ","platform":"WEB","appInstance":"browser"}
                        """)
                .when()
                .post("/api/v1/notifications/devices")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "notification-user")
    void returnsNotFoundForDeviceOwnedByAnotherRecipientOrMissingDevice() {
        given()
                .when()
                .delete("/api/v1/notifications/devices/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }
}
