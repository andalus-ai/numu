package tech.kayys.syirkah.kiosk.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.kiosk.domain.valueobject.KioskMode;

public record RegisterKioskCommand(
        String deviceName,
        String model,
        String location,
        String storeId,
        KioskMode mode,
        boolean cashAccepted,
        boolean cardAccepted,
        boolean mobilePaymentAccepted
) implements Command {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String deviceName;
        private String model;
        private String location;
        private String storeId;
        private KioskMode mode;
        private boolean cashAccepted = true;
        private boolean cardAccepted = true;
        private boolean mobilePaymentAccepted = true;

        public Builder deviceName(String deviceName) { this.deviceName = deviceName; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder storeId(String storeId) { this.storeId = storeId; return this; }
        public Builder mode(KioskMode mode) { this.mode = mode; return this; }
        public Builder cashAccepted(boolean cashAccepted) { this.cashAccepted = cashAccepted; return this; }
        public Builder cardAccepted(boolean cardAccepted) { this.cardAccepted = cardAccepted; return this; }
        public Builder mobilePaymentAccepted(boolean mobilePaymentAccepted) { this.mobilePaymentAccepted = mobilePaymentAccepted; return this; }

        public RegisterKioskCommand build() {
            return new RegisterKioskCommand(
                deviceName, model, location, storeId, mode, cashAccepted, cardAccepted, mobilePaymentAccepted
            );
        }
    }
}
