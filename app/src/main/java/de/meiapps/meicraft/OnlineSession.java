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
    volatile boolean closed,foreground=true,healthy,loading=true,pending;
    volatile List<Player> players=Collections.emptyList();
    volatile int sector,side;
    volatile World world;
    volatile String owner="",name="";
    long seed,lastHeartbeat,nextAttempt;int failures;String watermark="";
    final Map<String,String> versions=new HashMap<>();
    final Map<String,Integer> knownTypes=new HashMap<>();
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
                if(own==null)firebase.commit(new JSONArray().put(memberWrite()));
                else if(own.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue"))throw new IOException("Du wurdest aus dieser Welt entfernt.");
            }
            JSONObject root=firebase.get(base());if(root==null)throw new IOException("Diese Welt existiert nicht.");JSONObject data=root.getJSONObject("fields");
            if(FirebaseRest.integer(data,"protocol")!=OnlineWorld.PROTOCOL)throw new IOException("Für diese Welt brauchst du eine andere App-Version.");
            if(data.getJSONObject("closed").getBoolean("booleanValue"))throw new IOException("Diese Welt wurde geschlossen.");
            invitation=new OnlineWorld(invitation.id,FirebaseRest.string(data,"invite"));
            owner=FirebaseRest.string(data,"owner");name=FirebaseRest.string(data,"name");seed=data.getJSONObject("seed").getLong("integerValue");side=FirebaseRest.integer(data,"side");
            if(side!=2&&side!=3)throw new IOException("Ungültige Weltgröße.");
            loadSector(0,0,0,0,0,0);remember();
            if(!closed)worker.scheduleWithFixedDelay(this::poll,0,2500,TimeUnit.MILLISECONDS);
        }catch(Exception e){problem(e,true);}});
    }
    JSONObject memberWrite()throws JSONException{
        return firebase.write(member(firebase.uid),FirebaseRest.fields("name",playerName,"banned",false,"invite",invitation.invitation,"sector",0,"x",48.5,"y",40.0,"z",48.5,"yaw",0.0),null,new JSONObject().put("exists",false),true);
    }
    void remember(){
        android.content.SharedPreferences prefs=activity.getSharedPreferences("online-worlds",0);
        try{JSONObject saved=new JSONObject(prefs.getString("worlds","{}"));saved.put(invitation.id,name);prefs.edit().putString("worlds",saved.toString()).putString("player",playerName).apply();}catch(JSONException ignored){/* A broken recent list does not affect world access. */}
    }
    void loadSector(int target,int dx,int dz,float oldY,float oldOther,float heading)throws Exception{
        loading=true;healthy=false;versions.clear();knownTypes.clear();watermark="";
        World next=new World(OnlineWorld.sectorSeed(seed,target),false,false);next.online=true;next.nature.flowEnabled=false;
        world=next;sector=target;loadChanges(true);
        if(dx!=0){next.x=dx>0?1.5f:World.W-1.5f;next.z=Math.max(1.5f,Math.min(World.D-1.5f,oldOther));next.y=oldY;}
        if(dz!=0){next.z=dz>0?1.5f:World.D-1.5f;next.x=Math.max(1.5f,Math.min(World.W-1.5f,oldOther));next.y=oldY;}
        if(next.collides(next.x,next.y,next.z)){
            boolean found=false;for(int y=World.H+2;y>=3;y--)if(!next.collides(next.x,y,next.z)&&next.solid((int)next.x,y-2,(int)next.z)){next.y=y;found=true;break;}
            if(!found)next.y=World.H+2;
        }
        next.yaw=heading;px=next.x;py=next.y;pz=next.z;lastHeartbeat=0;lastX=Float.NaN;
        if(closed)return;
        activity.runOnUiThread(()->{if(!closed)listener.loaded(next,name);});
        healthy=true;loading=false;
    }
    void cross(int dx,int dz){
        if(closed||loading||pending||!healthy)return;int target=OnlineWorld.neighbor(sector,side,dx,dz);if(target<0)return;
        loading=true;healthy=false;float oldY=py,other=dx!=0?pz:px,oldYaw=angle;
        worker.execute(()->{try{loadSector(target,dx,dz,oldY,other,oldYaw);}catch(Exception e){problem(e,true);}});
    }
    void position(World w){if(w==world){px=w.x;py=w.y;pz=w.z;angle=w.yaw;}}
    private void heartbeat()throws Exception{
        long now=System.currentTimeMillis();boolean moved=Float.isNaN(lastX)||Math.abs(px-lastX)>.1f||Math.abs(py-lastY)>.1f||Math.abs(pz-lastZ)>.1f||Math.abs(angle-lastAngle)>.1f;
        if(!moved&&now-lastHeartbeat<10000)return;
        float x=px,y=py,z=pz,a=angle;
        JSONObject write=firebase.write(member(firebase.uid),FirebaseRest.fields("name",playerName,"sector",sector,"x",x,"y",y,"z",z,"yaw",a),new String[]{"name","sector","x","y","z","yaw"},new JSONObject().put("exists",true),true);
        firebase.commit(new JSONArray().put(write));lastX=x;lastY=y;lastZ=z;lastAngle=a;lastHeartbeat=now;
    }
    private void poll(){
        if(closed||!foreground||loading||System.currentTimeMillis()<nextAttempt)return;
        try{
            JSONObject own=firebase.get(member(firebase.uid));
            if(own==null||own.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue")){problem(new IOException("Du wurdest aus dieser Welt entfernt."),true);return;}
            loadChanges(false);heartbeat();loadPlayers();healthy=true;failures=0;nextAttempt=0;
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
    private void loadChanges(boolean initial)throws Exception{
        String parent=base()+"/sectors/"+sector;JSONArray cursor=null;String latest=watermark;World target=world;int region=sector;
        do{
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","blocks"))).put("orderBy",new JSONArray().put(order("updatedAt")).put(order("__name__"))).put("limit",200);
            if(!watermark.isEmpty())query.put("where",new JSONObject().put("fieldFilter",new JSONObject().put("field",new JSONObject().put("fieldPath","updatedAt")).put("op","GREATER_THAN_OR_EQUAL").put("value",new JSONObject().put("timestampValue",watermark))));
            if(cursor!=null)query.put("startAt",new JSONObject().put("values",cursor).put("before",false));
            JSONArray result=firebase.query(parent,query);int count=0;cursor=null;
            for(int i=0;i<result.length();i++){JSONObject row=result.getJSONObject(i);if(!row.has("document"))continue;JSONObject doc=row.getJSONObject("document"),d=doc.getJSONObject("fields");
                int x=FirebaseRest.integer(d,"x"),y=FirebaseRest.integer(d,"y"),z=FirebaseRest.integer(d,"z"),type=FirebaseRest.integer(d,"type");
                if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Ungültiger Online-Block.");
                String id=x+"_"+y+"_"+z,version=doc.getString("updateTime"),timestamp=d.getJSONObject("updatedAt").getString("timestampValue");
                knownTypes.put(id,type);
                if(!version.equals(versions.put(id,version)))apply(target,region,x,y,z,type,initial);
                latest=timestamp;count++;cursor=new JSONArray().put(d.getJSONObject("updatedAt")).put(new JSONObject().put("referenceValue",doc.getString("name")));
            }
            if(count<200)cursor=null;
        }while(cursor!=null&&!closed);
        watermark=latest;
    }
    static JSONObject order(String field)throws JSONException{return new JSONObject().put("field",new JSONObject().put("fieldPath",field)).put("direction","ASCENDING");}
    void apply(World target,int region,int x,int y,int z,int type,boolean initial){
        if(initial){target.set(x,y,z,type);return;}
        activity.game.gl.queueEvent(()->{if(!closed&&world==target&&sector==region){synchronized(target){target.set(x,y,z,type);}activity.game.renderer.dirty(x,z);}});
    }
    void edit(int x,int y,int z,int type){
        if(!OnlineWorld.validBlock(x,y,z,type)||closed)return;
        if(!healthy||loading||!foreground){activity.game.message("Verbindung wird hergestellt …");return;}
        if(pending){activity.game.message("Bauänderung wird gespeichert …");return;}
        pending=true;World target=world;int region=sector;final int expected=target.get(x,y,z);
        worker.execute(()->{try{
            String id=x+"_"+y+"_"+z,path=base()+"/sectors/"+region+"/blocks/"+id;
            if(knownTypes.containsKey(id)&&knownTypes.get(id)!=expected){apply(target,region,x,y,z,knownTypes.get(id),false);activity.game.message("Der Block wurde inzwischen geändert. Bitte erneut versuchen.");return;}
            JSONObject condition=versions.containsKey(id)?new JSONObject().put("updateTime",versions.get(id)):new JSONObject().put("exists",false);
            JSONObject fields=FirebaseRest.fields("x",x,"y",y,"z",z,"type",type,"by",firebase.uid);
            try{JSONObject result=firebase.commit(new JSONArray().put(firebase.write(path,fields,null,condition,true)));String version=result.getJSONArray("writeResults").getJSONObject(0).getString("updateTime");versions.put(id,version);knownTypes.put(id,type);apply(target,region,x,y,z,type,false);}
            catch(FirebaseRest.Failure e){if(e.status!=409&&e.status!=400&&e.status!=412)throw e;JSONObject current=firebase.get(path);if(current!=null){versions.put(id,current.getString("updateTime"));int actual=FirebaseRest.integer(current.getJSONObject("fields"),"type");knownTypes.put(id,actual);apply(target,region,x,y,z,actual,false);}activity.game.message("Der Block wurde inzwischen geändert. Bitte erneut versuchen.");}
        }catch(Exception e){healthy=false;problem(e,false);}finally{pending=false;}});
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
