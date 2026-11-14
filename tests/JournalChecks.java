package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real protocol-6 migration, atomic mutation, recovery and checkpoint code, offline transports only. */
final class JournalChecks {
    final SmokeInstrumentation test;JournalChecks(SmokeInstrumentation test){this.test=test;}
    void check(boolean ok,String message){test.check(ok,message);}
    static final class Backend implements FirebaseRest.ConnectionFactory {
        int failCheckpointPage=-1,checkpointPages;boolean failReady;
        final OnlineV5Checks.Store firestore=new OnlineV5Checks.Store(){
            @Override synchronized JSONObject commit(JSONObject body)throws Exception{boolean saved=false;JSONArray w=body.getJSONArray("writes");for(int i=0;i<w.length();i++)if(w.getJSONObject(i).has("update")&&w.getJSONObject(i).getJSONObject("update").getString("name").contains("/saved/"))saved=true;if(saved&&++checkpointPages==failCheckpointPage)throw new FirebaseRest.Failure(429);return super.commit(body);}
        };JSONObject root=new JSONObject();int writes,reads;boolean loseReply,offline;long clockOffset;
        static Object get(JSONObject root,String path){Object value=root;for(String key:path.split("/")){if(key.isEmpty())continue;if(!(value instanceof JSONObject))return JSONObject.NULL;value=((JSONObject)value).opt(key);if(value==null)return JSONObject.NULL;}return value;}
        static Object resolve(Object value,Object old,long time)throws JSONException{
            if(value instanceof JSONObject){JSONObject object=(JSONObject)value;if(object.has(".sv")){Object sv=object.get(".sv");if(sv instanceof String)return time;return (old instanceof Number?((Number)old).longValue():0)+((JSONObject)sv).getLong("increment");}
                JSONObject result=new JSONObject();for(String key:OnlineV5.keys(object)){Object before=old instanceof JSONObject?((JSONObject)old).opt(key):JSONObject.NULL;Object v=resolve(object.get(key),before,time);if(v!=JSONObject.NULL)result.put(key,v);}return result.length()==0?JSONObject.NULL:result;}
            return value;
        }
        static void put(JSONObject root,String path,Object value)throws JSONException{String[] keys=path.split("/");JSONObject current=root;for(int i=0;i<keys.length;i++){String key=keys[i];if(key.isEmpty())continue;boolean last=i==keys.length-1;if(last){if(value==JSONObject.NULL)current.remove(key);else current.put(key,value);}else{JSONObject child=current.optJSONObject(key);if(child==null){child=new JSONObject();current.put(key,child);}current=child;}}}
        static String etag(Object value)throws Exception{return '"'+Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)))+'"';}
        synchronized String request(URL url,String method,String body,String match,Holder holder)throws Exception{
            if(offline)throw new IOException("fixture RTDB offline");String path=url.getPath().substring(1,url.getPath().length()-5);Object old=get(root,path);holder.etag=etag(old);
            if(match!=null&&!match.equals(holder.etag)&&!(match.equals("null_etag")&&old==JSONObject.NULL)){holder.status=412;return "{}";}
            if(method.equals("GET")){
                reads++;String query=URLDecoder.decode(url.getQuery(),"UTF-8");if(query.contains("orderBy=\"rev\"")){
                    JSONObject filtered=new JSONObject();double start=query.contains("startAt=")?Double.parseDouble(query.split("startAt=")[1].split("&")[0]):0,end=Double.parseDouble(query.split("endAt=")[1].split("&")[0]);
                    if(old instanceof JSONObject)for(String key:OnlineV5.keys((JSONObject)old)){JSONObject e=((JSONObject)old).getJSONObject(key);long rev=e.getLong("rev");if(rev>=start&&rev<=end)filtered.put(key,e);}return filtered.length()==0?"null":filtered.toString();
                }return old.toString();
            }
            JSONObject next=new JSONObject(root.toString());Object value=body.isEmpty()?JSONObject.NULL:new JSONTokener(body).nextValue();if(failReady&&path.endsWith("/storage/control")&&value instanceof JSONObject&&((JSONObject)value).optString("phase").equals("ready")){failReady=false;throw new IOException("fixture migration interrupted before activation");}long now=System.currentTimeMillis()+clockOffset;
            if(method.equals("PATCH")){JSONObject patch=(JSONObject)value;for(String key:OnlineV5.keys(patch))put(next,path+"/"+key,resolve(patch.get(key),get(root,path+"/"+key),now));}
            else put(next,path,resolve(value,old,now));root=next;writes++;if(loseReply){loseReply=false;throw new IOException("fixture lost reply after durable journal commit");}return value.toString();
        }
        static final class Holder {String etag;int status=200;}
        @Override public HttpURLConnection open(URL url)throws IOException{
            if(!url.getHost().endsWith("firebasedatabase.app"))return firestore.open(url);
            return new HttpURLConnection(url){final ByteArrayOutputStream body=new ByteArrayOutputStream();final Holder result=new Holder();String response;boolean evaluated;
                @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}@Override public OutputStream getOutputStream(){return body;}
                void evaluate()throws IOException{if(evaluated)return;evaluated=true;try{String effective=getRequestProperty("X-HTTP-Method-Override");response=request(url,effective==null?getRequestMethod():effective,body.toString("UTF-8"),getRequestProperty("if-match"),result);}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}}
                @Override public int getResponseCode()throws IOException{evaluate();return result.status;}@Override public InputStream getInputStream()throws IOException{evaluate();return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}@Override public InputStream getErrorStream(){return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));}
                @Override public String getHeaderField(String key){if(key.equalsIgnoreCase("ETag"))return result.etag;return null;}
            };
        }
    }
    OnlineSession session(MainActivity activity,Backend backend,OnlineWorld invite,String uid)throws Exception{
        FirebaseRest f=new FirebaseRest(activity,backend);f.uid=uid;f.token="fixture-token";f.expires=System.currentTimeMillis()+3600000;
        OnlineSession s=new OnlineSession(activity,f,invite,uid,new OnlineSession.Listener(){public void loaded(World w,String n){}public void problem(String t,boolean fatal){}});s.modern=new OnlineV6(s,false);return s;
    }
    OnlineV6 v(OnlineSession s){return (OnlineV6)s.modern;}
    void drain(OnlineSession s,GameView game)throws Exception{s.worker.submit(()->{}).get(30,TimeUnit.SECONDS);s.worker.schedule(()->{},100,TimeUnit.MILLISECONDS).get(30,TimeUnit.SECONDS);test.onGl(game,()->{});}
    void ready(OnlineSession s){s.loading=false;s.healthy=true;}
    void stock(OnlineV6 v)throws Exception{Story story=new Story();story.chapter=7;story.inventory[2]=100;v.commit(story,null);v.readSync();}
    void finish(OnlineSession s)throws Exception{s.close();if(s.regionCache!=null)s.regionCache.writer.awaitTermination(10,TimeUnit.SECONDS);}
    void run(MainActivity activity,GameView game)throws Exception{
        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession a=session(activity,backend,invite,"parent"),b=session(activity,backend,invite,"child");
        try{
            v(a).start(true,"Journal-Test",2);ready(a);stock(v(a));v(b).start(false,"",2);ready(b);stock(v(b));
            check(FirebaseRest.integer(backend.firestore.docs.get(a.base()).getJSONObject("fields"),"protocol")==6&&!backend.firestore.docs.get(a.base()).getJSONObject("fields").has("migration"),"v6 migration freezes old writers and clears its marker only after verification");
            int fsWrites=backend.firestore.writes,fsCommits=backend.firestore.commits.size();v(a).edit(10,44,10,2);drain(a,game);v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            check(a.inventory.inventory[2]==99&&b.world.get(10,44,10)==2&&backend.firestore.writes==fsWrites,"Immediate durable build consumes one resource and reaches peers with zero Firestore writes");
            backend.loseReply=true;v(a).edit(11,44,10,2);drain(a,game);check(!a.inventoryUncertain&&a.inventory.inventory[2]==98&&a.world.get(11,44,10)==2,"Lost RTDB response reconciles the cell and inventory exactly once");
            for(int i=0;i<20;i++){a.healthy=true;v(a).edit(20+i,44,10,2);drain(a,game);}v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            check(b.world.get(39,44,10)==2&&backend.firestore.writes==fsWrites,"Ring gap reloads durable chunks without any Firestore access");
            OnlineCheckpoint host=v(a).checkpoints;host.lease();check(host.held,"An active player acquires an ETag-protected host lease");v(b).checkpoints.lease();check(!v(b).checkpoints.held,"Second player cannot take an unexpired host lease");
            host.save(true);JSONObject manifest=backend.firestore.docs.get(a.base()+"/sync/checkpoint").getJSONObject("fields");check(OnlineV5.integer(manifest,"g")==v(a).fetchHead().generation,"Checkpoint publishes the complete current atomic journal generation");
            JSONObject refs=OnlineV5.fields(manifest,"chunks");for(String id:OnlineV5.keys(refs)){String version=FirebaseRest.string(refs,id);check(backend.firestore.docs.containsKey(a.base()+"/saved/"+version),"Checkpoint never references an incomplete upload");}
            int afterSave=backend.firestore.writes;host.save(true);check(afterSave==backend.firestore.writes,"Unchanged world does not produce duplicate backup writes");
            String invVersion=FirebaseRest.string(OnlineV5.fields(manifest,"inventories"),"parent");JSONObject savedInventory=backend.firestore.docs.get(a.base()+"/saved/"+invVersion).getJSONObject("fields");long rev=OnlineV5.integer(savedInventory,"rev");Story saved=v(a).cipher(false).decrypt(FirebaseRest.string(savedInventory,"cipher"),invite.id,"parent",rev);check(saved.inventory[2]==78,"Backup inventory matches the same generation as all saved blocks");
            host.release();v(b).checkpoints.lease();check(v(b).checkpoints.held,"A peer takes over after host release");
            b.healthy=true;v(b).edit(45,44,10,2);drain(b,game);v(b).checkpoints.save(true);check(OnlineV5.integer(backend.firestore.docs.get(a.base()+"/sync/checkpoint").getJSONObject("fields"),"g")>OnlineV5.integer(manifest,"g"),"Replacement host advances checkpoint instead of rolling it back");
            check(backend.firestore.commits.size()>fsCommits&&backend.firestore.writes<40,"Many builds are coalesced into a small number of checkpoint writes");
        }finally{finish(a);finish(b);}
        OnlineSession warm=session(activity,backend,invite,"parent");try{v(warm).start(false,"",2);ready(warm);check(warm.world.get(45,44,10)==2&&warm.inventory.inventory[2]==78,"Restart restores durable peer builds and the private inventory after a host change");}finally{finish(warm);}
        deleteRecovery(activity,backend,invite);largeUpload(activity);resumeMigration(activity,game);
        System.out.println("JOURNAL CHECKS: migration, zero-write builds, lost replies, ring gaps, coherent checkpoints, host takeover and warm restart verified");
    }
    void deleteRecovery(MainActivity activity,Backend backend,OnlineWorld invite)throws Exception{
        backend.firestore.docs.get("onlineWorlds/"+invite.id).getJSONObject("fields").put("closed",FirebaseRest.value(true));OnlineSession resumed=session(activity,backend,invite,"parent");boolean closed=false;
        try{v(resumed).start(false,"",2);}catch(IOException expected){closed=true;}
        try{
            check(closed&&resumed.deletionOnly&&resumed.owner(),"Owner can resume deletion of a closed protocol-6 world");
            java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] error={""};v(resumed).deleteWorld(done::countDown,text->{error[0]=text;done.countDown();});
            check(done.await(30,TimeUnit.SECONDS)&&error[0].isEmpty()&&!backend.firestore.docs.containsKey(resumed.base()),"Resumed world deletion completes the old and checkpoint collections");
            check(Backend.get(backend.root,"worlds/parent/"+invite.id+"/storage")==JSONObject.NULL&&Boolean.TRUE.equals(Backend.get(backend.root,"worlds/parent/"+invite.id+"/meta/closed")),"Deletion erases durable RTDB storage while retaining the closed tombstone");
            for(String path:backend.firestore.docs.keySet())check(!path.startsWith(resumed.base()+"/saved/")&&!path.startsWith(resumed.base()+"/uploads/"),"World deletion leaves no orphan backup documents");
        }finally{finish(resumed);}
    }
    void largeUpload(MainActivity activity)throws Exception{
        Backend backend=new Backend();OnlineSession s=session(activity,backend,new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId()),"parent");
        try{
            v(s).start(true,"Paged Checkpoint",2);ready(s);OnlineV6 engine=v(s);JSONObject data=(JSONObject)Backend.get(backend.root,engine.storage()),head=data.getJSONObject("head"),chunks=new JSONObject(),counters=new JSONObject(),regions=new JSONObject();
            for(int i=0;i<205;i++){int r=i/108,k=i%108,cx=k/18,cy=k%18/6,cz=k%6;String id=r+"_"+cx+"_"+cy+"_"+cz,key=(cx*16)+"_"+Math.max(1,cy*16)+"_"+(cz*16);JSONObject cell=new JSONObject().put("type",2).put("water",false).put("token",String.format(java.util.Locale.ROOT,"%032x",i+1));chunks.put(id,new JSONObject().put("cells",new JSONObject().put(key,cell)));counters.put(id,1);regions.put("s"+r,regions.optLong("s"+r)+1);}
            data.put("chunks",chunks);head.put("chunks",counters).put("regions",regions);OnlineCheckpoint host=engine.checkpoints;host.lease();backend.failCheckpointPage=2;boolean failed=false;try{host.save(true);}catch(IOException expected){failed=true;}
            check(failed&&!backend.firestore.docs.containsKey(s.base()+"/sync/checkpoint"),"Interrupted second upload page cannot publish a partial checkpoint");
            host.lastAttempt=System.nanoTime()-20000000000L;backend.failCheckpointPage=backend.checkpointPages+1;host.lease();host.tick();int afterQuota=backend.firestore.commits.size(),afterReads=backend.firestore.reads;for(int i=0;i<10;i++)host.tick();check(backend.firestore.commits.size()==afterQuota&&backend.firestore.reads==afterReads&&host.nextRetry>System.nanoTime()&&s.healthy,"Checkpoint quota failure backs off without pausing or hammering the live world");backend.failCheckpointPage=-1;
            String abandoned="";for(String path:backend.firestore.docs.keySet())if(abandoned.isEmpty()&&path.startsWith(s.base()+"/uploads/"))abandoned=path;
            host.lease();host.save(true);check(host.nextRetry==0,"Successful forced checkpoint clears the prior quota backoff");JSONObject manifest=backend.firestore.docs.get(s.base()+"/sync/checkpoint").getJSONObject("fields");check(OnlineV5.fields(manifest,"chunks").length()==205,"Paged retry atomically publishes all 205 chunks");
            JSONObject at=new JSONObject().put("timestampValue",java.time.Instant.now().minusSeconds(180).toString());backend.firestore.docs.get(abandoned).getJSONObject("fields").put("at",at);host.recoverAbandoned();check(!backend.firestore.docs.containsKey(abandoned),"Abandoned staged upload is fenced and garbage-collected");
            for(String key:OnlineV5.keys(OnlineV5.fields(manifest,"chunks")))check(backend.firestore.docs.containsKey(s.base()+"/saved/"+FirebaseRest.string(OnlineV5.fields(manifest,"chunks"),key)),"Garbage collection retains every currently referenced immutable chunk");
        }finally{finish(s);}
    }
    void resumeMigration(MainActivity activity,GameView game)throws Exception{
        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession old=session(activity,backend,invite,"parent");old.modern=new OnlineV5(old);OnlineV5Checks helper=new OnlineV5Checks(test);
        try{old.modern.start(true,"Resume",2);ready(old);helper.stock(backend.firestore,old);old.modern.edit(10,44,10,2);drain(old,game);}finally{finish(old);}
        OnlineSession interrupted=session(activity,backend,invite,"parent");backend.failReady=true;boolean failed=false;try{v(interrupted).start(false,"",2);}catch(IOException expected){failed=true;}finally{finish(interrupted);}
        JSONObject root=backend.firestore.docs.get(interrupted.base()).getJSONObject("fields");check(failed&&FirebaseRest.integer(root,"protocol")==6&&FirebaseRest.string(root,"migration").equals("rtdb6"),"Interrupted activation keeps old writers frozen and a durable migration marker");
        OnlineSession resumed=session(activity,backend,invite,"parent");try{v(resumed).start(false,"",2);check(resumed.world.get(10,44,10)==2&&resumed.inventory.inventory[2]==99&&!backend.firestore.docs.get(resumed.base()).getJSONObject("fields").has("migration"),"Migration resumes from frozen chunks and preserves private resources");}finally{finish(resumed);}
    }

}
