package tech.kayys.numou.history.source.application;
import java.util.UUID;
/** Supplies authenticated tenant identity; never resolve this from a command payload. */
@FunctionalInterface public interface HistoricalSourceTenantContext { UUID requireTenantId(); }