package tech.kayys.syirkah.groceries.application.api.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.groceries.domain.identifier.ScaleId;

public record ConnectScaleCommand(
        ScaleId scaleId,
        String ipAddress,
        int port
) implements Command {

    public ConnectScaleCommand {
        if (scaleId == null) throw new IllegalArgumentException("Scale ID cannot be null");
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("IP address is required");
        }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ScaleId scaleId;
        private String ipAddress;
        private int port = 9100;

        public Builder scaleId(ScaleId scaleId) { this.scaleId = scaleId; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder port(int port) { this.port = port; return this; }

        public ConnectScaleCommand build() {
            return new ConnectScaleCommand(scaleId, ipAddress, port);
        }
    }
}
