package tech.kayys.numou.notification.adapter.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class PushDeviceUnauthenticatedHttpTest {
    @Test
    void rejectsAnonymousDeviceManagementRequests() {
        given()
                .when()
                .get("/api/v1/notifications/devices")
                .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .body("""
                        {"token":"secret","platform":"WEB","appInstance":"browser"}
                        """)
                .when()
                .post("/api/v1/notifications/devices")
                .then()
                .statusCode(401);
    }
}
