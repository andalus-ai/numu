package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.warehouse.domain.WarehouseWorkOrder;
public record WarehouseCommitResult(WarehouseWorkOrder order,boolean replayed) {public WarehouseCommitResult{if(order==null)throw new IllegalArgumentException("order required");}}