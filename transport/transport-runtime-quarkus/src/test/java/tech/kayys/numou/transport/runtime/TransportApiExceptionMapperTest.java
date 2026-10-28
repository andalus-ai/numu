package tech.kayys.numou.transport.runtime;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransportApiExceptionMapperTest {
    private final TransportApiExceptionMapper mapper = new TransportApiExceptionMapper();

    @Test
    void mapsMissingTrustedIdentityToForbiddenWithoutSensitiveDetails() {
        Response response = mapper.toResponse(new SecurityException("tenant-SECRET-123 is missing"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getEntity()).isEqualTo(
                new TransportApiExceptionMapper.ApiError(
                        "trusted_tenant_context_required",
                        "Authenticated tenant and actor context is required."));
        assertThat(response.getEntity().toString()).doesNotContain("SECRET");
    }

    @Test
    void mapsSecurityProviderOutageToRetryableServiceUnavailable() {
        Response response = mapper.toResponse(
                new TransportAuthorizationUnavailableException("policy provider SECRET is down"));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getEntity()).isEqualTo(
                new TransportApiExceptionMapper.ApiError(
                        "authorization_unavailable",
                        "Tenant membership or authorization service is temporarily unavailable."));
        assertThat(response.getEntity().toString()).doesNotContain("SECRET");
    }

    @Test
    void preservesExplicitHttpClientErrors() {
        BadRequestException failure = new BadRequestException("invalid request");
        Response response = mapper.toResponse(failure);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void mapsInvalidLifecycleTransitionToConflict() {
        Response response = mapper.toResponse(new IllegalStateException("cannot confirm released hold"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getEntity()).isEqualTo(
                new TransportApiExceptionMapper.ApiError(
                        "booking_state_conflict",
                        "The booking cannot make that state transition."));
    }

    @Test
    void hidesUnexpectedFailureDetailsFromClient() {
        Response response = mapper.toResponse(new RuntimeException("db-password-SECRET"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getEntity()).isEqualTo(
                new TransportApiExceptionMapper.ApiError(
                        "internal_error",
                        "The request could not be completed."));
        assertThat(response.getEntity().toString()).doesNotContain("SECRET");
    }
}
