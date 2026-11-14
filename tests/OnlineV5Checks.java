package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Android executes the real session/REST/migration code against a deterministic offline backend.
 * Authorization and cross-document races are tested separately against the Firestore emulator. */
final class OnlineV5Checks {
    final SmokeInstrumentation test;OnlineV5Checks(SmokeInstrumentation test){this.test=test;}
    void check(boolean ok,String text){test.check(ok,text);}
    static class Store implements FirebaseRest.ConnectionFactory {
        final Map<String,JSONObject> docs=new LinkedHashMap<>();Map<String,JSONObject> snapshot=new LinkedHashMap<>();
        final List<String> gets=new ArrayList<>();
        final List<JSONObject> commits=new ArrayList<>(),batchGets=new ArrayList<>();
        int tick,reads,chunkQueries,chunkDocuments,legacyDocuments,writes,failMigrationAt=-1;boolean lostReply;
        final Instant base=Instant.now().minusSeconds(2);String readTime="";
        String stamp(){return base.plusMillis(++tick).toString();}
        static JSONObject copy(JSONObject value)throws JSONException{return new JSONObject(value.toString());}
        static String relative(String name){return name.substring(name.indexOf("/documents/")+11);}
        void put(String path,JSONObject fields)throws JSONException{docs.put(path,new JSONObject().put("name","projects/fixture/databases/(default)/documents/"+path).put("updateTime",stamp()).put("fields",copy(fields)));}
        static String[] parts(String field){return field.replace("`","").split("\\.");}
        static JSONObject value(JSONObject fields,String field)throws JSONException{String[] keys=parts(field);JSONObject current=fields;for(int i=0;i<keys.length-1;i++){if(!current.has(keys[i]))return null;current=OnlineV5.fields(current,keys[i]);}return current.optJSONObject(keys[keys.length-1]);}
        static void set(JSONObject fields,String field,JSONObject value)throws JSONException{String[] keys=parts(field);JSONObject current=fields;for(int i=0;i<keys.length-1;i++){if(!current.has(keys[i]))current.put(keys[i],OnlineV5.map(new JSONObject()));current=OnlineV5.fields(current,keys[i]);}if(value==null)current.remove(keys[keys.length-1]);else current.put(keys[keys.length-1],copy(value));}
        synchronized JSONObject commit(JSONObject body)throws Exception{
            JSONArray changes=body.getJSONArray("writes");
            for(int i=0;i<changes.length();i++){
                JSONObject write=changes.getJSONObject(i);if(!write.has("update"))continue;JSONObject update=write.getJSONObject("update");String path=relative(update.getString("name"));
                JSONObject data=update.getJSONObject("fields");if(path.endsWith("/sync/v5")&&data.has("migration")){
                    JSONObject progress=OnlineV5.fields(data,"migration");if(progress.has("count")&&OnlineV5.integer(progress,"count")>=failMigrationAt&&failMigrationAt>=0){failMigrationAt=-1;throw new FirebaseRest.Failure(429);}
                }
                JSONObject condition=write.optJSONObject("currentDocument");if(condition!=null){if(condition.has("exists")&&condition.getBoolean("exists")!=docs.containsKey(path))throw new FirebaseRest.Failure(409);if(condition.has("updateTime")&&(!docs.containsKey(path)||!docs.get(path).getString("updateTime").equals(condition.getString("updateTime"))))throw new FirebaseRest.Failure(409);}
            }
            String time=stamp();JSONArray results=new JSONArray();
            for(int i=0;i<changes.length();i++){
                JSONObject write=changes.getJSONObject(i);if(write.has("delete")){docs.remove(relative(write.getString("delete")));results.put(new JSONObject());continue;}
                JSONObject update=write.getJSONObject("update"),data=update.getJSONObject("fields");String path=relative(update.getString("name"));JSONObject next=docs.containsKey(path)?copy(docs.get(path).getJSONObject("fields")):new JSONObject();
                JSONObject mask=write.optJSONObject("updateMask");if(mask==null)next=copy(data);else{JSONArray paths=mask.getJSONArray("fieldPaths");for(int j=0;j<paths.length();j++)set(next,paths.getString(j),value(data,paths.getString(j)));}
                JSONArray transforms=write.optJSONArray("updateTransforms"),transformed=new JSONArray();if(transforms!=null)for(int j=0;j<transforms.length();j++){
                    JSONObject transform=transforms.getJSONObject(j);String field=transform.getString("fieldPath");JSONObject value;
                    if(transform.has("increment")){JSONObject before=value(next,field);value=FirebaseRest.value((before==null?0:before.getLong("integerValue"))+transform.getJSONObject("increment").getLong("integerValue"));}
                    else value=new JSONObject().put("timestampValue",time);set(next,field,value);transformed.put(value);
                }
                docs.put(path,new JSONObject().put("name",update.getString("name")).put("fields",next).put("updateTime",time));results.put(new JSONObject().put("updateTime",time).put("transformResults",transformed));
            }
            writes+=changes.length();commits.add(copy(body));if(lostReply){lostReply=false;throw new IOException("Simulated lost commit response");}
            return new JSONObject().put("commitTime",time).put("writeResults",results);
        }
        @Override public HttpURLConnection open(URL url){return new HttpURLConnection(url){
            final ByteArrayOutputStream body=new ByteArrayOutputStream();String response;int status=200;boolean evaluated;
            @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
            @Override public OutputStream getOutputStream(){return body;}
            void evaluate()throws IOException{if(evaluated)return;evaluated=true;try{
                String path=url.getPath();JSONObject request=body.size()==0?new JSONObject():new JSONObject(body.toString("UTF-8"));
                if(path.endsWith(":commit")){response=commit(request).toString();return;}
                if(path.endsWith(":batchGet")){
                    reads++;batchGets.add(copy(request));snapshot=new LinkedHashMap<>();for(Map.Entry<String,JSONObject> e:docs.entrySet())snapshot.put(e.getKey(),copy(e.getValue()));readTime=stamp();String key=relative(request.getJSONArray("documents").getString(0));JSONObject doc=snapshot.get(key);
                    JSONObject row=new JSONObject().put("readTime",readTime);if(doc==null)row.put("missing",request.getJSONArray("documents").getString(0));else{
                        doc=copy(doc);if(request.has("mask")){JSONObject projected=new JSONObject();JSONArray paths=request.getJSONObject("mask").getJSONArray("fieldPaths");for(int i=0;i<paths.length();i++){String field=paths.getString(i);JSONObject selected=value(doc.getJSONObject("fields"),field);if(selected!=null)set(projected,field,selected);}doc.put("fields",projected);}row.put("found",doc);
                    }response=new JSONArray().put(row).toString();return;
                }
                String relative=path.substring(path.indexOf("/documents/")+11);
                if(path.endsWith(":runQuery")){
                    String parent=relative.substring(0,relative.length()-9);JSONObject query=request.getJSONObject("structuredQuery");String collection=query.getJSONArray("from").getJSONObject(0).getString("collectionId"),prefix=parent+"/"+collection+"/";boolean chunks=collection.equals("chunks");if(chunks)chunkQueries++;
                    Map<String,JSONObject> source=request.has("readTime")?snapshot:docs;List<JSONObject> selected=new ArrayList<>();String lower=query.has("where")?query.getJSONObject("where").getJSONObject("fieldFilter").getJSONObject("value").getString("timestampValue"):"";
                    String after="";if(query.has("startAt")){JSONArray values=query.getJSONObject("startAt").getJSONArray("values");after=values.getJSONObject(values.length()-1).getString("referenceValue");}
                    for(Map.Entry<String,JSONObject> e:source.entrySet())if(e.getKey().startsWith(prefix)&&e.getKey().substring(prefix.length()).indexOf('/')<0){JSONObject doc=e.getValue();if(!lower.isEmpty()&&!Instant.parse(doc.getJSONObject("fields").getJSONObject("updatedAt").getString("timestampValue")).isAfter(Instant.parse(lower)))continue;selected.add(doc);}
                    boolean timestamp=query.getJSONArray("orderBy").getJSONObject(0).getJSONObject("field").getString("fieldPath").equals("updatedAt");
                    selected.sort((a,b)->{try{if(timestamp){int cmp=Instant.parse(a.getJSONObject("fields").getJSONObject("updatedAt").getString("timestampValue")).compareTo(Instant.parse(b.getJSONObject("fields").getJSONObject("updatedAt").getString("timestampValue")));if(cmp!=0)return cmp;}return a.getString("name").compareTo(b.getString("name"));}catch(JSONException e){throw new IllegalStateException(e);}});
                    JSONArray result=new JSONArray();boolean past=after.isEmpty();int max=query.getInt("limit");for(JSONObject doc:selected){if(!past){if(doc.getString("name").equals(after))past=true;continue;}result.put(new JSONObject().put("document",copy(doc)));if(chunks)chunkDocuments++;else legacyDocuments++;if(result.length()==max)break;}
                    response=result.toString();return;
                }
                if("GET".equals(getRequestMethod())&&url.getQuery()!=null&&url.getQuery().contains("pageSize=")){
                    JSONArray list=new JSONArray();String prefix=relative+"/";for(Map.Entry<String,JSONObject> e:docs.entrySet())if(e.getKey().startsWith(prefix)&&e.getKey().substring(prefix.length()).indexOf('/')<0)list.put(copy(e.getValue()));response=new JSONObject().put("documents",list).toString();return;
                }
                reads++;gets.add(relative);JSONObject doc=docs.get(relative);if(doc==null){status=404;response="{}";}else response=doc.toString();
            }catch(FirebaseRest.Failure e){status=e.status;response="{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"fixture quota\"}}";}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}}
            @Override public int getResponseCode()throws IOException{evaluate();return status;}
            @Override public InputStream getInputStream()throws IOException{evaluate();return new ByteArrayInputStream(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            @Override public InputStream getErrorStream(){return new ByteArrayInputStream((response==null?"{}":response).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        };}
    }
    OnlineSession session(MainActivity activity,Store store,OnlineWorld invitation,String uid)throws Exception{
        FirebaseRest rest=new FirebaseRest(activity,store);rest.uid=uid;rest.token="fixture-token";rest.expires=System.currentTimeMillis()+3600000;
        OnlineSession s=new OnlineSession(activity,rest,invitation,uid,new OnlineSession.Listener(){public void loaded(World w,String n){}public void problem(String t,boolean fatal){}});s.modern=new OnlineV5(s);return s;
    }
    void ready(OnlineSession s){s.loading=false;s.healthy=true;}
    void drain(OnlineSession s,GameView game)throws Exception{s.worker.submit(()->{}).get(30,TimeUnit.SECONDS);test.onGl(game,()->{});}
    void finish(OnlineSession s)throws Exception{s.close();if(s.regionCache!=null)check(s.regionCache.writer.awaitTermination(10,TimeUnit.SECONDS),"v5 cache writer drains on leave");}
    void stock(Store store,OnlineSession s)throws Exception{
        JSONObject state=store.docs.get(s.modern.path()).getJSONObject("fields"),players=OnlineV5.fields(state,"players"),me=OnlineV5.fields(players,s.firebase.uid);Story inv=new Story();inv.chapter=7;inv.inventory[2]=100;
        me.put("inventory",FirebaseRest.value(s.modern.encrypt(inv,1)));me.put("rev",FirebaseRest.value(1));s.inventoryVersion="";s.modern.readSync();
    }
    void run(MainActivity activity,GameView game)throws Exception{
        Store store=new Store();OnlineWorld invitation=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession a=session(activity,store,invitation,"parent"),b=session(activity,store,invitation,"child");
        try{
            a.modern.start(true,"Testwelt",2);ready(a);b.modern.start(false,"",2);ready(b);stock(store,a);stock(store,b);
            int queries=store.chunkQueries,commits=store.commits.size();a.modern.edit(10,44,10,2);drain(a,game);
            check(store.commits.get(commits).getJSONArray("writes").length()==2&&a.inventory.inventory[2]==99,"real v5 edit performs two writes and consumes exactly one item");
            b.modern.readSync();b.modern.syncVisible(false);test.onGl(game,()->{});check(store.chunkQueries==queries&&b.world.get(10,44,10)==2,"peer consumes the overview tail without any chunk query");
            JSONObject projection=store.batchGets.get(store.batchGets.size()-1);check(projection.has("mask")&&!projection.getJSONObject("mask").getJSONArray("fieldPaths").toString().contains("parent`.inventory"),"regular snapshot projects out peer inventories and confirmed own tail");
            // Both players edit separate cells in one chunk using the same prior overview.
            a.modern.edit(11,44,10,2);b.modern.edit(12,44,10,2);drain(a,game);drain(b,game);a.modern.readSync();a.modern.syncVisible(false);b.modern.readSync();b.modern.syncVisible(false);test.onGl(game,()->{});
            check(a.world.get(11,44,10)==2&&a.world.get(12,44,10)==2&&b.world.get(11,44,10)==2&&b.world.get(12,44,10)==2&&store.chunkQueries==queries,"shared-chunk cell patches preserve both builders without a global CAS retry");
            // The cloud applied this action but its reply never reached the client.
            store.lostReply=true;a.modern.edit(13,44,10,2);drain(a,game);check(a.inventoryUncertain,"ambiguous commit is marked uncertain");a.poll();test.onGl(game,()->{});
            check(a.world.get(13,44,10)==2&&a.inventory.inventory[2]==97,"lost response reconciles own inventory and cell exactly once");
            // Exceed the bounded tail while a peer is absent: fall back to a chunk snapshot.
            for(int i=0;i<OnlineChange.TAIL+2;i++){a.healthy=true;a.modern.edit(20+i,44,10,2);drain(a,game);}int before=store.chunkQueries;b.modern.readSync();b.modern.syncVisible(false);test.onGl(game,()->{});
            check(store.chunkQueries>before&&b.world.get(20+OnlineChange.TAIL+1,44,10)==2&&b.world.get(10,44,10)==2,"tail gap downloads changed chunks and preserves old constructions");
        }finally{finish(a);finish(b);}
        migration(activity,game);
        legacyLifecycle(activity);
    }
    void migration(MainActivity activity,GameView game)throws Exception{
        Store store=new Store();OnlineWorld invitation=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession first=session(activity,store,invitation,"parent");String base=first.base();
        store.put(base,FirebaseRest.fields("owner","parent","name","Altbestand","seed",123,"side",2,"protocol",4,"invite",invitation.invitation,"closed",false));
        store.put(base+"/members/parent",FirebaseRest.fields("name","parent","banned",false));store.put(base+"/members/child",FirebaseRest.fields("name","child","banned",false));store.put(base+"/members/banned",FirebaseRest.fields("name","banned","banned",true));
        Story inventory=new Story();inventory.chapter=7;inventory.inventory[9]=23;inventory.iron=17;inventory.buckets=2;inventory.sword=true;
        for(String uid:new String[]{"parent","child","banned"})store.put(base+"/members/"+uid+"/inventory/main",first.inventoryFields(inventory,""));
        store.put(first.syncPath(),new JSONObject().put("regions",OnlineV5.map(new JSONObject().put("s0",OnlineV5.map(FirebaseRest.fields("revision",3000,"updatedAt","unused"))))).put("players",OnlineV5.map(new JSONObject())));
        for(int i=0;i<3000;i++){int x=i%96,z=i/96,type=i%17;store.put(base+"/sectors/0/blocks/"+x+"_30_"+z,FirebaseRest.fields("x",x,"y",30,"z",z,"type",type,"renewable",type==10,"by","parent").put("updatedAt",new JSONObject().put("timestampValue",store.stamp())));}
        store.failMigrationAt=400;boolean quota=false;try{first.modern.start(false,"",2);}catch(FirebaseRest.Failure expected){quota=expected.status==429;}finally{finish(first);}
        JSONObject progress=OnlineV5.fields(store.docs.get(base+"/sync/v5").getJSONObject("fields"),"migration");
        check(quota&&OnlineV5.integer(progress,"count")==200&&store.docs.get(base).getJSONObject("fields").getJSONObject("closed").getBoolean("booleanValue"),"migration quota stop retains the atomic 200-document cursor and frozen legacy source");
        OnlineSession resumed=session(activity,store,invitation,"parent");try{
            resumed.modern.start(false,"",2);ready(resumed);JSONObject state=store.docs.get(base+"/sync/v5").getJSONObject("fields");
            check(FirebaseRest.string(state,"phase").equals("ready")&&!store.docs.get(base).getJSONObject("fields").has("migration")&&store.legacyDocuments==3200,"migration resumes past saved prefix, verifies all cells, then removes freeze atomically");
            check(resumed.inventory.inventory[9]==23&&resumed.inventory.iron==17&&resumed.inventory.buckets==2&&resumed.inventory.sword&&resumed.inventory.chapter==7,"migration preserves resources, progress and tools");
            check(!OnlineV5.fields(state,"access").getJSONObject("banned").getBoolean("booleanValue"),"migration retains banned identities");
            for(int i=0;i<3000;i++)check(resumed.world.get(i%96,30,i/96)==i%17,"migrated block, removal or water cell "+i);
            check(resumed.regions.get(0).renewable.get("10_30_0"),"renewable natural-water flag survives migration");
            int documents=0;for(String key:store.docs.keySet())if(key.contains("/chunks/"))documents++;check(documents==12,"3000 legacy documents compact to twelve spatial chunks");
        }finally{finish(resumed);}
        int queries=store.chunkQueries;OnlineSession warm=session(activity,store,invitation,"parent");try{warm.modern.start(false,"",2);check(store.chunkQueries==queries&&warm.world.get(10,30,10)==(970%17),"migrated world reopens from cache with zero chunk queries");}finally{finish(warm);}
        check(!store.gets.contains(base+"/members/child/inventory/main")&&!store.gets.contains(base+"/members/banned/inventory/main"),"owner migration never reads another private inventory");
        OnlineSession child=session(activity,store,invitation,"child");try{child.modern.start(false,"",2);check(child.inventory.inventory[9]==23&&child.inventory.iron==17&&child.inventory.sword,"child independently adopts its preserved private inventory");check(store.chunkQueries==queries,"child can reuse the same authorized world cache");}finally{finish(child);}
        OnlineSession banned=session(activity,store,invitation,"banned");boolean denied=false;try{banned.modern.start(false,"",2);}catch(IOException expected){denied=true;}finally{finish(banned);}check(denied,"migrated banned identity cannot rejoin");
        System.out.println("V5 MIGRATION: 3000 legacy documents -> 12 chunks; quota-resume, inventory, bans, water and warm cache verified");
    }
    void legacyLifecycle(MainActivity activity)throws Exception{
        Store store=new Store();OnlineWorld invitation=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession first=session(activity,store,invitation,"parent");String base=first.base();
        store.put(base,FirebaseRest.fields("owner","parent","name","Protokoll 3","seed",123,"side",2,"protocol",3,"invite",invitation.invitation,"closed",false));store.put(base+"/members/parent",FirebaseRest.fields("name","parent","banned",false));Story original=new Story();original.inventory[9]=7;store.put(base+"/members/parent/inventory/main",first.inventoryFields(original,""));
        try{first.modern.start(false,"",2);check(FirebaseRest.integer(store.docs.get(base).getJSONObject("fields"),"protocol")==5&&first.inventory.inventory[9]==7,"protocol 3 upgrades through 4 and preserves the private inventory before v5 play");}finally{finish(first);}
        store.docs.get(base).getJSONObject("fields").put("closed",FirebaseRest.value(true));store.docs.get(base+"/sync/v5").getJSONObject("fields").put("open",FirebaseRest.value(false));store.docs.remove(base+"/members/parent");store.docs.remove(base+"/members/parent/inventory/main");
        OnlineSession resume=session(activity,store,invitation,"parent");boolean closed=false;try{resume.modern.start(false,"",2);}catch(IOException expected){closed=true;}
        try{
            check(closed&&resume.deletionOnly&&resume.owner(),"owner can reopen deletion controls after its own membership was already removed");
            java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);String[] error={""};resume.modern.deleteWorld(finished::countDown,message->{error[0]=message;finished.countDown();});
            check(finished.await(30,TimeUnit.SECONDS)&&error[0].isEmpty()&&!store.docs.containsKey(base)&&!store.docs.containsKey(base+"/sync/v5"),"interrupted v5 world deletion removes remaining old and new structures");
        }finally{finish(resume);}
    }

}
