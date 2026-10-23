package tech.kayys.numou.warehouse.application;
import tech.kayys.numou.warehouse.domain.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.UnaryOperator;
/** Tenant-scoped command boundary; it does not directly mutate inventory balances. */
public final class WarehouseExecutionService {
 private final WarehouseWorkRepository repo;
 public WarehouseExecutionService(WarehouseWorkRepository repo){this.repo=Objects.requireNonNull(repo);}
 public WarehouseWorkOrder create(String tenant,String key,String ref,String fulfillment,WarehouseWorkType type,List<WarehouseWorkLine> lines){
  String t=req(tenant,"tenantId"),k=req(key,"commandKey");List<WarehouseWorkLine> snapshot=List.copyOf(lines);
  String canonical=snapshot.stream().map(l->l.lineId()+"|"+l.itemReference()+"|"+l.uom()+"|"+decimal(l.plannedQuantity())).reduce("",(a,b)->a+b.length()+":"+b);
  String fp=hash("create",t,ref,fulfillment==null?"":fulfillment,String.valueOf(type),canonical);
  Optional<WarehouseWorkOrder> replay=replay(t,k,fp);if(replay.isPresent())return replay.get();
  WarehouseWorkOrder order=WarehouseWorkOrder.create(t,ref,fulfillment,type,snapshot);
  return repo.commit(t,0,order,k,fp,List.of(event(order,"",order.status().name()))).order();
 }
 public WarehouseWorkOrder start(String t,String id,String key){return mutate(t,id,key,"start","",WarehouseWorkOrder::start);}
 public WarehouseWorkOrder execute(String t,String id,String line,BigDecimal qty,String context,String key){return mutate(t,id,key,"execute",line+"|"+decimal(qty)+"|"+(context==null?"":context),o->o.execute(line,qty,context));}
 public WarehouseWorkOrder complete(String t,String id,String key){return mutate(t,id,key,"complete","",WarehouseWorkOrder::complete);}
 public WarehouseWorkOrder cancel(String t,String id,String key){return mutate(t,id,key,"cancel","",WarehouseWorkOrder::cancel);}
 private WarehouseWorkOrder mutate(String tenant,String id,String key,String op,String args,UnaryOperator<WarehouseWorkOrder> f){
  String t=req(tenant,"tenantId"),k=req(key,"commandKey"),work=req(id,"workOrderId"),fp=hash(op,t,work,args);
  Optional<WarehouseWorkOrder> replay=replay(t,k,fp);if(replay.isPresent())return replay.get();
  WarehouseWorkOrder old=repo.find(t,work).orElseThrow(()->new IllegalArgumentException("Work order not found"));WarehouseWorkOrder next=f.apply(old);
  return repo.commit(t,old.version(),next,k,fp,List.of(event(next,old.status().name(),next.status().name()))).order();
 }
 private WarehouseOutboxEvent event(WarehouseWorkOrder o,String from,String to){return WarehouseOutboxEvent.changed(o.tenantId(),o.id(),o.version(),o.type().name(),from,to);}
 private Optional<WarehouseWorkOrder> replay(String t,String k,String fp){return repo.findReceipt(t,k).map(r->{if(!r.fingerprint().equals(fp))throw new IllegalStateException("Command key reused with different payload");return r.result();});}
 private static String hash(String... f){try{MessageDigest d=MessageDigest.getInstance("SHA-256");StringBuilder b=new StringBuilder();for(String s:f)b.append(s.length()).append(':').append(s);return HexFormat.of().formatHex(d.digest(b.toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String decimal(BigDecimal q){Objects.requireNonNull(q);return q.stripTrailingZeros().toPlainString();}
 private static String req(String v,String n){if(v==null||v.isBlank())throw new IllegalArgumentException(n+" required");return v.trim();}
}