package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Background lease + paged immutable uploads. A CAS manifest atomically publishes world AND inventories. */
final class OnlineCheckpoint implements AutoCloseable {
    final OnlineV6 v;final OnlineSession s;final FirebaseRest f;final RtdbRest db;
    final ScheduledExecutorService renewal=Executors.newSingleThreadScheduledExecutor(),writer=Executors.newSingleThreadScheduledExecutor();
    volatile boolean stopped,held;volatile String leaseToken="";volatile JSONObject manifestDocument;long savedAt,lastAttempt,lastMaintenance,nextRetry;int failures;boolean wasForeground=true;
    OnlineCheckpoint(OnlineV6 v)throws Exception{this.v=v;s=v.s;f=v.f;db=new RtdbRest(f);}
    String manifest(){return s.base()+"/sync/checkpoint";}
    void start(){renewal.scheduleWithFixedDelay(this::lease,0,5,TimeUnit.SECONDS);writer.scheduleWithFixedDelay(this::tick,1,5,TimeUnit.SECONDS);}
    long now(){return System.currentTimeMillis()+db.serverOffset;}
    void lease(){
        if(stopped||s.closed||!s.foreground)return;
        try{
            RtdbRest.Version version=db.version(v.storage()+"/lease");JSONObject old=version.value instanceof JSONObject?(JSONObject)version.value:new JSONObject();
            if(!OnlineJournal.Lease.claim(old.optString("uid"),f.uid,old.optLong("until"),now())){held=false;return;}
            String token=held&&!leaseToken.isEmpty()?leaseToken:OnlineWorld.randomId();JSONObject next=new JSONObject().put("uid",f.uid).put("token",token).put("until",now()+OnlineJournal.Lease.LENGTH);
            if(version.etag==null)throw new IOException("Host-Lease ohne ETag.");db.compare(v.storage()+"/lease",next,version.etag);boolean newlyHeld=!held;held=true;leaseToken=token;if(newlyHeld)manifestDocument=null;
        }catch(Exception e){held=false;}
    }
    boolean valid()throws Exception{JSONObject lease=(JSONObject)db.request("GET",v.storage()+"/lease",null);return held&&OnlineJournal.Lease.valid(lease.optString("uid"),f.uid,lease.optLong("until"),now())&&leaseToken.equals(lease.optString("token"));}
    void tick(){
        if(stopped)return;if(!s.foreground){if(wasForeground)pause();wasForeground=false;return;}wasForeground=true;
        if(!held||System.nanoTime()<nextRetry)return;try{save(false);failures=0;nextRetry=0;}catch(Exception e){failures=Math.min(6,failures+1);nextRetry=System.nanoTime()+OnlineSyncPolicy.retryDelay(failures,e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==429)*1000000L;manifestDocument=null;FirebaseRest.DIAGNOSTICS.failed("Firestore-Checkpoint · "+e.getClass().getSimpleName()+" · Journal bleibt erhalten");}
    }
    JSONObject fields(JSONObject document)throws JSONException{return document==null?new JSONObject():document.getJSONObject("fields");}
    JSONObject plainMap(JSONObject data,String key)throws JSONException{JSONObject map=new JSONObject();if(data.has(key))for(String id:OnlineV5.keys(OnlineV5.fields(data,key)))map.put(id,FirebaseRest.string(OnlineV5.fields(data,key),id));return map;}
    JSONObject counters(JSONObject data)throws JSONException{JSONObject result=new JSONObject();if(data.has("counters"))for(String id:OnlineV5.keys(OnlineV5.fields(data,"counters")))result.put(id,OnlineV5.integer(OnlineV5.fields(data,"counters"),id));return result;}
    JSONObject stringFields(JSONObject plain)throws JSONException{JSONObject result=new JSONObject();for(String key:OnlineV5.keys(plain))result.put(key,FirebaseRest.value(plain.getString(key)));return OnlineV5.map(result);}
    JSONObject numberFields(JSONObject plain)throws JSONException{JSONObject result=new JSONObject();for(String key:OnlineV5.keys(plain))result.put(key,FirebaseRest.value(plain.getLong(key)));return OnlineV5.map(result);}
    JSONObject array(JSONArray values)throws JSONException{JSONArray typed=new JSONArray();for(int i=0;i<values.length();i++)typed.put(FirebaseRest.value(values.getString(i)));return new JSONObject().put("arrayValue",new JSONObject().put("values",typed));}
    List<String> strings(JSONObject data,String key)throws JSONException{List<String> result=new ArrayList<>();if(data.has(key)){JSONArray a=data.getJSONObject(key).getJSONObject("arrayValue").optJSONArray("values");if(a!=null)for(int i=0;i<a.length();i++)result.add(a.getJSONObject(i).getString("stringValue"));}return result;}
    void save(boolean force)throws Exception{
        if(!held||!valid())return;if(manifestDocument==null)manifestDocument=f.get(manifest());JSONObject previousDocument=manifestDocument,old=fields(previousDocument);
        String live=v.latest;JournalHead preview=v.journalConnected&&!live.isEmpty()?new JournalHead(new JSONObject(live)):JournalSnapshot.head(db,v.storage());long oldGeneration=old.has("g")?OnlineV5.integer(old,"g"):-1;
        if(preview.generation<=oldGeneration){if(System.nanoTime()-lastMaintenance>60000000000L){acknowledge(old);recoverAbandoned();lastMaintenance=System.nanoTime();}return;}if(!old.isNull("epoch")&&old.has("epoch")&&!FirebaseRest.string(old,"epoch").equals(v.epoch))throw new IOException("Checkpoint-Epoche stimmt nicht überein.");
        long elapsed=System.nanoTime()-savedAt;boolean onlyInventory=JournalSnapshot.sameCounters(counters(old),preview.chunks);
        if(!force&&manifestDocument!=null&&elapsed<(onlyInventory?10000000000L:60000000000L))return;
        // A failed upload is throttled independently from gameplay and lease renewal.
        if(!force&&System.nanoTime()-lastAttempt<10000000000L)return;lastAttempt=System.nanoTime();
        collectGarbage(old);JournalSnapshot snapshot=JournalSnapshot.collect(db,v.storage(),counters(old));JournalHead h=snapshot.head;
        String upload=OnlineWorld.randomId();JSONObject chunks=plainMap(old,"chunks"),inventories=plainMap(old,"inventories"),actorRevs=new JSONObject();JSONArray ids=new JSONArray(),garbage=new JSONArray(),oldUploads=new JSONArray();List<JSONObject> pages=new ArrayList<>();
        for(String id:snapshot.chunks.keySet()){
            String version=upload+"_c_"+id;if(chunks.has(id))garbage.put(chunks.getString(id));chunks.put(id,version);ids.put(version);JSONObject cells=new JSONObject();for(String key:snapshot.chunks.get(id).keySet())cells.put(key,FirebaseRest.value(snapshot.chunks.get(id).get(key)));
            pages.add(f.write(s.base()+"/saved/"+version,FirebaseRest.fields("upload",upload,"epoch",v.epoch,"g",h.generation,"kind","chunk","chunk",id).put("cells",OnlineV5.map(cells)),null,new JSONObject().put("exists",false),false));
        }
        JSONObject oldActors=old.has("actors")?OnlineV5.fields(old,"actors"):new JSONObject();
        for(String uid:OnlineV5.keys(h.actors)){
            JSONObject a=h.actors.getJSONObject(uid);long rev=a.getLong("rev");actorRevs.put(uid,rev);if(oldActors.has(uid)&&OnlineV5.integer(oldActors,uid)==rev)continue;
            String version=upload+"_u_"+uid;if(inventories.has(uid))garbage.put(inventories.getString(uid));inventories.put(uid,version);ids.put(version);
            pages.add(f.write(s.base()+"/saved/"+version,FirebaseRest.fields("upload",upload,"epoch",v.epoch,"g",h.generation,"kind","inventory","uid",uid,"rev",rev,"seq",a.getLong("seq"),"cipher",a.getString("inventory")).put("log",stringFields(JournalHead.object(a,"tail"))),null,new JSONObject().put("exists",false),false));
        }
        JSONObject stage=FirebaseRest.fields("writer",f.uid,"epoch",v.epoch,"g",h.generation,"state","staging").put("ids",array(ids));
        JSONObject stageResult=f.commit(new JSONArray().put(f.write(s.base()+"/uploads/"+upload,stage,null,new JSONObject().put("exists",false),false).put("updateTransforms",new JSONArray().put(new JSONObject().put("fieldPath","at").put("setToServerValue","REQUEST_TIME")))));
        String stageVersion=stageResult.getJSONArray("writeResults").getJSONObject(0).getString("updateTime");
        JSONArray pageBatch=new JSONArray();int bytes=0;
        for(JSONObject page:pages){int size=page.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(size>950000)throw new IOException("Checkpoint-Dokument zu groß.");if(pageBatch.length()==200||bytes+size>8*1024*1024){f.commit(pageBatch);pageBatch=new JSONArray();bytes=0;}pageBatch.put(page);bytes+=size;}if(pageBatch.length()>0)f.commit(pageBatch);
        if(!valid())throw new IOException("Host-Lease inzwischen übernommen.");if(old.has("upload"))oldUploads.put(FirebaseRest.string(old,"upload"));
        JSONObject record=FirebaseRest.fields("epoch",v.epoch,"g",h.generation,"upload",upload).put("chunks",stringFields(chunks)).put("inventories",stringFields(inventories)).put("counters",numberFields(h.chunks)).put("regions",numberFields(h.regions)).put("actors",numberFields(actorRevs)).put("garbage",array(garbage)).put("oldUploads",array(oldUploads));
        JSONObject condition=previousDocument==null?new JSONObject().put("exists",false):new JSONObject().put("updateTime",previousDocument.getString("updateTime"));
        JSONObject publish=f.write(manifest(),record,null,condition,false).put("updateTransforms",new JSONArray().put(new JSONObject().put("fieldPath","at").put("setToServerValue","REQUEST_TIME")));
        JSONObject committed=f.write(s.base()+"/uploads/"+upload,FirebaseRest.fields("state","committed"),new String[]{"state"},new JSONObject().put("updateTime",stageVersion),false);
        try{f.commit(new JSONArray().put(publish).put(committed));}catch(Exception e){manifestDocument=null;throw e;}
        manifestDocument=f.get(manifest());savedAt=System.nanoTime();if(!FirebaseRest.string(fields(manifestDocument),"upload").equals(upload))return;
        db.request("PUT",v.storage()+"/checkpoint",new JSONObject().put("g",h.generation).put("actors",actorRevs).put("token",upload));prune(actorRevs);recoverAbandoned();lastMaintenance=System.nanoTime();FirebaseRest.DIAGNOSTICS.checkpoint(h.generation,pages.size());
    }
    void collectGarbage(JSONObject old)throws Exception{
        List<String> versions=strings(old,"garbage"),uploads=strings(old,"oldUploads");JSONArray batch=new JSONArray();for(String id:versions){batch.put(new JSONObject().put("delete",f.documentName(s.base()+"/saved/"+id)));if(batch.length()==200){f.commit(batch);batch=new JSONArray();}}
        for(String id:uploads)batch.put(new JSONObject().put("delete",f.documentName(s.base()+"/uploads/"+id)));if(batch.length()>0)f.commit(batch);
    }
    void acknowledge(JSONObject manifest)throws Exception{
        if(!manifest.has("actors")||!valid())return;JSONObject actors=new JSONObject(),typed=OnlineV5.fields(manifest,"actors");for(String uid:OnlineV5.keys(typed))actors.put(uid,OnlineV5.integer(typed,uid));
        db.request("PUT",v.storage()+"/checkpoint",new JSONObject().put("g",OnlineV5.integer(manifest,"g")).put("actors",actors).put("token",FirebaseRest.string(manifest,"upload")));prune(actors);
    }
    void prune(JSONObject revisions)throws Exception{
        for(String uid:OnlineV5.keys(revisions))for(int page=0;page<10;page++){
            long limit=revisions.getLong(uid)-16;if(limit<0)break;String query="?orderBy=%22rev%22&endAt="+limit+"&limitToFirst=200";Object raw=db.request("GET",v.storage()+"/journal/"+uid+query,null);if(!(raw instanceof JSONObject))break;
            JSONObject entries=(JSONObject)raw,patch=new JSONObject();for(String key:OnlineV5.keys(entries))if(entries.getJSONObject(key).getLong("rev")<=limit)patch.put(uid+"/"+key,JSONObject.NULL);
            if(patch.length()==0||!valid())break;db.request("PATCH",v.storage()+"/journal",patch);if(entries.length()<200)break;
        }
    }
    void recoverAbandoned()throws Exception{
        JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","uploads"))).put("orderBy",new JSONArray().put(OnlineSession.order("at"))).put("limit",20);
        JSONArray response=f.query(s.base(),query);for(int i=0;i<response.length();i++)if(response.getJSONObject(i).has("document")){
            JSONObject d=response.getJSONObject(i).getJSONObject("document"),fields=d.getJSONObject("fields");String state=FirebaseRest.string(fields,"state");if(!state.equals("staging")&&!state.equals("abandoned")||System.currentTimeMillis()-Instant.parse(fields.getJSONObject("at").getString("timestampValue")).toEpochMilli()<121000)continue;
            String name=d.getString("name"),id=name.substring(name.lastIndexOf('/')+1);if(state.equals("staging"))f.commit(new JSONArray().put(f.write(s.base()+"/uploads/"+id,FirebaseRest.fields("state","abandoned"),new String[]{"state"},new JSONObject().put("updateTime",d.getString("updateTime")),false)));
            JSONArray deletes=new JSONArray();for(String version:strings(fields,"ids")){deletes.put(new JSONObject().put("delete",f.documentName(s.base()+"/saved/"+version)));if(deletes.length()==200){f.commit(deletes);deletes=new JSONArray();}}
            deletes.put(new JSONObject().put("delete",f.documentName(s.base()+"/uploads/"+id)));f.commit(deletes);
        }
    }
    void pause(){try{writer.execute(()->{try{save(true);}catch(Exception ignored){/* Durable journal remains authoritative. */}finally{release();}});}catch(RejectedExecutionException ignored){/* Already closing. */}}
    void release(){try{if(held){JSONObject lease=(JSONObject)db.request("GET",v.storage()+"/lease",null);if(leaseToken.equals(lease.optString("token")))db.request("PUT",v.storage()+"/lease",new JSONObject().put("uid",f.uid).put("token",leaseToken).put("until",0));}}catch(Exception ignored){/* Leases expire after fifteen seconds. */}held=false;}
    void stop(){stopped=true;renewal.shutdownNow();writer.shutdownNow();db.close();}
    @Override public void close(){stopped=true;renewal.shutdownNow();try{writer.execute(()->{try{save(true);}catch(Exception ignored){/* A killed process cannot erase its acknowledged RTDB journal. */}finally{release();db.close();}});}catch(RejectedExecutionException ignored){db.close();}writer.shutdown();}
}
