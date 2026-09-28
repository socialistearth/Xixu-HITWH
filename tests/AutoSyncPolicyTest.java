package edu.hitwh.fieldnote;
public final class AutoSyncPolicyTest {
 public static void main(String[] args){
  long now=10_000_000L;
  eq(0,AutoSyncPolicy.remaining(0,now,60));
  eq(3_600_000,AutoSyncPolicy.remaining(now,now,60));
  eq(1,AutoSyncPolicy.remaining(now-3_599_999,now,60));
  eq(0,AutoSyncPolicy.remaining(now-3_600_000,now,60));
  eq(0,AutoSyncPolicy.remaining(now-3_600_001,now,60));
  eq(60_000,AutoSyncPolicy.remaining(now+1,now,1));
  eq(604_800_000,AutoSyncPolicy.remaining(now,now,10080));
  eq(60,AutoSyncPolicy.minutes(0));eq(60,AutoSyncPolicy.minutes(-1));eq(60,AutoSyncPolicy.minutes(10081));
  eq(1,AutoSyncPolicy.minutes(1));eq(10080,AutoSyncPolicy.minutes(10080));
  // Reading the same persisted timestamp in a new process must still suppress startup.
  eq(3_540_000,AutoSyncPolicy.remaining(Long.parseLong(Long.toString(now)),now+60_000,60));
  System.out.println("Auto sync cooldown: 13 assertions passed.");
 }
 private static void eq(long wanted,long value){if(wanted!=value)throw new AssertionError(wanted+" != "+value);}
}
