package tech.kayys.numou.planning.domain;
import java.time.LocalDate;
import java.util.*;
/** Deterministic critical-path method for finish-to-start dependencies, in calendar days. */
public final class DependencyScheduleForecastCalculator {
 public static final String METHOD_VERSION="critical-path-calendar-days-v1";
 public DependencyScheduleForecast calculate(LocalDate projectStart,List<ScheduleActivityDuration> durations,List<ActivityDependency> dependencies) {
  Objects.requireNonNull(projectStart); Objects.requireNonNull(durations); Objects.requireNonNull(dependencies);
  if(durations.isEmpty()) throw new IllegalArgumentException("At least one activity is required");
  Map<ActivityId,ScheduleActivityDuration> activities=new LinkedHashMap<>();
  for(var a:durations){Objects.requireNonNull(a);if(activities.putIfAbsent(a.activityId(),a)!=null)throw new IllegalArgumentException("Duplicate activity id: "+a.activityId());}
  Map<ActivityId,List<ActivityDependency>> out=new HashMap<>(),in=new HashMap<>();Map<ActivityId,Integer> degree=new HashMap<>();
  activities.keySet().forEach(id->{out.put(id,new ArrayList<>());in.put(id,new ArrayList<>());degree.put(id,0);});
  Set<String> keys=new HashSet<>();
  for(var e:dependencies){Objects.requireNonNull(e);
   if(!activities.containsKey(e.predecessor())||!activities.containsKey(e.successor()))throw new IllegalArgumentException("Dependency references an unknown activity");
   String key=e.predecessor().value()+"->"+e.successor().value();if(!keys.add(key))throw new IllegalArgumentException("Duplicate dependency: "+key);
   out.get(e.predecessor()).add(e);in.get(e.successor()).add(e);degree.compute(e.successor(),(k,v)->v+1);
  }
  PriorityQueue<ActivityId> ready=new PriorityQueue<>(Comparator.comparing(x->x.value().toString()));
  degree.forEach((id,n)->{if(n==0)ready.add(id);});List<ActivityId> topo=new ArrayList<>();
  while(!ready.isEmpty()){var id=ready.remove();topo.add(id);for(var e:out.get(id)){int n=degree.compute(e.successor(),(k,v)->v-1);if(n==0)ready.add(e.successor());}}
  if(topo.size()!=activities.size())throw new IllegalArgumentException("Schedule dependency network contains a cycle");
  Map<ActivityId,Long> es=new HashMap<>(),ef=new HashMap<>();
  for(var id:topo){long start=0;for(var e:in.get(id))start=Math.max(start,ef.get(e.predecessor()));es.put(id,start);ef.put(id,start+activities.get(id).durationDays());}
  long duration=ef.values().stream().mapToLong(Long::longValue).max().orElse(0);
  Map<ActivityId,Long> ls=new HashMap<>(),lf=new HashMap<>();
  for(int i=topo.size()-1;i>=0;i--){var id=topo.get(i);long finish=duration;if(!out.get(id).isEmpty()){finish=Long.MAX_VALUE;for(var e:out.get(id))finish=Math.min(finish,ls.get(e.successor()));}lf.put(id,finish);ls.put(id,finish-activities.get(id).durationDays());}
  Map<ActivityId,ActivityScheduleForecast> results=new LinkedHashMap<>();
  for(var id:topo){long f=ls.get(id)-es.get(id);if(f<0)throw new IllegalStateException("Negative float indicates an inconsistent schedule");results.put(id,new ActivityScheduleForecast(id,projectStart.plusDays(es.get(id)),projectStart.plusDays(ef.get(id)),projectStart.plusDays(ls.get(id)),projectStart.plusDays(lf.get(id)),Math.toIntExact(f),f==0));}
  List<ActivityId> critical=topo.stream().filter(id->results.get(id).critical()).toList();
  return new DependencyScheduleForecast(projectStart,projectStart.plusDays(duration),Math.toIntExact(duration),critical,results,METHOD_VERSION);
 }
}
