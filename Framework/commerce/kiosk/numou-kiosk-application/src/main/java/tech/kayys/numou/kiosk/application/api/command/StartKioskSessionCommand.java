package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskId;
import tech.kayys.syirkah.kiosk.domain.identifier.KioskSessionId;

import java.util.UUID;

/**
 * Command to start a kiosk session.
 */
public record StartKioskSessionCommand(
        KioskSessionId kioskSessionId,
        KioskId kioskId,
        String language,
        String currencyCode,
        String customerId // Optional customer ID (null = guest)
) implements Command {

    public StartKioskSessionCommand {
        if (kioskId == null) {
            throw new IllegalArgumentException("Kiosk ID cannot be null");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private KioskSessionId kioskSessionId;
        private KioskId kioskId;
        private String language = "en";
        private String currencyCode = "USD";
        private String customerId;

        public Builder kioskSessionId(KioskSessionId kioskSessionId) {
            this.kioskSessionId = kioskSessionId;
            return this;
        }

        public Builder kioskId(KioskId kioskId) {
            this.kioskId = kioskId;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public StartKioskSessionCommand build() {
            if (kioskSessionId == null) {
                kioskSessionId = KioskSessionId.generate();
            }
            return new StartKioskSessionCommand(
                kioskSessionId, kioskId, language, currencyCode, customerId
            );
        }
    }
}