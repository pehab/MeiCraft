package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/** RTDB is a low-latency presence/notification channel. Firestore alone confirms resource mutations. */
final class OnlineRealtime implements AutoCloseable {
    final OnlineSession s;final RtdbRest db;final String base;
    final ExecutorService reader=Executors.newSingleThreadExecutor();
    final ScheduledExecutorService sender=Executors.newSingleThreadScheduledExecutor();
    final AtomicLong outgoing=new AtomicLong();final RealtimeTree tree=new RealtimeTree();
    final Map<String,Long> revisions=new HashMap<>();final Map<String,OnlineSession.Player> poses=new HashMap<>();
    volatile Exception lastFailure;volatile String access;volatile boolean connected,closed,legacyPeers;volatile List<OnlineSession.Player> legacyPlayers=Collections.emptyList();boolean hadPosition;long lastSend;float x,y,z,yaw;long retryAt;
    OnlineRealtime(OnlineSession s)throws Exception{this.s=s;db=new RtdbRest(s.firebase);base="worlds/"+s.owner+"/"+s.invitation.id;access=s.modern.state.has("access")?OnlineV5.fields(s.modern.state,"access").toString():"{}";if(!(s.modern instanceof OnlineV6)&&s.modern.sequence>0)outgoing.set(s.modern.revision);}
    void start(){reader.execute(this::run);sender.scheduleWithFixedDelay(this::tick,0,100,TimeUnit.MILLISECONDS);}
    void status(boolean active){if(connected==active)return;connected=active;FirebaseRest.DIAGNOSTICS.realtime(active);s.realtimeStatus();}
    JSONObject timestamp()throws JSONException{return new JSONObject().put(".sv","timestamp");}
    void initialize()throws Exception{
        if(s.owner()){
            Object current=db.request("GET",base,null);
            if(current==JSONObject.NULL){
                JSONObject members=new JSONObject();JSONObject rights=new JSONObject(access);for(String uid:OnlineV5.keys(rights))members.put(uid,new JSONObject().put("active",rights.getJSONObject(uid).getBoolean("booleanValue")).put("proof",s.invitation.invitation));
                JSONObject root=new JSONObject().put("meta",new JSONObject().put("invite",s.invitation.invitation).put("side",s.side).put("closed",false)).put("members",members);
                try{db.request("PUT",base,root,true);}catch(FirebaseRest.Failure e){if(e.status!=412)throw e;}
            }
        }
        Object raw=db.request("GET",base+"/members/"+s.firebase.uid,null);
        if(raw==JSONObject.NULL)db.request("PUT",base+"/members/"+s.firebase.uid,new JSONObject().put("active",true).put("proof",s.invitation.invitation));
        else if(!(raw instanceof JSONObject)||!((JSONObject)raw).optBoolean("active"))throw new Banned();
        reconcileAccess(new JSONObject(access));
    }
    static final class Banned extends IOException{private static final long serialVersionUID=1L;Banned(){super("Du wurdest aus dieser Welt entfernt.");}}
    void run(){int failures=0;
        while(!closed&&!s.closed){
            if(!s.foreground){LockSupport.parkNanos(200000000);continue;}
            try{
                if(lastSend!=0&&System.nanoTime()-lastSend<10000000000L)failures=0;
                initialize();synchronized(tree){tree.root=new JSONObject();poses.clear();revisions.clear();}
                db.listen(base+"/live",this::event);
            }catch(Banned e){s.healthy=false;s.problem(e,true);close();return;}
            catch(Exception e){
                if(!closed&&s.foreground){
                    try{Object member=db.request("GET",base+"/members/"+s.firebase.uid,null);if(member instanceof JSONObject&&!((JSONObject)member).optBoolean("active")){s.healthy=false;s.problem(new Banned(),true);close();return;}}catch(Exception ignored){/* Locked rules and outages use the authorized Firestore fallback. */}
                    failures=Math.min(6,failures+1);retryAt=System.nanoTime()+Math.min(60000,1000L<<(failures-1))*1000000L;
                }
            }finally{status(false);}
            while(!closed&&s.foreground&&System.nanoTime()<retryAt)LockSupport.parkNanos(100000000);
        }
    }
    void event(String type,String payload)throws IOException{
        if(closed||!s.foreground)throw new IOException("RTDB pausiert.");
        if(type.equals("keep-alive"))return;
        if(type.equals("auth_revoked")){s.firebase.invalidateToken(s.firebase.token);throw new IOException("RTDB-Anmeldung erneuern.");}
        if(type.equals("cancel"))throw new IOException("RTDB-Zugriff beendet.");
        if(!type.equals("put")&&!type.equals("patch"))return;
        try{synchronized(tree){tree.apply(type,payload);consume();}status(true);FirebaseRest.DIAGNOSTICS.realtimeEvent();}
        catch(JSONException e){throw new IOException("Ungültige RTDB-Antwort.",e);}
    }
    void consume()throws JSONException{
        JSONObject players=tree.root.optJSONObject("players");Set<String> present=new HashSet<>();long now=System.currentTimeMillis()+db.serverOffset;
        if(players!=null)for(String uid:OnlineV5.keys(players)){
            JSONObject p=players.optJSONObject(uid);if(p==null||uid.equals(s.firebase.uid)||poses.size()>=32&&!poses.containsKey(uid))continue;
            long time=p.optLong("t");if(time<now-8000||time>now+5000)continue;
            int sector=p.optInt("sector",-1);float px=(float)p.optDouble("x",Double.NaN),py=(float)p.optDouble("y",Double.NaN),pz=(float)p.optDouble("z",Double.NaN),a=(float)p.optDouble("yaw",Double.NaN);
            if(sector<0||sector>=s.side*s.side||!Float.isFinite(px)||!Float.isFinite(py)||!Float.isFinite(pz)||!Float.isFinite(a))continue;
            present.add(uid);OnlineSession.Player old=poses.get(uid);
            if(old==null||old.serverTime!=time)poses.put(uid,new OnlineSession.Player(uid,p.optString("name","Spieler"),sector,px,py,pz,a,time,System.nanoTime()));
        }
        poses.keySet().retainAll(present);publish();
        JSONObject signals=s.modern instanceof OnlineV6?null:tree.root.optJSONObject("signals");if(signals!=null)for(String uid:OnlineV5.keys(signals)){
            JSONObject signal=signals.optJSONObject(uid);if(signal==null||uid.equals(s.firebase.uid))continue;long rev=signal.optLong("rev");Long seen=revisions.put(uid,rev);if(seen==null||rev>seen)s.requestRealtimeSync();
        }
    }
    boolean live(){return connected&&!legacyPeers;}
    void fallbackPlayers(List<OnlineSession.Player> players){
        synchronized(tree){List<OnlineSession.Player> old=new ArrayList<>();for(OnlineSession.Player p:players)if(!p.uid.equals(s.firebase.uid)&&!poses.containsKey(p.uid))old.add(p);legacyPlayers=old;boolean changed=legacyPeers!=!old.isEmpty();legacyPeers=!old.isEmpty();publish();if(changed)s.scheduleNextPoll();}
    }
    void publish(){if(connected){List<OnlineSession.Player> all=new ArrayList<>(poses.values());for(OnlineSession.Player p:legacyPlayers)if(!poses.containsKey(p.uid))all.add(p);s.players=Collections.unmodifiableList(all);}}
    void signal(long rev){outgoing.accumulateAndGet(rev,Math::max);}
    void tick(){
        if(closed)return;
        if(!s.foreground){status(false);db.interrupt();if(hadPosition){hadPosition=false;try{db.request("DELETE",base+"/live/players/"+s.firebase.uid,null);}catch(Exception ignored){/* Crashed or unreachable clients expire by server timestamp. */}}return;}
        if(!connected)return;
        try{
            long revision=outgoing.get();if(revision>0){db.request("PUT",base+"/live/signals/"+s.firebase.uid,new JSONObject().put("rev",revision).put("t",timestamp()));outgoing.compareAndSet(revision,0);}
            long now=System.nanoTime();float px=s.px,py=s.py,pz=s.pz,a=s.angle;
            if(OnlinePose.send(now,lastSend,px,py,pz,a,x,y,z,yaw)){
                int cell=OnlineWorld.sectorAt(px,pz,s.side);JSONObject position=new JSONObject().put("name",s.playerName).put("sector",cell).put("x",px-cell%s.side*World.W).put("y",py).put("z",pz-cell/s.side*World.D).put("yaw",a).put("t",timestamp());
                db.request("PUT",base+"/live/players/"+s.firebase.uid,position);lastSend=now;x=px;y=py;z=pz;yaw=a;hadPosition=true;
            }
            synchronized(tree){consume();}
        }catch(Exception e){lastFailure=e;FirebaseRest.DIAGNOSTICS.failed("RTDB · Live-Versand · "+e.getClass().getSimpleName());status(false);db.interrupt();}
    }
    void reconcileAccess(JSONObject access)throws Exception{
        if(!s.owner())return;
        Object raw=db.request("GET",base+"/members",null);JSONObject members=raw instanceof JSONObject?(JSONObject)raw:new JSONObject(),patch=new JSONObject();
        for(String uid:OnlineV5.keys(access)){boolean allowed=access.getJSONObject(uid).getBoolean("booleanValue");JSONObject saved=members.optJSONObject(uid);
            if(saved==null)patch.put(uid,new JSONObject().put("active",allowed).put("proof",s.invitation.invitation));else if(!allowed&&saved.optBoolean("active"))patch.put(uid+"/active",false);}
        if(patch.length()>0)db.request("PATCH",base+"/members",patch);
    }
    void revoke(String uid)throws Exception{db.request("PUT",base+"/members/"+uid+"/active",Boolean.FALSE);}
    void closeWorld()throws Exception{if(db.request("GET",base,null)!=JSONObject.NULL)db.request("PUT",base+"/meta/closed",Boolean.TRUE);}
    @Override public void close(){
        if(closed)return;closed=true;status(false);db.interrupt();reader.shutdownNow();
        try{sender.execute(()->{try{if(hadPosition)db.request("DELETE",base+"/live/players/"+s.firebase.uid,null);}catch(Exception ignored){/* Server-time TTL is the crash/offline fallback. */}finally{db.close();}});}catch(RejectedExecutionException ignored){db.close();}sender.shutdown();
    }
}
