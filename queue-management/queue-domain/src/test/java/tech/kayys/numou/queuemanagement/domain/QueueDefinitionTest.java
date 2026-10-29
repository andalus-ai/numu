package tech.kayys.numou.queuemanagement.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class QueueDefinitionTest {
    @Test
    void normalizesCodeAndEnforcesPauseResumeCloseLifecycle() {
        QueueDefinition queue = QueueDefinition.create(QueueId.generate(), TenantId.generate(),
                " front-desk ", " Front desk ", QueueProfile.WAITING_SERVICE, QueueOrderingPolicy.FIFO);

        assertThat(queue.code()).isEqualTo("FRONT-DESK");
        assertThat(queue.name()).isEqualTo("Front desk");
        assertThat(queue.acceptsEntries()).isTrue();

        queue.pause();
        assertThat(queue.status()).isEqualTo(QueueStatus.PAUSED);
        assertThat(queue.acceptsEntries()).isFalse();
        assertThatIllegalStateException().isThrownBy(queue::pause);

        queue.resume();
        queue.close();
        assertThat(queue.status()).isEqualTo(QueueStatus.CLOSED);
        assertThatIllegalStateException().isThrownBy(() -> queue.rename("Renamed"));
        assertThatIllegalStateException().isThrownBy(queue::resume);
    }

    @Test
    void rejectsInvalidCodesAndNames() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                QueueDefinition.create(QueueId.generate(), TenantId.generate(), "invalid code",
                        "Queue", QueueProfile.DISPATCH, QueueOrderingPolicy.FIFO));
        assertThatIllegalArgumentException().isThrownBy(() ->
                QueueDefinition.create(QueueId.generate(), TenantId.generate(), "Q1",
                        " ", QueueProfile.DISPATCH, QueueOrderingPolicy.FIFO));
    }
}
