package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.warehouse.domain.WarehouseWorkOrder;
import java.util.*;
public interface WarehouseWorkRepository {
 Optional<WarehouseWorkOrder> find(String tenantId,String workOrderId);
 Optional<WarehouseCommandReceipt> findReceipt(String tenantId,String commandKey);
 /** Atomically persist aggregate, idempotency receipt and outbox; expectedVersion=0 means create-only. */
 WarehouseCommitResult commit(String tenantId,long expectedVersion,WarehouseWorkOrder candidate,String commandKey,String fingerprint,List<WarehouseOutboxEvent> outboxEvents);
}