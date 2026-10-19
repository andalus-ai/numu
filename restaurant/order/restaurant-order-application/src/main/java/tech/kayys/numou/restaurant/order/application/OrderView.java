package tech.kayys.numou.restaurant.order.application;

import tech.kayys.numou.restaurant.order.domain.DineInOrder;
import tech.kayys.numou.restaurant.order.domain.OrderLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, UUID venueId, String status, String tableLabel, int covers, List<Line> lines, Totals totals) {

    public record Line(UUID id, String name, int quantity, String status, int course, BigDecimal unitTotal, BigDecimal lineTotal,
                       List<String> modifiers, String notes) { }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal serviceCharge, BigDecimal tax, BigDecimal total, String currency) { }

    public static OrderView of(DineInOrder o) {
        final var t = o.totals();
        return new OrderView(o.id().value(), o.venueId().value(), o.status().name(), o.tableLabel(), o.covers(),
                o.lines().stream().map(l -> new Line(l.lineId(), l.name(), l.quantity(), l.status().name(), l.course(), l.unitTotal().amount(),
                        l.lineTotal().amount(), l.modifiers().stream().map(OrderLine.Modifier::name).toList(), l.notes())).toList(),
                new Totals(t.subtotal().amount(), t.discount().amount(), t.serviceCharge().amount(), t.tax().amount(), t.total().amount(),
                        t.total().currency().code()));
    }
}
