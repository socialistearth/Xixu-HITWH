package edu.hitwh.fieldnote;
import java.time.*;
public final class ScheduleMathTest {
 static int checks=0;
 static void equal(Object a,Object b){checks++;if(!a.equals(b))throw new AssertionError(a+" != "+b);}
 static void rejects(Runnable r){checks++;try{r.run();}catch(RuntimeException e){return;}throw new AssertionError("Expected rejection");}
 public static void main(String[] args){
  equal(ScheduleMath.millis("2026-10-12","09:00"),Instant.parse("2026-10-12T01:00:00Z").toEpochMilli());
  equal(ScheduleMath.alarmTime("2026-10-12","09:00",1440,0),Instant.parse("2026-10-11T01:00:00Z").toEpochMilli());
  equal(ScheduleMath.alarmTime("2026-10-12","00:05",10,0),Instant.parse("2026-10-11T15:55:00Z").toEpochMilli());
  equal(ScheduleMath.classDate("2026-12-28",2,7),LocalDate.of(2027,1,10));
  rejects(()->ScheduleMath.classDate("2026-09-08",1,1));
  rejects(()->ScheduleMath.classDate("2026-09-07",0,1));
  rejects(()->ScheduleMath.classDate("2026-09-07",1,8));
  rejects(()->ScheduleMath.millis("2026-02-29","08:00"));
  rejects(()->ScheduleMath.millis("2026-10-12","24:00"));
  rejects(()->ScheduleMath.alarmTime("2026-10-12","09:00",-1,0));
  rejects(()->ScheduleMath.alarmTime("2026-10-12","09:00",1441,0));
  rejects(()->ScheduleMath.alarmTime("2026-10-12","09:00",0,Instant.parse("2026-10-12T01:00:00Z").toEpochMilli()));
  equal(ScheduleMath.millis("2026-12-01","09:00"),Instant.parse("2026-12-01T01:00:00Z").toEpochMilli());
  System.out.println("ScheduleMath: "+checks+" checks passed (Asia/Shanghai, exact-date alarms)");
 }
}
