package tech.kayys.numou.asset.application.maintenance.query;

import java.util.List;

public record MaintenanceWorkOrderPage<T>(List<T> items, int page, int size, long total) {
    public static <T> MaintenanceWorkOrderPage<T> of(List<T> items, int page, int size, long total) {
        return new MaintenanceWorkOrderPage<>(List.copyOf(items), page, size, total);
    }
}
