package edu.hitwh.fieldnote;
import android.app.job.*;
/** One daily system job. Failure is silent and is not retried in a loop. */
public final class NewsSyncJob extends JobService {
    private long runId;private int generation;
    @Override public boolean onStartJob(JobParameters params){int started=++generation;runId=NewsClient.startAutomatic(this,()->{if(started==generation)jobFinished(params,false);});if(runId==0)NewsClient.schedule(this,true);return runId!=0;}
    @Override public boolean onStopJob(JobParameters params){generation++;NewsClient.cancelAutomatic(runId);NewsClient.schedule(this,true);return false;}
}
