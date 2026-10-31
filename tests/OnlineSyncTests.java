package de.meiapps.meicraft;

public final class OnlineSyncTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(OnlineSyncPolicy.reload(false,-1,0),"New areas load even without edits");
        check(!OnlineSyncPolicy.reload(true,0,0),"Untouched loaded area never queries blocks again");
        check(!OnlineSyncPolicy.reload(true,12,12),"Moving players cannot invalidate an unchanged block area");
        check(OnlineSyncPolicy.reload(true,12,14),"Multiple edits between polls are not missed");
        check(OnlineSyncPolicy.reload(true,0,1),"First edit of an empty area is fetched");
        int manifests=0,idleWrites=0,movingWrites=0;long lastIdle=0,lastMoving=0;
        for(long now=100000;now<3700000;now+=OnlineSyncPolicy.POLL_MS){
            manifests++;if(OnlineSyncPolicy.heartbeat(now,lastIdle,false)){idleWrites++;lastIdle=now;}
            if(OnlineSyncPolicy.heartbeat(now,lastMoving,true)){movingWrites++;lastMoving=now;}
        }
        check(manifests==720&&idleWrites==120&&movingWrites==720,"One idle hour needs 720 manifest reads and only 120 presence writes");
        int oldRequests=1440*(1+4+1+1)+1440; // own membership, four areas, inventory, players, movement
        check((manifests+movingWrites)*100<oldRequests*15,"Moving scenario reduces HTTP requests by more than 85 percent without block queries");
        check(OnlineSyncPolicy.PRESENCE_TTL_MS>2*OnlineSyncPolicy.IDLE_HEARTBEAT_MS,"Idle players stay visible across slow requests");
        check(OnlineSyncPolicy.retryDelay(1,true)>=60000,"Quota errors wait at least one minute");
        for(int i=1;i<100;i++)check(OnlineSyncPolicy.retryDelay(i,true)<=900000,"Quota retry has bounded delay");
        check(OnlineSyncPolicy.retryDelay(6,true)>OnlineSyncPolicy.retryDelay(1,true),"Repeated quota errors reduce request rate further");
        String jwt="eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbm9uIn0.signature",key="AIza-private-key",refresh="private-refresh-token";
        String clean=OnlineDiagnostics.clean("Firestore firestore.googleapis.com document/read_count key="+key+" Bearer "+jwt+" refresh_token="+refresh+" uid="+"a".repeat(32),key,refresh);
        check(!clean.contains(key)&&!clean.contains(jwt)&&!clean.contains(refresh)&&!clean.contains("a".repeat(32)),"Tokens, API keys, refresh tokens and invitation IDs are redacted");
        check(clean.contains("firestore.googleapis.com")&&clean.contains("document/read_count"),"Useful quota metric and service remain readable");
        OnlineDiagnostics diagnostics=new OnlineDiagnostics();diagnostics.request("Blockänderungen lesen");diagnostics.read(3);diagnostics.committed(2,0);
        for(int i=0;i<20;i++)diagnostics.failed("HTTP 429 test-"+i);
        String report=diagnostics.report();check(report.contains("HTTP-Anfragen: 1")&&report.contains("Empfangene Dokumente: 3")&&report.contains("Schreibvorgänge: 2"),"Report distinguishes requests, actual documents and writes");
        check(!report.contains("test-7\n")&&report.contains("test-8\n")&&report.contains("test-19\n"),"Error history is bounded to the latest twelve entries");
        check(OnlineDiagnostics.clean("x".repeat(3000)).length()<1250,"Server errors have bounded storage");
        System.out.println("PASS: "+checks+" sync request-budget, quota backoff and diagnostic privacy assertions");
    }
}
