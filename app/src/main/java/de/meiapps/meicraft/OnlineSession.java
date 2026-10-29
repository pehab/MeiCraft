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
        final String uid,name;final int sector;final float x,y,z,yaw;final boolean banned;
        Player(String uid,JSONObject d)throws JSONException{this.uid=uid;name=FirebaseRest.string(d,"name");sector=FirebaseRest.integer(d,"sector");x=(float)FirebaseRest.number(d,"x");y=(float)FirebaseRest.number(d,"y");z=(float)FirebaseRest.number(d,"z");yaw=(float)FirebaseRest.number(d,"yaw");banned=d.getJSONObject("banned").getBoolean("booleanValue");}
    }
    final MainActivity activity;final FirebaseRest firebase;final Listener listener;
    final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    volatile OnlineWorld invitation;
    final String playerName;
    volatile boolean closed,foreground=true,healthy,loading=true,pending,streaming,deletionOnly;
    volatile List<Player> players=Collections.emptyList();
    volatile int sector,side;
    volatile World world;
    volatile String owner="",name="";
    long seed,lastHeartbeat,nextAttempt;int failures;
    static final class Region {
        final int id;String watermark="";boolean generated;
        final Map<String,String> versions=new HashMap<>();final Map<String,Integer> types=new HashMap<>();final Map<String,Boolean> renewable=new HashMap<>();
        Region(int id){this.id=id;}
    }
    final Map<Integer,Region> regions=new HashMap<>();
    volatile Set<Integer> readyRegions=Collections.emptySet();
    volatile Story inventory=new Story();String inventoryVersion="";
    volatile float px,py,pz,angle;
    float lastX=Float.NaN,lastY,lastZ,lastAngle;
    OnlineSession(MainActivity activity,FirebaseRest firebase,OnlineWorld invitation,String playerName,Listener listener){this.activity=activity;this.firebase=firebase;this.invitation=invitation;this.playerName=OnlineWorld.playerName(playerName);this.listener=listener;}
    String base(){return "onlineWorlds/"+invitation.id;}
    String member(String uid){return base()+"/members/"+uid;}
    boolean owner(){return firebase.uid.equals(owner);}
    void start(boolean create,String worldName,int size){
        worker.execute(()->{try{
            firebase.authenticate();
            if(create){
                if(size!=2&&size!=3)throw new IOException("Ungültige Weltgröße.");
                if(worldName.trim().isEmpty()||worldName.trim().length()>48)throw new IOException("Weltname: 1–48 Zeichen.");
                seed=new java.security.SecureRandom().nextInt(1000000000);side=size;
                JSONObject root=FirebaseRest.fields("owner",firebase.uid,"name",worldName.trim(),"seed",seed,"side",side,"protocol",OnlineWorld.PROTOCOL,"invite",invitation.invitation,"closed",false);
                firebase.commit(new JSONArray().put(firebase.write(base(),root,null,new JSONObject().put("exists",false),false)).put(memberWrite()));
            }else{
                JSONObject own=firebase.get(member(firebase.uid));
                if(own==null){
                    // The owner can resume deleting a closed world even after its membership was removed.
                    JSONObject owned=null;try{owned=firebase.get(base());}catch(FirebaseRest.Failure e){if(e.status!=403)throw e;}
                    if(owned==null||!firebase.uid.equals(FirebaseRest.string(owned.getJSONObject("fields"),"owner")))firebase.commit(new JSONArray().put(memberWrite()));
                }
                else if(own.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue"))throw new IOException("Du wurdest aus dieser Welt entfernt.");
            }
            JSONObject root=firebase.get(base());if(root==null)throw new IOException("Diese Welt existiert nicht.");JSONObject data=root.getJSONObject("fields");
            owner=FirebaseRest.string(data,"owner");name=FirebaseRest.string(data,"name");side=FirebaseRest.integer(data,"side");
            if(side!=2&&side!=3)throw new IOException("Ungültige Weltgröße.");
            int protocol=FirebaseRest.integer(data,"protocol");
            deletionOnly=protocol<OnlineWorld.PROTOCOL||data.getJSONObject("closed").getBoolean("booleanValue");
            if(protocol<OnlineWorld.PROTOCOL)throw new IOException("Diese Testwelt nutzt die alte Landschaft. Bitte mit 0.8.1 eine neue Online-Welt anlegen.");
            if(protocol!=OnlineWorld.PROTOCOL)throw new IOException("Bitte alle Geräte auf dieselbe aktuelle App-Version aktualisieren.");
            if(data.getJSONObject("closed").getBoolean("booleanValue"))throw new IOException("Diese Welt wurde geschlossen.");
            invitation=new OnlineWorld(invitation.id,FirebaseRest.string(data,"invite"));
            owner=FirebaseRest.string(data,"owner");name=FirebaseRest.string(data,"name");seed=data.getJSONObject("seed").getLong("integerValue");side=FirebaseRest.integer(data,"side");
            if(side!=2&&side!=3)throw new IOException("Ungültige Weltgröße.");
            try{loadInventory(true);}catch(FirebaseRest.Failure e){if(e.status==403)throw new IOException("Bitte die aktualisierten Firestore-Regeln für 0.8.1 veröffentlichen.");throw e;}
            world=World.onlineSpace(seed,side);World origin=OnlineTerrain.region(seed,0,side);world.x=origin.x;world.y=origin.y;world.z=origin.z;world.yaw=0;px=world.x;py=world.y;pz=world.z;OnlineInventory.apply(world.progress,inventory);
            syncVisible(true);remember();
            if(!closed)activity.runOnUiThread(()->{if(!closed)listener.loaded(world,name);});
            healthy=true;loading=false;
            if(!closed)worker.scheduleWithFixedDelay(this::poll,0,2500,TimeUnit.MILLISECONDS);
        }catch(Exception e){if(e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==403&&create)e=new IOException("Bitte die aktualisierten Firestore-Regeln für 0.8.1 veröffentlichen.");problem(e,true);}});
    }
    JSONObject memberWrite()throws JSONException{
        return firebase.write(member(firebase.uid),FirebaseRest.fields("name",playerName,"banned",false,"invite",invitation.invitation,"sector",0,"x",48.5,"y",40.0,"z",48.5,"yaw",0.0),null,new JSONObject().put("exists",false),true);
    }
    void remember(){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("online-worlds",0);
        try{JSONObject saved=new JSONObject(prefs.getString("worlds","{}"));saved.put(invitation.id,name);prefs.edit().putString("worlds",saved.toString()).putString("player",playerName).apply();}catch(JSONException ignored){/* A broken recent list does not affect world access. */}
    }
    void syncVisible(boolean initial)throws Exception{
        World target=world;
        try{for(int id:OnlineWorld.visibleSectors(px,pz,side,82)){
            if(closed)return;Region region=regions.computeIfAbsent(id,Region::new);
            if(!region.generated)streaming=true;
            World tile=region.generated?null:OnlineTerrain.region(seed,id,side);
            loadChanges(region,tile);
            if(tile!=null){
                region.generated=true;
                if(initial){OnlineInventory.install(target,tile,id,side);markReady(id);}
                else activity.game.gl.queueEvent(()->{if(!closed&&world==target){synchronized(target){OnlineInventory.install(target,tile,id,side);}for(int x=id%side*World.W;x<(id%side+1)*World.W;x+=16)for(int z=id/side*World.D;z<(id/side+1)*World.D;z+=16)activity.game.renderer.dirty(x,z);markReady(id);}});
            }
        }}finally{streaming=false;}
    }
    void markReady(int id){Set<Integer> next=new HashSet<>(readyRegions);next.add(id);readyRegions=Collections.unmodifiableSet(next);}
    boolean canMove(float x,float z){return readyRegions.contains(OnlineWorld.sectorAt(x,z,side));}
    void position(World w){if(w==world){px=w.x;py=w.y;pz=w.z;angle=w.yaw;sector=OnlineWorld.sectorAt(w.x,w.z,side);}}
    private void heartbeat()throws Exception{
        long now=System.currentTimeMillis();boolean moved=Float.isNaN(lastX)||Math.abs(px-lastX)>.1f||Math.abs(py-lastY)>.1f||Math.abs(pz-lastZ)>.1f||Math.abs(angle-lastAngle)>.1f;
        if(!moved&&now-lastHeartbeat<10000)return;
        float x=px,y=py,z=pz,a=angle;
        JSONObject write=firebase.write(member(firebase.uid),FirebaseRest.fields("name",playerName,"sector",sector,"x",x-(sector%side)*World.W,"y",y,"z",z-(sector/side)*World.D,"yaw",a),new String[]{"name","sector","x","y","z","yaw"},new JSONObject().put("exists",true),true);
        firebase.commit(new JSONArray().put(write));lastX=x;lastY=y;lastZ=z;lastAngle=a;lastHeartbeat=now;
    }
    private void poll(){
        if(closed||!foreground||loading||System.currentTimeMillis()<nextAttempt)return;
        try{
            JSONObject own=firebase.get(member(firebase.uid));
            if(own==null||own.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue")){problem(new IOException("Du wurdest aus dieser Welt entfernt."),true);return;}
            syncVisible(false);loadInventory(false);heartbeat();loadPlayers();healthy=true;failures=0;nextAttempt=0;
        }catch(Exception e){healthy=false;failures=Math.min(5,failures+1);nextAttempt=System.currentTimeMillis()+(2500L<<failures);problem(e,e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==403);}
    }
    private void loadPlayers()throws Exception{
        List<Player> next=new ArrayList<>();String page="";
        do{JSONObject result=firebase.list(base()+"/members",page);JSONArray docs=result.optJSONArray("documents");
            if(docs!=null)for(int i=0;i<docs.length();i++){JSONObject doc=docs.getJSONObject(i),d=doc.getJSONObject("fields");String path=doc.getString("name"),id=path.substring(path.lastIndexOf('/')+1);Player p=new Player(id,d);String timestamp=d.getJSONObject("updatedAt").getString("timestampValue");
                if(p.banned||System.currentTimeMillis()-Instant.parse(timestamp).toEpochMilli()>20000)continue;if(next.size()<32)next.add(p);
            }
            page=result.optString("nextPageToken","");
        }while(!page.isEmpty()&&!closed);
        players=Collections.unmodifiableList(next);
    }
    private void loadChanges(Region region,World tile)throws Exception{
        String parent=base()+"/sectors/"+region.id;JSONArray cursor=null;String latest=region.watermark;World target=world;
        do{
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","blocks"))).put("orderBy",new JSONArray().put(order("updatedAt")).put(order("__name__"))).put("limit",200);
            if(!region.watermark.isEmpty())query.put("where",new JSONObject().put("fieldFilter",new JSONObject().put("field",new JSONObject().put("fieldPath","updatedAt")).put("op","GREATER_THAN_OR_EQUAL").put("value",new JSONObject().put("timestampValue",region.watermark))));
            if(cursor!=null)query.put("startAt",new JSONObject().put("values",cursor).put("before",false));
            JSONArray result=firebase.query(parent,query);int count=0;cursor=null;
            for(int i=0;i<result.length();i++){JSONObject row=result.getJSONObject(i);if(!row.has("document"))continue;JSONObject doc=row.getJSONObject("document"),d=doc.getJSONObject("fields");
                int x=FirebaseRest.integer(d,"x"),y=FirebaseRest.integer(d,"y"),z=FirebaseRest.integer(d,"z"),type=FirebaseRest.integer(d,"type");
                if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Ungültiger Online-Block.");
                String id=x+"_"+y+"_"+z,version=doc.getString("updateTime"),timestamp=d.getJSONObject("updatedAt").getString("timestampValue");
                region.types.put(id,type);region.renewable.put(id,d.optJSONObject("renewable")!=null&&d.getJSONObject("renewable").optBoolean("booleanValue",false));
                String previous=region.versions.put(id,version);
                // A retried initial page must restore every delta into the fresh terrain,
                // including versions seen before a previous request failed.
                if(tile!=null)tile.set(x,y,z,type);else if(!version.equals(previous))apply(target,region.id,x,y,z,type,false);
                latest=timestamp;count++;cursor=new JSONArray().put(d.getJSONObject("updatedAt")).put(new JSONObject().put("referenceValue",doc.getString("name")));
            }
            if(count<200)cursor=null;
        }while(cursor!=null&&!closed);
        region.watermark=latest;
    }
    static JSONObject order(String field)throws JSONException{return new JSONObject().put("field",new JSONObject().put("fieldPath",field)).put("direction","ASCENDING");}
    void apply(World target,int region,int x,int y,int z,int type,boolean initial){
        if(initial){target.set(x,y,z,type);return;}
        activity.game.gl.queueEvent(()->{if(!closed&&world==target){int gx=region%side*World.W+x,gz=region/side*World.D+z;synchronized(target){target.set(gx,y,gz,type);}activity.game.renderer.dirty(gx,gz);}});
    }
    String inventoryPath(){return member(firebase.uid)+"/inventory/main";}
    JSONObject inventoryFields(Story state,String lastBlock)throws JSONException{
        JSONObject fields=FirebaseRest.fields("chapter",state.chapter,"iron",state.iron,"placed",state.placed,"glassPlaced",state.glassPlaced,"buckets",state.buckets,"sword",state.sword,"lastBlock",lastBlock);
        JSONArray items=new JSONArray();for(int n:state.inventory)items.put(FirebaseRest.value(n));
        fields.put("items",new JSONObject().put("arrayValue",new JSONObject().put("values",items)));return fields;
    }
    Story decodeInventory(JSONObject d)throws JSONException,IOException{
        Story state=new Story();JSONArray items=d.getJSONObject("items").getJSONObject("arrayValue").getJSONArray("values");
        if(items.length()!=state.inventory.length)throw new IOException("Ungültiges Online-Inventar.");
        for(int i=0;i<items.length();i++)state.inventory[i]=items.getJSONObject(i).getInt("integerValue");
        state.chapter=FirebaseRest.integer(d,"chapter");state.iron=FirebaseRest.integer(d,"iron");state.placed=FirebaseRest.integer(d,"placed");state.glassPlaced=FirebaseRest.integer(d,"glassPlaced");state.buckets=FirebaseRest.integer(d,"buckets");state.sword=d.getJSONObject("sword").getBoolean("booleanValue");
        if(!OnlineInventory.valid(state))throw new IOException("Ungültiges Online-Inventar.");return state;
    }
    void loadInventory(boolean create)throws Exception{
        JSONObject doc=firebase.get(inventoryPath());
        if(doc==null&&create){
            try{firebase.commit(new JSONArray().put(firebase.write(inventoryPath(),inventoryFields(new Story(),""),null,new JSONObject().put("exists",false),true)));}
            catch(FirebaseRest.Failure e){if(e.status!=409&&e.status!=400&&e.status!=412)throw e;}
            doc=firebase.get(inventoryPath());
        }
        if(doc==null)throw new IOException("Online-Inventar konnte nicht geladen werden.");
        String version=doc.getString("updateTime");if(!version.equals(inventoryVersion)){inventory=decodeInventory(doc.getJSONObject("fields"));inventoryVersion=version;if(world!=null)applyInventory(inventory);}
    }
    void applyInventory(Story state){
        Story snapshot=OnlineInventory.copy(state);activity.game.gl.queueEvent(()->{World current=world;if(!closed&&current!=null){synchronized(current){OnlineInventory.apply(current.progress,snapshot);}}});
    }
    boolean beginAction(){
        if(closed)return false;if(!healthy||loading||!foreground){activity.game.message("Verbindung wird hergestellt …");return false;}
        if(pending){activity.game.message("Aktion wird gespeichert …");return false;}pending=true;return true;
    }
    void edit(int x,int y,int z,int type){
        World target=world;if(target==null||!target.contains(x,y,z)||y==0||type<0||type>=World.NAMES.length||!beginAction())return;
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
            JSONArray writes=new JSONArray().put(firebase.write(path,fields,null,condition,true)).put(firebase.write(inventoryPath(),inventoryFields(next,region+"/"+id),null,new JSONObject().put("updateTime",inventoryVersion),true));
            try{
                JSONObject result=firebase.commit(writes);data.versions.put(id,result.getJSONArray("writeResults").getJSONObject(0).getString("updateTime"));data.types.put(id,actual);data.renewable.put(id,renewable);
                inventory=next;inventoryVersion=result.getJSONArray("writeResults").getJSONObject(1).getString("updateTime");
                apply(target,region,lx,y,lz,actual,false);applyInventory(next);
            }catch(FirebaseRest.Failure e){
                if(e.status!=409&&e.status!=400&&e.status!=412)throw e;
                JSONObject current=firebase.get(path);if(current!=null){data.versions.put(id,current.getString("updateTime"));int changed=FirebaseRest.integer(current.getJSONObject("fields"),"type");data.types.put(id,changed);data.renewable.put(id,current.getJSONObject("fields").optJSONObject("renewable")!=null&&current.getJSONObject("fields").getJSONObject("renewable").optBoolean("booleanValue",false));apply(target,region,lx,y,lz,changed,false);}
                loadInventory(false);activity.game.message("Der Block oder dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.");
            }
        }catch(Exception e){healthy=false;problem(e,false);}finally{pending=false;}});
    }
    void progressAction(int recipe){
        if(!beginAction())return;
        worker.execute(()->{try{
            Story next=OnlineInventory.copy(inventory);String message;
            if(recipe<0){boolean claimed=next.claim();message=claimed?"Werkzeug / Auftrag freigeschaltet":next.goal();}
            else message=next.craft(recipe);
            if(!OnlineInventory.same(next,inventory)){
                try{JSONObject result=firebase.commit(new JSONArray().put(firebase.write(inventoryPath(),inventoryFields(next,""),null,new JSONObject().put("updateTime",inventoryVersion),true)));inventory=next;inventoryVersion=result.getJSONArray("writeResults").getJSONObject(0).getString("updateTime");applyInventory(next);}
                catch(FirebaseRest.Failure e){if(e.status!=409&&e.status!=400&&e.status!=412)throw e;loadInventory(false);message="Dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.";}
            }
            final String notice=message;activity.runOnUiThread(()->{if(!closed){activity.toast(notice);activity.online.refreshWorkbench();}});
        }catch(Exception e){healthy=false;problem(e,false);}finally{pending=false;}});
    }
    void deleteWorld(Runnable success,java.util.function.Consumer<String> failure){
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
        if(!owner()||uid.equals(owner))return;
        worker.execute(()->{try{firebase.commit(new JSONArray().put(firebase.write(member(uid),FirebaseRest.fields("banned",true),new String[]{"banned"},new JSONObject().put("exists",true),false)));loadPlayers();activity.runOnUiThread(()->activity.toast("Spieler entfernt und für diese Geräte-ID gesperrt."));}catch(Exception e){problem(e,false);}});
    }
    void problem(Exception e,boolean fatal){
        String message=e instanceof FirebaseRest.Failure?e.getMessage():e instanceof IOException&&e.getMessage()!=null&&!e.getMessage().startsWith("Unable")?e.getMessage():"Verbindung unterbrochen. Bauen pausiert; erneuter Versuch folgt.";
        activity.runOnUiThread(()->{if(!closed)listener.problem(message,fatal);});
    }
    @Override public void close(){closed=true;healthy=false;players=Collections.emptyList();worker.shutdownNow();}
}
