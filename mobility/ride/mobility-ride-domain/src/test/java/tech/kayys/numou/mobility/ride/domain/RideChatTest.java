package tech.kayys.numou.mobility.ride.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.kernel.domain.RideId;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RideChatTest {

    static final Instant T = Instant.parse("2026-10-10T10:00:00Z");

    @Test
    void phoneNumbersEmailsAndLinksAreHiddenButDirectionsAreKept() {
        assertThat(ChatFilter.sanitize("call me on 0812-3456-7890 please")).isEqualTo("call me on [number hidden] please");
        assertThat(ChatFilter.sanitize("wa +62 812 3456 7890")).isEqualTo("wa [number hidden]");
        assertThat(ChatFilter.sanitize("mail budi.s@example.com now")).isEqualTo("mail [email hidden] now");
        assertThat(ChatFilter.sanitize("pay here https://pay.example.com/x?id=1")).isEqualTo("pay here [link hidden]");
        assertThat(ChatFilter.sanitize("I am at gate 3, blue car, plate B 1234 XYZ, floor 12")).isEqualTo("I am at gate 3, blue car, plate B 1234 XYZ, floor 12");
        assertThat(ChatFilter.sanitize("arrive in 15 minutes")).isEqualTo("arrive in 15 minutes");
    }

    @Test
    void messagesAreBoundedAndRateLimitedPerSender() {
        final var c = RideChat.open(RideId.generate(), T);
        c.send(RideChat.Sender.RIDER, "hello", T);
        assertThatThrownBy(() -> c.send(RideChat.Sender.RIDER, "again", T.plusMillis(500))).hasMessageContaining("too quickly");
        c.send(RideChat.Sender.DRIVER, "hi", T.plusMillis(500));                              // the other party is not throttled by the first
        c.send(RideChat.Sender.RIDER, "again", T.plusSeconds(2));
        assertThatThrownBy(() -> c.send(RideChat.Sender.RIDER, " ", T.plusSeconds(5))).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> c.send(RideChat.Sender.RIDER, "x".repeat(501), T.plusSeconds(5))).hasMessageContaining("too long");
        final var full = RideChat.open(RideId.generate(), T);
        for (int i = 0; i < RideChat.MAX_MESSAGES; i++) full.send(RideChat.Sender.RIDER, "m" + i, T.plusSeconds(i * 2L));
        assertThatThrownBy(() -> full.send(RideChat.Sender.RIDER, "one more", T.plusSeconds(10_000))).hasMessageContaining("limit");
    }

    @Test
    void eventsNeverContainTheMessageText() {
        final var c = RideChat.open(RideId.generate(), T);
        c.send(RideChat.Sender.RIDER, "my secret gate code is open sesame", T);
        assertThat(c.pullDomainEvents().toString()).doesNotContain("sesame", "secret");
    }

    @Test
    void snapshotRoundTrip() {
        final var c = RideChat.open(RideId.generate(), T);
        c.send(RideChat.Sender.RIDER, "hello", T); c.send(RideChat.Sender.DRIVER, "hi", T.plusSeconds(3));
        assertThat(RideChat.restore(c.snapshot()).snapshot()).isEqualTo(c.snapshot());
    }
}
