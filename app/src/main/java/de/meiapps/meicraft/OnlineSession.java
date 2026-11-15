package de.meiapps.meicraft;

import org.json.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** One serial network worker; all world changes are applied on the rendering thread. */
final class OnlineSession implements AutoCloseable {
    interface Listener { void loaded(World world,String name); void problem(String text,boolean fatal); }
    static final class Player {
        final String uid,name;final int sector;final float x,y,z,yaw;final boolean banned;final long serverTime,receivedAt;
        Player(String uid,JSONObject d)throws JSONException{serverTime=0;receivedAt=System.nanoTime();this.uid=uid;name=FirebaseRest.string(d,"name");sector=FirebaseRest.integer(d,"sector");x=(float)FirebaseRest.number(d,"x");y=(float)FirebaseRest.number(d,"y");z=(float)FirebaseRest.number(d,"z");yaw=(float)FirebaseRest.number(d,"yaw");banned=d.getJSONObject("banned").getBoolean("booleanValue");}
        Player(String uid,String name,int sector,float x,float y,float z,float yaw,long serverTime,long receivedAt){this.uid=uid;this.name=name;this.sector=sector;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.serverTime=serverTime;this.receivedAt=receivedAt;banned=false;}
    }
    final MainActivity activity;final FirebaseRest firebase;final Listener listener;
    final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    volatile OnlineWorld invitation;
    final String playerName;
    volatile OnlineV5 modern;volatile OnlineRealtime realtime;
    private ScheduledFuture<?> pollTask;private final java.util.concurrent.atomic.AtomicBoolean realtimeSyncQueued=new java.util.concurrent.atomic.AtomicBoolean();
    volatile boolean closed,foreground=true,healthy,loading=true,pending,streaming,deletionOnly;
    volatile List<Player> players=Collections.emptyList();
    volatile int sector,side;int terrainVersion=1;
    volatile World world;
    volatile String owner="",name="";
    long seed,lastHeartbeat,nextAttempt;volatile long actionStarted;int failures;
    static final class Region {
        final int id;String watermark="";boolean generated,complete;long revision=-1,ownChanges;
        final Map<String,String> versions=new HashMap<>();final Map<String,Integer> types=new HashMap<>();final Map<String,Boolean> renewable=new HashMap<>();
        final List<OnlineChange> own=new ArrayList<>();final Map<String,Long> heads=new HashMap<>();
        Region(int id){this.id=id;}
    }
    JSONObject announcedRegions=new JSONObject();String announcedReadTime="";
    final Map<Integer,Region> regions=new HashMap<>();
    volatile OnlineRegionCache regionCache;
    volatile Set<Integer> readyRegions=Collections.emptySet();
    volatile Story inventory=new Story();String inventoryVersion="";boolean inventoryUncertain;
    volatile float px,py,pz,angle;int visibleX=Integer.MIN_VALUE,visibleZ=Integer.MIN_VALUE;
    float lastX=Float.NaN,lastY,lastZ,lastAngle;
    OnlineSession(MainActivity activity,FirebaseRest firebase,OnlineWorld invitation,String playerName,Listener listener){this.activity=activity;this.firebase=firebase;this.invitation=invitation;this.playerName=OnlineWorld.playerName(playerName);this.listener=listener;}
    String base(){return "onlineWorlds/"+invitation.id;}
    String member(String uid){return base()+"/members/"+uid;}
    boolean owner(){return firebase.uid.equals(owner);}
    String syncPath(){return base()+"/sync/state";}
    JSONObject emptySync()throws JSONException{return new JSONObject().put("regions",new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject()))).put("players",new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject())));}
    JSONObject createSync()throws JSONException{return firebase.write(syncPath(),emptySync(),null,new JSONObject().put("exists",false),false);}
    static String quoted(String field){return "`"+field.replace("\\","\\\\").replace("`","\\`")+"`";}
    JSONObject revisionWrite(int region,Presence presence)throws JSONException{
        JSONObject write=presence==null?firebase.write(syncPath(),new JSONObject(),new String[0],new JSONObject().put("exists",true),false):presenceWrite(presence);
        String field="regions.s"+region;
        JSONArray transforms=write.optJSONArray("updateTransforms");if(transforms==null)transforms=new JSONArray();
        transforms.put(new JSONObject().put("fieldPath",field+".revision").put("increment",FirebaseRest.value(1)))
            .put(new JSONObject().put("fieldPath",field+".updatedAt").put("setToServerValue","REQUEST_TIME"));
        write.put("updateTransforms",transforms);
        return write;
    }
    void start(boolean create,String worldName,int size){
        worker.execute(()->{try{
            firebase.authenticate();modern=firebase.realtimeEligible?new OnlineV6(this):new OnlineV5(this);modern.start(create,worldName,size);
            if(closed)return;healthy=true;loading=false;
            activity.runOnUiThread(()->{if(!closed)listener.loaded(world,name);});
            if(firebase.realtimeEligible){realtime=new OnlineRealtime(this);realtime.start();}scheduleNextPoll();
        }catch(Exception e){problem(e,true);}});
    }
    JSONObject memberWrite()throws JSONException{
        return firebase.write(member(firebase.uid),FirebaseRest.fields("name",playerName,"banned",false,"invite",invitation.invitation,"sector",0,"x",48.5,"y",40.0,"z",48.5,"yaw",0.0),null,new JSONObject().put("exists",false),true);
    }
    void remember(){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("online-worlds",0);
        try{JSONObject saved=new JSONObject(prefs.getString("worlds","{}"));saved.put(invitation.id,name);prefs.edit().putString("worlds",saved.toString()).putString("player",playerName).apply();}catch(JSONException ignored){/* A broken recent list does not affect world access. */}
    }
    void syncVisible(boolean initial)throws Exception{
        if(modern!=null){modern.syncVisible(initial);return;}
        World target=world;
        try{for(int id:OnlineWorld.visibleSectors(px,pz,side,82)){
            if(closed)return;Region region=regions.get(id);
            if(region==null){
                region=new Region(id);regions.put(id,region);
                OnlineRegionCache.Snapshot cached=regionCache==null?null:regionCache.load(id);
                if(cached!=null){region.versions.putAll(cached.versions);region.types.putAll(cached.types);region.renewable.putAll(cached.renewable);region.revision=cached.revision;region.watermark=cached.watermark;region.complete=true;FirebaseRest.DIAGNOSTICS.cacheRestored(cached.types.size());}
            }
            long revision=announcedRegions.has("s"+id)?FirebaseRest.integer(announcedRegions.getJSONObject("s"+id).getJSONObject("mapValue").getJSONObject("fields"),"revision"):0;
            // A revision rollback cannot use a newer exclusive watermark.
            if(region.revision>revision){if(region.generated)throw new IOException("Weltstand zurückgesetzt. Bitte erneut beitreten.");region.versions.clear();region.types.clear();region.renewable.clear();region.watermark="";region.complete=false;region.revision=-1;region.ownChanges=0;}
            boolean reload=OnlineSyncPolicy.reload(region.complete||region.generated,region.revision,region.ownChanges,revision);
            if(!region.generated)streaming=true;
            World tile=region.generated?null:OnlineTerrain.region(seed,id,side,terrainVersion);
            if(tile!=null)for(Map.Entry<String,Integer> entry:region.types.entrySet()){
                String[] xyz=entry.getKey().split("_");tile.set(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),entry.getValue());
            }
            boolean changed=reload||region.ownChanges>0;
            if(reload){loadChanges(region,tile);if(closed)return;}
            else{if(region.ownChanges>0)FirebaseRest.DIAGNOSTICS.ownRegionSkipped();region.watermark=announcedReadTime;}
            region.revision=revision;region.ownChanges=0;region.complete=true;
            if(changed&&regionCache!=null)regionCache.offer(id,new OnlineRegionCache.Snapshot(region.revision,region.watermark,region.versions,region.types,region.renewable));
            if(tile!=null){
                region.generated=true;
                if(initial){OnlineInventory.install(target,tile,id,side);markReady(id);}
                else activity.game.gl.queueEvent(()->{if(!closed&&world==target){synchronized(target){OnlineInventory.install(target,tile,id,side);}for(int x=id%side*World.W;x<(id%side+1)*World.W;x+=16)for(int z=id/side*World.D;z<(id/side+1)*World.D;z+=16)activity.game.renderer.dirty(x,z);markReady(id);}});
            }
        }}finally{streaming=false;}
    }
    void markReady(int id){Set<Integer> next=new HashSet<>(readyRegions);next.add(id);readyRegions=Collections.unmodifiableSet(next);}
    boolean canMove(float x,float z){return readyRegions.contains(OnlineWorld.sectorAt(x,z,side));}
    void position(World w){if(w==world){
        px=w.x;py=w.y;pz=w.z;angle=w.yaw;sector=OnlineWorld.sectorAt(w.x,w.z,side);
        int gx=(int)(px/8),gz=(int)(pz/8);if(modern!=null&&!loading&&!streaming&&(gx!=visibleX||gz!=visibleZ)){
            visibleX=gx;visibleZ=gz;for(int id:OnlineWorld.visibleSectors(px,pz,side,82))if(!readyRegions.contains(id)){requestRealtimeSync();break;}
        }
    }}
    static final class Presence {
        final float x,y,z,angle;final int cell;final long time;
        Presence(float x,float y,float z,float angle,int side,long time){this.x=x;this.y=y;this.z=z;this.angle=angle;this.cell=OnlineWorld.sectorAt(x,z,side);this.time=time;}
    }
    Presence duePresence(){
        if(realtime!=null&&realtime.live())return null;
        long now=System.currentTimeMillis();float x=px,y=py,z=pz,a=angle;
        boolean moved=Float.isNaN(lastX)||Math.abs(x-lastX)>.35f||Math.abs(y-lastY)>.35f||Math.abs(z-lastZ)>.35f||Math.abs(a-lastAngle)>.2f;
        return OnlineSyncPolicy.heartbeat(now,lastHeartbeat,moved,hasPeers())?new Presence(x,y,z,a,side,now):null;
    }
    JSONObject presenceWrite(Presence p)throws JSONException{
        JSONObject player=FirebaseRest.fields("name",playerName,"sector",p.cell,"x",p.x-(p.cell%side)*World.W,"y",p.y,"z",p.z-(p.cell/side)*World.D,"yaw",p.angle);
        JSONObject fields=new JSONObject().put("players",new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject().put(firebase.uid,new JSONObject().put("mapValue",new JSONObject().put("fields",player))))));
        String field="players."+quoted(firebase.uid);
        JSONObject write=firebase.write(syncPath(),fields,new String[]{field},new JSONObject().put("exists",true),false);
        write.put("updateTransforms",new JSONArray().put(new JSONObject().put("fieldPath",field+".updatedAt").put("setToServerValue","REQUEST_TIME")));
        return write;
    }
    void presenceConfirmed(Presence p){lastX=p.x;lastY=p.y;lastZ=p.z;lastAngle=p.angle;lastHeartbeat=p.time;}
    private void heartbeat()throws Exception{
        if(modern!=null){modern.heartbeat();return;}
        Presence presence=duePresence();if(presence==null)return;
        firebase.commit(new JSONArray().put(presenceWrite(presence)));presenceConfirmed(presence);
    }
    boolean hasPeers(){for(Player player:players)if(!player.uid.equals(firebase.uid))return true;return false;}
    synchronized void scheduleNextPoll(){
        if(closed)return;if(pollTask!=null)pollTask.cancel(false);
        long interval=realtime!=null&&realtime.live()&&(!(modern instanceof OnlineV6)||((OnlineV6)modern).journalConnected)?30000:OnlineSyncPolicy.pollInterval(hasPeers());
        if(nextAttempt>System.currentTimeMillis())interval=Math.min(interval,Math.max(100,nextAttempt-System.currentTimeMillis()));
        try{pollTask=worker.schedule(()->{poll();scheduleNextPoll();},interval,TimeUnit.MILLISECONDS);}
        catch(RejectedExecutionException e){if(!closed)throw e;}
    }
    void realtimeStatus(){if(!closed){scheduleNextPoll();requestRealtimeSync();}}
    void requestRealtimeSync(){
        if(closed||!realtimeSyncQueued.compareAndSet(false,true))return;
        try{worker.schedule(()->{realtimeSyncQueued.set(false);if(!closed)poll();},25,TimeUnit.MILLISECONDS);}
        catch(RejectedExecutionException ignored){realtimeSyncQueued.set(false);}
    }
    void poll(){
        if(closed||!foreground||loading||System.currentTimeMillis()<nextAttempt)return;
        try{
            if(modern!=null)modern.retryBans();readSync();syncVisible(false);if(inventoryUncertain){loadInventory(false);inventoryUncertain=false;}heartbeat();healthy=true;failures=0;nextAttempt=0;
        }catch(Exception e){healthy=false;failures=Math.min(6,failures+1);boolean quota=e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==429;
            nextAttempt=System.currentTimeMillis()+OnlineSyncPolicy.retryDelay(failures,quota);
            problem(e,e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==403);scheduleNextPoll();}
    }
    private void readSync()throws Exception{
        if(modern!=null){modern.readSync();return;}
        FirebaseRest.Snapshot snapshot=firebase.snapshot(syncPath());
        JSONObject document=snapshot.document;announcedReadTime=snapshot.readTime;if(document==null)throw new IOException("Synchronisierung fehlt. Bitte zuerst den Weltbesitzer mit der aktuellen Version verbinden lassen.");
        JSONObject fields=document.getJSONObject("fields");announcedRegions=fields.getJSONObject("regions").getJSONObject("mapValue").optJSONObject("fields");if(announcedRegions==null)announcedRegions=new JSONObject();
        JSONObject presence=fields.getJSONObject("players").getJSONObject("mapValue").optJSONObject("fields");List<Player> next=new ArrayList<>();
        if(presence!=null){Iterator<String> keys=presence.keys();while(keys.hasNext()){
            String uid=keys.next();JSONObject data=presence.getJSONObject(uid).getJSONObject("mapValue").getJSONObject("fields");
            String timestamp=data.getJSONObject("updatedAt").getString("timestampValue");
            if(System.currentTimeMillis()-Instant.parse(timestamp).toEpochMilli()>OnlineSyncPolicy.PRESENCE_TTL_MS)continue;
            // Membership permissions are enforced on every server request; presence carries no rights.
            data.put("banned",FirebaseRest.value(false));if(next.size()<32)next.add(new Player(uid,data));
        }}
        players=Collections.unmodifiableList(next);
    }
    private void loadChanges(Region region,World tile)throws Exception{
        String parent=base()+"/sectors/"+region.id;JSONArray cursor=null;String snapshot=announcedReadTime;World target=world;
        if(snapshot.isEmpty())throw new IOException("Synchronisierungs-Snapshot fehlt.");
        do{
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","blocks"))).put("orderBy",new JSONArray().put(order("updatedAt")).put(order("__name__"))).put("limit",200)
                .put("select",new JSONObject().put("fields",new JSONArray().put(field("x")).put(field("y")).put(field("z")).put(field("type")).put(field("renewable")).put(field("updatedAt"))));
            if(!region.watermark.isEmpty())query.put("where",new JSONObject().put("fieldFilter",new JSONObject().put("field",new JSONObject().put("fieldPath","updatedAt")).put("op","GREATER_THAN").put("value",new JSONObject().put("timestampValue",region.watermark))));
            if(cursor!=null)query.put("startAt",new JSONObject().put("values",cursor).put("before",false));
            JSONArray result=firebase.query(parent,query,snapshot);int count=0;cursor=null;
            // The overview and all pages share one server snapshot. A completed
            // snapshot is an exclusive lower bound; no document from its boundary
            // needs downloading again, even when many commits share a timestamp.
            for(int i=0;i<result.length();i++){JSONObject row=result.getJSONObject(i);if(!row.has("document"))continue;JSONObject doc=row.getJSONObject("document"),d=doc.getJSONObject("fields");
                int x=FirebaseRest.integer(d,"x"),y=FirebaseRest.integer(d,"y"),z=FirebaseRest.integer(d,"z"),type=FirebaseRest.integer(d,"type");
                if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Ungültiger Online-Block.");
                String id=x+"_"+y+"_"+z,version=doc.getString("updateTime");
                region.types.put(id,type);region.renewable.put(id,d.optJSONObject("renewable")!=null&&d.getJSONObject("renewable").optBoolean("booleanValue",false));
                String previous=region.versions.put(id,version);
                // A retried initial page must restore every delta into the fresh terrain,
                // including versions seen before a previous request failed.
                if(tile!=null)tile.set(x,y,z,type);else if(!version.equals(previous))apply(target,region.id,x,y,z,type,false);
                count++;cursor=new JSONArray().put(d.getJSONObject("updatedAt")).put(new JSONObject().put("referenceValue",doc.getString("name")));
            }
            if(count<200)cursor=null;
        }while(cursor!=null&&!closed);
        // Failed/cancelled pagination must keep its previous completed watermark.
        if(closed)return;
        region.watermark=snapshot;
    }
    static JSONObject field(String name)throws JSONException{return new JSONObject().put("fieldPath",name);}
    static JSONObject order(String field)throws JSONException{return new JSONObject().put("field",new JSONObject().put("fieldPath",field)).put("direction","ASCENDING");}
    void apply(World target,int region,int x,int y,int z,int type,boolean initial){
        if(initial){target.set(x,y,z,type);return;}
        activity.game.gl.queueEvent(()->{if(!closed&&world==target){int gx=region%side*World.W+x,gz=region/side*World.D+z;int previous; synchronized(target){previous=target.get(gx,y,gz);target.set(gx,y,gz,type);}activity.game.renderer.dirty(gx,gz);if(previous!=0&&type==0)activity.game.renderer.miningDebris(gx,y,gz,previous);}});
    }
    String inventoryPath(){return member(firebase.uid)+"/inventory/main";}
    JSONObject inventoryFields(Story state,String lastBlock)throws JSONException{
        JSONObject fields=FirebaseRest.fields("chapter",state.chapter,"iron",state.iron,"placed",state.placed,"glassPlaced",state.glassPlaced,"buckets",state.buckets,"sword",state.sword,"lastBlock",lastBlock);
        JSONArray items=new JSONArray();for(int n:state.inventory)items.put(FirebaseRest.value(n));
        fields.put("items",new JSONObject().put("arrayValue",new JSONObject().put("values",items)));return fields;
    }
    /** Preserve unmodified fields on the server; the inventory CAS stays unchanged. */
    JSONObject inventoryUpdate(Story next,String lastBlock)throws JSONException{
        JSONObject fields=inventoryFields(next,lastBlock);
        if(Arrays.equals(next.inventory,inventory.inventory))fields.remove("items");
        if(next.chapter==inventory.chapter)fields.remove("chapter");
        if(next.iron==inventory.iron)fields.remove("iron");
        if(next.placed==inventory.placed)fields.remove("placed");
        if(next.glassPlaced==inventory.glassPlaced)fields.remove("glassPlaced");
        if(next.buckets==inventory.buckets)fields.remove("buckets");
        if(next.sword==inventory.sword)fields.remove("sword");
        List<String> mask=new ArrayList<>();Iterator<String> keys=fields.keys();while(keys.hasNext())mask.add(keys.next());
        return firebase.write(inventoryPath(),fields,mask.toArray(new String[0]),new JSONObject().put("updateTime",inventoryVersion),true);
    }
    Story decodeInventory(JSONObject d)throws JSONException,IOException{
        Story state=new Story();JSONArray items=d.getJSONObject("items").getJSONObject("arrayValue").getJSONArray("values");
        if(items.length()!=17&&items.length()!=state.inventory.length)throw new IOException("Ungültiges Online-Inventar.");
        for(int i=0;i<items.length();i++)state.inventory[i]=items.getJSONObject(i).getInt("integerValue");
        state.chapter=FirebaseRest.integer(d,"chapter");state.iron=FirebaseRest.integer(d,"iron");state.placed=FirebaseRest.integer(d,"placed");state.glassPlaced=FirebaseRest.integer(d,"glassPlaced");state.buckets=FirebaseRest.integer(d,"buckets");state.sword=d.getJSONObject("sword").getBoolean("booleanValue");
        if(!OnlineInventory.valid(state))throw new IOException("Ungültiges Online-Inventar.");return state;
    }
    void loadInventory(boolean create)throws Exception{
        if(modern!=null){modern.readSync();return;}
        JSONObject doc=firebase.get(inventoryPath());
        if(doc==null&&create){
            try{firebase.commit(new JSONArray().put(firebase.write(inventoryPath(),inventoryFields(new Story(),""),null,new JSONObject().put("exists",false),true)));}
            catch(FirebaseRest.Failure e){if(e.status!=409&&e.status!=400&&e.status!=412)throw e;}
            doc=firebase.get(inventoryPath());
        }
        if(doc==null)throw new IOException("Online-Inventar konnte nicht geladen werden.");
        String version=doc.getString("updateTime");if(!version.equals(inventoryVersion)){inventory=decodeInventory(doc.getJSONObject("fields"));inventoryVersion=version;if(world!=null)applyInventory(inventory);}
    }
    void applyInventory(Story state){applyInventory(state,0);}
    void applyInventory(Story state,long started){
        Story snapshot=OnlineInventory.copy(state);activity.game.gl.queueEvent(()->{World current=world;if(!closed&&current!=null){synchronized(current){OnlineInventory.apply(current.progress,snapshot);}if(started!=0)FirebaseRest.DIAGNOSTICS.actionCompleted(System.nanoTime()-started);}});
    }
    void mutationFailed(Exception error){
        healthy=false;inventoryUncertain=true;
        if(error instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)error).status==429){failures=Math.min(6,failures+1);nextAttempt=System.currentTimeMillis()+OnlineSyncPolicy.retryDelay(failures,true);}
        problem(error,false);if(realtime!=null||modern instanceof OnlineV6)requestRealtimeSync();
    }
    boolean beginAction(){
        if(closed)return false;if(!healthy||loading||!foreground){activity.game.message("Verbindung wird hergestellt …");return false;}
        if(pending){activity.game.message("Aktion wird gespeichert …");return false;}pending=true;actionStarted=System.nanoTime();return true;
    }
    void edit(int x,int y,int z,int type){
        if(modern!=null){modern.edit(x,y,z,type);return;}
        World target=world;if(target==null||!target.contains(x,y,z)||y==0||type<0||type>=World.NAMES.length||!beginAction())return;
        final long started=actionStarted;
        int region=OnlineWorld.sectorAt(x,z,side);final int expected=target.get(x,y,z),lx=x-region%side*World.W,lz=z-region/side*World.D;
        worker.execute(()->{try{
            Region data=regions.get(region);if(data==null||!data.generated)return;String id=lx+"_"+y+"_"+lz,path=base()+"/sectors/"+region+"/blocks/"+id;
            if(data.types.containsKey(id)&&data.types.get(id)!=expected){apply(target,region,lx,y,lz,data.types.get(id),false);activity.game.message("Der Block wurde inzwischen geändert. Bitte erneut versuchen.");return;}
            if(type!=0&&expected!=0){activity.game.message("Hier steht bereits ein Block.");return;}
            Story next=OnlineInventory.copy(inventory);String problem=type==0?OnlineInventory.harvest(next,expected):OnlineInventory.place(next,type);
            if(!problem.isEmpty()){activity.game.message(problem);return;}
            boolean renewable=type==0&&expected==10&&(data.renewable.getOrDefault(id,false)||!data.versions.containsKey(id));int actual=renewable?10:type;
            JSONObject condition=data.versions.containsKey(id)?new JSONObject().put("updateTime",data.versions.get(id)):new JSONObject().put("exists",false);
            JSONObject fields=FirebaseRest.fields("x",lx,"y",y,"z",lz,"type",actual,"by",firebase.uid,"renewable",renewable);
            Presence presence=duePresence();
            JSONArray writes=new JSONArray().put(firebase.write(path,fields,null,condition,true)).put(inventoryUpdate(next,region+"/"+id)).put(revisionWrite(region,presence));
            try{
                JSONObject result=firebase.commit(writes);data.versions.put(id,result.getJSONArray("writeResults").getJSONObject(0).getString("updateTime"));data.types.put(id,actual);data.renewable.put(id,renewable);
                inventory=next;inventoryVersion=result.getJSONArray("writeResults").getJSONObject(1).getString("updateTime");
                data.ownChanges++;
                if(presence!=null){presenceConfirmed(presence);FirebaseRest.DIAGNOSTICS.positionMerged();}
                apply(target,region,lx,y,lz,actual,false);applyInventory(next,started);
            }catch(FirebaseRest.Failure e){
                if(e.status!=409&&e.status!=400&&e.status!=412)throw e;
                JSONObject current=firebase.get(path);if(current!=null){data.versions.put(id,current.getString("updateTime"));int changed=FirebaseRest.integer(current.getJSONObject("fields"),"type");data.types.put(id,changed);data.renewable.put(id,current.getJSONObject("fields").optJSONObject("renewable")!=null&&current.getJSONObject("fields").getJSONObject("renewable").optBoolean("booleanValue",false));apply(target,region,lx,y,lz,changed,false);}
                loadInventory(false);activity.game.message("Der Block oder dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.");
            }
        }catch(Exception e){mutationFailed(e);}finally{pending=false;}});
    }
    void progressAction(int recipe){
        if(modern!=null){modern.progressAction(recipe);return;}
        if(!beginAction())return;final long started=actionStarted;
        worker.execute(()->{try{
            Story next=OnlineInventory.copy(inventory);String message;
            if(recipe<0){boolean claimed=next.claim();message=claimed?"Werkzeug / Auftrag freigeschaltet":next.goal();}
            else message=next.craft(recipe);
            if(!OnlineInventory.same(next,inventory)){
                try{JSONObject result=firebase.commit(new JSONArray().put(inventoryUpdate(next,"")));inventory=next;inventoryVersion=result.getJSONArray("writeResults").getJSONObject(0).getString("updateTime");applyInventory(next,started);}
                catch(FirebaseRest.Failure e){if(e.status!=409&&e.status!=400&&e.status!=412)throw e;loadInventory(false);message="Dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.";}
            }
            final String notice=message;activity.runOnUiThread(()->{if(!closed){activity.toast(notice);activity.online.refreshWorkbench();}});
        }catch(Exception e){mutationFailed(e);}finally{pending=false;}});
    }
    void deleteWorld(Runnable success,java.util.function.Consumer<String> failure){
        if(modern!=null){modern.deleteWorld(success,failure);return;}
        if(!owner()||closed)return;
        loading=true;healthy=false;
        worker.execute(()->{try{
            JSONObject root=firebase.get(base());
            if(root!=null){
                if(!firebase.uid.equals(FirebaseRest.string(root.getJSONObject("fields"),"owner")))throw new IOException("Nur der Ersteller darf diese Welt löschen.");
                firebase.commit(new JSONArray().put(firebase.write(base(),FirebaseRest.fields("closed",true),new String[]{"closed"},new JSONObject().put("updateTime",root.getString("updateTime")),false)));
                // Always consume the first page: removal cannot invalidate a pagination cursor.
                for(int id=0;id<side*side;id++)deleteCollection(base()+"/sectors/"+id+"/blocks",false);
                deleteCollection(base()+"/members",true);
                firebase.commit(new JSONArray().put(new JSONObject().put("delete",firebase.documentName(syncPath()))));
                firebase.commit(new JSONArray().put(new JSONObject().put("delete",firebase.documentName(base()))));
            }
            activity.runOnUiThread(()->{if(!closed)success.run();});
        }catch(Exception e){activity.runOnUiThread(()->{if(!closed)failure.accept("Löschen unterbrochen. Die Welt bleibt nach Beginn der Löschung gesperrt. Bitte erneut versuchen; noch vorhandene Daten werden weiter entfernt.");});}});
    }
    void deleteCollection(String path,boolean members)throws Exception{
        while(!closed){
            JSONObject page=firebase.list(path,"");JSONArray docs=page.optJSONArray("documents");if(docs==null||docs.length()==0)return;
            JSONArray writes=new JSONArray();
            for(int i=0;i<docs.length();i++){
                String document=docs.getJSONObject(i).getString("name");
                if(members)writes.put(new JSONObject().put("delete",document+"/inventory/main"));
                writes.put(new JSONObject().put("delete",document));
            }
            firebase.commit(writes);
        }
        throw new IOException("Löschen unterbrochen.");
    }
    void ban(String uid){
        if(modern!=null){modern.ban(uid);return;}
        if(!owner()||uid.equals(owner))return;
        worker.execute(()->{try{
            JSONArray writes=new JSONArray().put(firebase.write(member(uid),FirebaseRest.fields("banned",true),new String[]{"banned"},new JSONObject().put("exists",true),false))
                .put(firebase.write(syncPath(),new JSONObject(),new String[]{"players."+quoted(uid)},new JSONObject().put("exists",true),false));
            firebase.commit(writes);readSync();activity.runOnUiThread(()->activity.toast("Spieler entfernt und für diese Geräte-ID gesperrt."));
        }catch(Exception e){problem(e,false);}});
    }
    void problem(Exception e,boolean fatal){
        String message=e instanceof FirebaseRest.Failure?e.getMessage():e instanceof IOException&&e.getMessage()!=null&&!e.getMessage().startsWith("Unable")?e.getMessage():"Verbindung unterbrochen. Bauen pausiert; erneuter Versuch folgt.";
        activity.runOnUiThread(()->{if(!closed)listener.problem(message,fatal);});
    }
    @Override public void close(){closed=true;healthy=false;if(modern instanceof OnlineV6)((OnlineV6)modern).close();OnlineRealtime live=realtime;if(live!=null)live.close();players=Collections.emptyList();worker.shutdownNow();OnlineRegionCache cache=regionCache;if(cache!=null)cache.close();}
}
