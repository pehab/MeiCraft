package de.meiapps.meicraft;

/** Bounded polling fallback without SDK dependencies; idle worlds never reread their blocks. */
public final class OnlineSyncPolicy {
    public static final long POLL_MS=5000,IDLE_HEARTBEAT_MS=30000,PRESENCE_TTL_MS=75000;
    private OnlineSyncPolicy(){}
    public static boolean heartbeat(long now,long last,boolean moved){return last==0||now-last>=(moved?POLL_MS:IDLE_HEARTBEAT_MS);}
    public static boolean reload(boolean generated,long seen,long announced){return !generated||seen!=announced;}
    public static long retryDelay(int attempts,boolean quota){return Math.min(quota?900000:120000,(quota?60000L:5000L) << Math.min(5,Math.max(0,attempts-1)));}
}
