package edu.hitwh.fieldnote;

/** Persisted wall-clock cooldown; a clock rollback also observes a full interval. */
final class AutoSyncPolicy {
    static final int DEFAULT_MINUTES=60, MAX_MINUTES=10080;
    static int minutes(int value){return value>=1&&value<=MAX_MINUTES?value:DEFAULT_MINUTES;}
    static long remaining(long lastAttempt,long now,int minutes){
        if(lastAttempt<=0)return 0;
        long interval=minutes(minutes)*60_000L;
        if(now<lastAttempt)return interval;
        return Math.max(0,interval-(now-lastAttempt));
    }
}
