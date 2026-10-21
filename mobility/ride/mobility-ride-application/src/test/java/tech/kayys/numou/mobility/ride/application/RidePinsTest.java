package tech.kayys.numou.mobility.ride.application;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RidePinsTest {

    static final byte[] A = "0123456789abcdef0123456789abcdef-pin-secret-A".getBytes();
    static final byte[] B = "fedcba9876543210fedcba9876543210-pin-secret-B".getBytes();

    @Test
    void aPinIsFourDigitsStablePerRideAndDependsOnTheSecret() {
        final var id = UUID.randomUUID();
        final var pins = new RidePins(A);
        assertThat(pins.pinFor(id)).matches("\\d{4}").isEqualTo(pins.pinFor(id));
        final var distinct = new HashSet<String>();
        for (int i = 0; i < 200; i++) distinct.add(pins.pinFor(UUID.randomUUID()));
        assertThat(distinct.size()).isGreaterThan(150);                                  // well spread over the 10,000 possibilities
        int changed = 0;
        for (int i = 0; i < 200; i++) {
            final var r = UUID.randomUUID();
            if (!pins.pinFor(r).equals(new RidePins(B).pinFor(r))) changed++;
        }
        assertThat(changed).isGreaterThan(190);                                          // a different secret yields different PINs
    }

    @Test
    void weakSecretsAreRefused() {
        assertThatThrownBy(() -> new RidePins("short".getBytes())).isInstanceOf(IllegalArgumentException.class);
    }
}
