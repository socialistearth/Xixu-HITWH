package edu.hitwh.fieldnote;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class LaunchGateTest {
 public static void main(String[] args)throws Exception {
  LaunchGate gate=new LaunchGate();AtomicInteger count=new AtomicInteger();ExecutorService pool=Executors.newFixedThreadPool(8);
  for(int i=0;i<100;i++)pool.submit(()->{if(gate.take())count.incrementAndGet();});pool.shutdown();if(!pool.awaitTermination(5,TimeUnit.SECONDS)||count.get()!=1||gate.take()||!new LaunchGate().take())throw new AssertionError("Launch synchronization gate failed");
  System.out.println("Launch gate: concurrent duplicate requests suppressed; new launch allowed.");
 }
}
