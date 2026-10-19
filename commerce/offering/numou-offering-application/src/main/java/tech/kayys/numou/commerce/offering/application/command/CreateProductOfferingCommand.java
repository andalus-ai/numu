package tech.kayys.numou.commerce.offering.application.command;

import tech.kayys.numou.commerce.offering.domain.ChannelId;
import tech.kayys.numou.commerce.offering.domain.OfferingType;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.product.domain.product.ProductId;

import java.util.Objects;
import java.util.UUID;

public record CreateProductOfferingCommand(
        ProductId productId,
        String name,
        OfferingType type,
        ChannelId channelId,
        UUID sellerId,
        DateRange validity
) implements Command {

    public CreateProductOfferingCommand {
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(channelId, "channelId cannot be null");
    }
}
