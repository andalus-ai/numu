package tech.kayys.numou.planning.domain;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class DependencyScheduleForecastCalculatorTest {
 private final DependencyScheduleForecastCalculator calculator=new DependencyScheduleForecastCalculator();
 private ActivityId id(long n){return new ActivityId(new UUID(0,n));}
 @Test void calculatesCriticalPathAndFloatAcrossParallelBranches(){
  var a=id(1);var b=id(2);var c=id(3);var d=id(4);
  var f=calculator.calculate(LocalDate.of(2026,1,1),List.of(new ScheduleActivityDuration(a,3),new ScheduleActivityDuration(b,5),new ScheduleActivityDuration(c,2),new ScheduleActivityDuration(d,1)),List.of(new ActivityDependency(a,c),new ActivityDependency(b,c),new ActivityDependency(c,d)));
  assertEquals(9,f.projectDurationDays());assertEquals(LocalDate.of(2026,1,10),f.forecastFinish());assertEquals(2,f.activities().get(a).totalFloatDays());
  assertTrue(f.activities().get(b).critical());assertTrue(f.activities().get(c).critical());assertTrue(f.activities().get(d).critical());assertEquals(List.of(b,c,d),f.criticalPath());
 }
 @Test void rejectsUnknownDependenciesAndCycles(){
  var a=id(1);var b=id(2);var x=id(3);
  assertThrows(IllegalArgumentException.class,()->calculator.calculate(LocalDate.now(),List.of(new ScheduleActivityDuration(a,1)),List.of(new ActivityDependency(a,x))));
  assertThrows(IllegalArgumentException.class,()->calculator.calculate(LocalDate.now(),List.of(new ScheduleActivityDuration(a,1),new ScheduleActivityDuration(b,1)),List.of(new ActivityDependency(a,b),new ActivityDependency(b,a))));
 }
 @Test void supportsIndependentAndZeroDurationMilestones(){
  var a=id(1);var b=id(2);var f=calculator.calculate(LocalDate.of(2026,2,1),List.of(new ScheduleActivityDuration(a,0),new ScheduleActivityDuration(b,4)),List.of());
  assertEquals(4,f.projectDurationDays());assertEquals(4,f.activities().get(a).totalFloatDays());assertTrue(f.activities().get(b).critical());
 }
}
