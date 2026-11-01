package tech.kayys.numou.notification.adapter.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class PushDeviceResourceHttpTest {
    @Inject
    NotificationHttpTestStore store;

    @BeforeEach
    void resetStore() {
        store.clear();
    }

    @Test
    void rejectsAnonymousDeviceManagementRequests() {
        given()
                .when().get("/api/v1/notifications/devices")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "notification-user")
    void registersListsAndRevokesWithoutExposingProviderToken() {
        String token = "private-browser-subscription-token";

        String deviceId = given()
                .contentType("application/json")
                .body("""
                        {
                          "token": "private-browser-subscription-token",
                          "platform": "WEB",
                          "appInstance": "browser-integration-test"
                        }
                        """)
                .when().post("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .body("platform", equalTo("WEB"))
                .extract().path("id");

        String refreshedId = given()
                .contentType("application/json")
                .body("""
                        {
                          "token": "private-browser-subscription-token",
                          "platform": "WEB",
                          "appInstance": "browser-integration-test"
                        }
                        """)
                .when().post("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .extract().path("id");
        org.junit.jupiter.api.Assertions.assertEquals(deviceId, refreshedId,
                "refreshing the same recipient token must preserve the device identity");

        String listing = given()
                .when().get("/api/v1/notifications/devices")
                .then()
                .statusCode(200)
                .extract().asString();

        assertFalse(listing.contains(token), "provider token must not be serialized by the REST adapter");
        org.junit.jupiter.api.Assertions.assertTrue(listing.contains(deviceId));

        given()
                .when().delete("/api/v1/notifications/devices/" + deviceId)
                .then().statusCode(200);

        given()
                .when().delete("/api/v1/notifications/devices/" + deviceId)
                .then().statusCode(404);

        given()
                .when().get("/api/v1/notifications/devices")
                .then().statusCode(200)
                .body(not(containsString(deviceId)));
    }
}
