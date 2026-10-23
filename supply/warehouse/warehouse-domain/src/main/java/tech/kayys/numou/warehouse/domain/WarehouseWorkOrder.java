package tech.kayys.numou.warehouse.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Warehouse task lifecycle; inventory remains authoritative for stock balances and movements. */
public record WarehouseWorkOrder(String id,String tenantId,String reference,String fulfillmentReference,WarehouseWorkType type,WarehouseWorkStatus status,List<WarehouseWorkLine> lines,Instant createdAt,Instant updatedAt,long version) {
 public WarehouseWorkOrder {
  id=req(id,"id");tenantId=req(tenantId,"tenantId");reference=req(reference,"reference");Objects.requireNonNull(type);Objects.requireNonNull(status);lines=List.copyOf(lines);Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt);
  if(lines.isEmpty()||version<1)throw new IllegalArgumentException("Work requires lines and positive version");
  Set<String> ids=new HashSet<>();for(WarehouseWorkLine l:lines)if(!ids.add(l.lineId()))throw new IllegalArgumentException("Duplicate line id");
 }
 public static WarehouseWorkOrder create(String tenant,String ref,String fulfillment,WarehouseWorkType type,List<WarehouseWorkLine> lines){Instant now=Instant.now();return new WarehouseWorkOrder(UUID.randomUUID().toString(),tenant,ref,fulfillment,type,WarehouseWorkStatus.OPEN,lines,now,now,1);}
 public WarehouseWorkOrder start(){if(status!=WarehouseWorkStatus.OPEN)throw bad("Only OPEN work can start");return revise(WarehouseWorkStatus.IN_PROGRESS,lines);}
 public WarehouseWorkOrder execute(String lineId,BigDecimal q,String context){if(status!=WarehouseWorkStatus.IN_PROGRESS)throw bad("Work must be IN_PROGRESS");boolean found=false;List<WarehouseWorkLine> next=new ArrayList<>();for(WarehouseWorkLine l:lines){if(l.lineId().equals(lineId)){found=true;next.add(l.execute(q,context,type));}else next.add(l);}if(!found)throw new IllegalArgumentException("Unknown line: "+lineId);return revise(status,next);}
 public WarehouseWorkOrder complete(){if(status!=WarehouseWorkStatus.IN_PROGRESS)throw bad("Only IN_PROGRESS work can complete");if(lines.stream().anyMatch(l->l.executedQuantity().compareTo(l.plannedQuantity())!=0))throw bad("Outstanding quantities prevent completion");return revise(WarehouseWorkStatus.COMPLETED,lines);}
 public WarehouseWorkOrder cancel(){if(status==WarehouseWorkStatus.COMPLETED||status==WarehouseWorkStatus.CANCELLED)throw bad("Terminal work cannot be cancelled");return revise(WarehouseWorkStatus.CANCELLED,lines);}
 private WarehouseWorkOrder revise(WarehouseWorkStatus s,List<WarehouseWorkLine> l){return new WarehouseWorkOrder(id,tenantId,reference,fulfillmentReference,type,s,l,createdAt,Instant.now(),version+1);}
 private static IllegalStateException bad(String s){return new IllegalStateException(s);}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" required");return v.trim();}
}