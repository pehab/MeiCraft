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
        int failCheckpointPage=-1,checkpointPages,mechanismConflicts;boolean failReady,conflictBackground,denyMechanics;final List<String> chunkReads=new ArrayList<>();
        final OnlineV5Checks.Store firestore=new OnlineV5Checks.Store(){
            @Override synchronized JSONObject commit(JSONObject body)throws Exception{boolean saved=false;JSONArray w=body.getJSONArray("writes");for(int i=0;i<w.length();i++)if(w.getJSONObject(i).has("update")&&w.getJSONObject(i).getJSONObject("update").getString("name").contains("/saved/"))saved=true;if(saved&&++checkpointPages==failCheckpointPage)throw new FirebaseRest.Failure(429);return super.commit(body);}
        };JSONObject root=new JSONObject();int writes,reads,liveReads;boolean loseReply,offline;long clockOffset;
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
            String uid=URLDecoder.decode(url.getQuery(),"UTF-8").split("auth=fixture-token-",2)[1].split("&",2)[0];
            String[] parts=path.split("/",4);String base=parts[0]+"/"+parts[1]+"/"+parts[2];
            if(parts.length==4&&parts[3].startsWith("storage")&&!uid.equals(parts[1])&&!Boolean.TRUE.equals(get(root,base+"/members/"+uid+"/active"))){holder.status=401;return "{\"error\":\"Permission denied\"}";}
            if(match!=null&&!match.equals(holder.etag)&&!(match.equals("null_etag")&&old==JSONObject.NULL)){holder.status=412;return "{}";}
            if(method.equals("GET")){
                reads++;if(path.contains("/storage/chunks/"))chunkReads.add(path);if(path.endsWith("/live"))liveReads++;String query=URLDecoder.decode(url.getQuery(),"UTF-8");if(query.contains("orderBy=\"rev\"")){
                    JSONObject filtered=new JSONObject();double start=query.contains("startAt=")?Double.parseDouble(query.split("startAt=")[1].split("&")[0]):0,end=Double.parseDouble(query.split("endAt=")[1].split("&")[0]);
                    if(old instanceof JSONObject)for(String key:OnlineV5.keys((JSONObject)old)){JSONObject e=((JSONObject)old).getJSONObject(key);long rev=e.getLong("rev");if(rev>=start&&rev<=end)filtered.put(key,e);}return filtered.length()==0?"null":filtered.toString();
                }if(path.endsWith("/growth/saplings")&&old instanceof JSONObject){JSONObject regions=(JSONObject)old;int max=-1;boolean numeric=true;for(String region:OnlineV5.keys(regions)){if(!region.matches("[0-8]")){numeric=false;break;}max=Math.max(max,Integer.parseInt(region));}if(numeric&&max>=0&&max<regions.length()*2){JSONArray array=new JSONArray();for(int i=0;i<=max;i++)array.put(regions.opt(Integer.toString(i)));return array.toString();}}
                return old.toString();
            }
            JSONObject next=new JSONObject(root.toString());Object value=body.isEmpty()?JSONObject.NULL:new JSONTokener(body).nextValue();if(failReady&&path.endsWith("/storage/control")&&value instanceof JSONObject&&((JSONObject)value).optString("phase").equals("ready")){failReady=false;throw new IOException("fixture migration interrupted before activation");}long now=System.currentTimeMillis()+clockOffset;
            if(method.equals("PATCH")&&value instanceof JSONObject&&((JSONObject)value).optBoolean("head/actors/"+uid+"/mechanic")){
                JSONObject patch=(JSONObject)value;String prefix="head/actors/"+uid+"/";
                if(mechanismConflicts>0){mechanismConflicts--;Object pose=get(root,base+"/live/poseVersion");put(root,base+"/live/poseVersion",(pose instanceof Number?((Number)pose).longValue():0)+1);}
                Object pose=get(root,base+"/live/poseVersion");long version=pose instanceof Number?((Number)pose).longValue():0;
                if(denyMechanics||patch.optLong(prefix+"presenceBase",0)!=version||patch.optLong(prefix+"base",-1)!=((JSONObject)get(root,base+"/storage/head")).getLong("g")){holder.status=401;return "{\"error\":\"Permission denied\"}";}
            }
            if(conflictBackground&&method.equals("PATCH")&&value instanceof JSONObject&&((JSONObject)value).has("head/actors/parent/base")){
                conflictBackground=false;JSONObject head=(JSONObject)get(root,path+"/head");head.put("g",head.getLong("g")+1);holder.status=401;return "{\"error\":\"Permission denied\"}";
            }

            if(method.equals("PATCH")){JSONObject patch=(JSONObject)value;for(String key:OnlineV5.keys(patch))put(next,path+"/"+key,resolve(patch.get(key),get(root,path+"/"+key),now));}
            else put(next,path,resolve(value,old,now));root=next;writes++;if(loseReply){loseReply=false;throw new IOException("fixture lost reply after durable journal commit");}return value.toString();
        }
        static final class Holder {String etag;int status=200;}
        @Override public HttpURLConnection open(URL url)throws IOException{
            if(!url.getHost().endsWith("firebasedatabase.app"))return firestore.open(url);
            return new HttpURLConnection(url){final ByteArrayOutputStream body=new ByteArrayOutputStream();final Holder result=new Holder();String response;boolean evaluated;
                @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}@Override public OutputStream getOutputStream(){return body;}
                void evaluate()throws IOException{if(evaluated)return;evaluated=true;try{String effective=getRequestProperty("X-HTTP-Method-Override");response=request(url,effective==null?getRequestMethod():effective,body.toString("UTF-8"),getRequestProperty("if-match"),result);}catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}}
                @Override public int getResponseCode()throws IOException{evaluate();return result.status;}@Override public InputStream getInputStream()throws IOException{evaluate();return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}@Override public InputStream getErrorStream(){return new ByteArrayInputStream((response==null?"{}":response).getBytes(StandardCharsets.UTF_8));}
                @Override public String getHeaderField(String key){if(key.equalsIgnoreCase("ETag"))return result.etag;return null;}
            };
        }
    }
    OnlineSession session(MainActivity activity,Backend backend,OnlineWorld invite,String uid)throws Exception{
        FirebaseRest f=new FirebaseRest(activity,backend);f.uid=uid;f.token="fixture-token-"+uid;f.expires=System.currentTimeMillis()+3600000;
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
            v(a).start(true,"Journal-Test",2);ready(a);stock(v(a));
            check(Backend.get(backend.root,"worlds/parent/"+invite.id+"/members/child")==JSONObject.NULL,"First-time guest has no pre-existing RTDB membership");
            v(b).start(false,"",2);ready(b);stock(v(b));
            check(Boolean.TRUE.equals(Backend.get(backend.root,v(b).base+"/members/child/active"))&&v(b).activated&&b.world!=null,"First-time guest proves membership before protected journal reads and loads the world");
            check(FirebaseRest.integer(backend.firestore.docs.get(a.base()).getJSONObject("fields"),"protocol")==6&&!backend.firestore.docs.get(a.base()).getJSONObject("fields").has("migration"),"v6 migration freezes old writers and clears its marker only after verification");
            OnlineV6 initial=v(a);long revision=initial.revision;Story untouched=OnlineInventory.copy(a.inventory);backend.conflictBackground=true;
            List<OnlineChange> stale=Collections.singletonList(new OnlineChange(initial.sequence+1,0,20,44,10,0,false,OnlineWorld.randomId(),"","parent",""));boolean deferred=false;
            try{initial.nature.publish(JournalBatch.patch(initial,stale).put("head/actors/parent/base",initial.fetchHead().generation),stale);}catch(OnlineNature.Deferred expected){deferred=true;}
            check(deferred&&initial.revision==revision&&OnlineInventory.same(untouched,a.inventory)&&a.firebase.token.equals("fixture-token-parent"),"A proven background generation conflict is deferred without charging resources or invalidating the valid token");
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
        mechanics(activity,game);deleteRecovery(activity,backend,invite);largeUpload(activity);resumeMigration(activity,game);nature(activity,game);materials(activity,game);resources(activity,game);
        System.out.println("JOURNAL CHECKS: migration, zero-write builds, lost replies, ring gaps, coherent checkpoints, host takeover and warm restart verified");
    }
    void mechanics(MainActivity activity,GameView game)throws Exception{
        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession a=session(activity,backend,invite,"parent"),b=session(activity,backend,invite,"child");
        try{
            v(a).start(true,"Mechanics",2);ready(a);v(b).start(false,"",2);ready(b);stock(v(a));a.px=60;a.py=40;a.pz=60;
            World w=a.world;Story inventory=OnlineInventory.copy(a.inventory);String prefix=v(a).storage()+"/chunks/0_";
            // Seed authored blocks above the landscape, including a two-material group across a region edge.
            for(int[] c:new int[][]{{90,44,90,14},{94,44,90,7},{95,44,90,8}}){OnlineChange e=new OnlineChange(v(a).sequence+1,0,c[0],c[1],c[2],c[3],false,OnlineWorld.randomId(),"","parent","user");v(a).commit(a.inventory,e);v(a).readSync();v(a).syncVisible(false);test.onGl(game,()->{});}
            Mechanics.Link link=new Mechanics.Link(new Mechanics.Block(90,44,90,14),List.of(new Mechanics.Block(94,44,90,7),new Mechanics.Block(95,44,90,8)),0,2,false,"Gartentor");OnlineMechanics m=new OnlineMechanics(v(a));m.configureNow(link);test.onGl(game,()->{});
            check(w.mechanics.links.containsKey(w.idx(90,44,90)),"online switch definition enters the shared region model");
            backend.chunkReads.clear();OnlineMechanics.Snapshot snapshot=new OnlineMechanics(v(a)).new Snapshot(link,false,true);
            check(snapshot.chunks.equals(new HashSet<>(List.of("0_5_2_5","1_0_2_5")))&&backend.chunkReads.size()==1&&backend.chunkReads.get(0).endsWith("/0_5_2_5"),"mechanism snapshot covers only its two swept-space chunks and skips the empty chunk download");
            backend.mechanismConflicts=1;m.toggleNow(link);test.onGl(game,()->{});v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            check(w.get(94,44,90)==0&&w.get(96,44,90)==7&&w.get(97,44,90)==8&&b.world.get(97,44,90)==8,"one atomic mechanism crosses regions and reaches the peer");
            check(OnlineInventory.same(inventory,a.inventory),"mechanical motion preserves the entire private inventory");
            check(b.world.mechanics.links.get(b.world.idx(90,44,90)).name.equals("Gartentor"),"mechanism name is shared with the second player");
            backend.loseReply=true;m.toggleNow(link.toggle());test.onGl(game,()->{});check(w.get(94,44,90)==7&&w.get(95,44,90)==8&&w.get(97,44,90)==0,"second activation returns the original two materials");
            backend.denyMechanics=true;int writes=backend.writes;boolean denied=false;try{m.toggleNow(link);}catch(FirebaseRest.Failure expected){denied=true;}backend.denyMechanics=false;check(denied&&backend.writes==writes,"unproven permission failures are not retried or disguised as successful movement");
            Mechanics.Link renamed=new Mechanics.Link(link.button,link.blocks,link.direction,link.distance,false,"Hintertür");
            m.rename(link,renamed,null);drain(a,game);link=renamed;check(w.mechanics.links.get(w.idx(90,44,90)).name.equals("Hintertür"),"renaming updates the durable switch metadata");
            List<Mechanics.Block> panel=new ArrayList<>();
            for(int y=44;y<47;y++)for(int z=80;z<83;z++)panel.add(new Mechanics.Block(80,y,z,7));
            List<Mechanics.Block> seeded=new ArrayList<>(panel);Mechanics.Block doorSwitch=new Mechanics.Block(75,44,80,14);seeded.add(doorSwitch);
            for(Mechanics.Block cell:seeded){v(a).commit(a.inventory,new OnlineChange(v(a).sequence+1,0,cell.x,cell.y,cell.z,cell.type,false,OnlineWorld.randomId(),"","parent","user"));v(a).readSync();}
            Mechanics.Link door=new Mechanics.Link(doorSwitch,panel,0,1,false,"3x3 Tür");m.configureNow(door);m.toggleNow(door);test.onGl(game,()->{});v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            for(Mechanics.Block cell:panel)check(w.get(cell.x,cell.y,cell.z)==0&&w.get(cell.x+1,cell.y,cell.z)==7&&b.world.get(cell.x+1,cell.y,cell.z)==7,"nine-block door reaches both players despite wrapping the live ring");
            m.toggleNow(door.toggle());test.onGl(game,()->{});for(Mechanics.Block cell:panel)check(w.get(cell.x,cell.y,cell.z)==7&&w.get(cell.x+1,cell.y,cell.z)==0,"nine-block door returns every block");
            v(a).checkpoints.lease();v(a).checkpoints.save(true);
            JSONObject saved=backend.firestore.docs.get(a.base()+"/sync/checkpoint").getJSONObject("fields");boolean marker=false;for(String id:OnlineV5.keys(OnlineV5.fields(saved,"chunks"))){String version=FirebaseRest.string(OnlineV5.fields(saved,"chunks"),id);JSONObject cells=OnlineV5.fields(backend.firestore.docs.get(a.base()+"/saved/"+version).getJSONObject("fields"),"cells");for(String key:OnlineV5.keys(cells))if(FirebaseRest.string(cells,key).contains("mech_"))marker=true;}check(marker,"checkpoint retains mechanism definition with its switch cell");
        }finally{finish(a);finish(b);}
        OnlineSession warm=session(activity,backend,invite,"parent");try{v(warm).start(false,"",2);test.onGl(game,()->{});check(warm.world.mechanics.links.containsKey(warm.world.idx(90,44,90)),"warm region cache retains switch metadata after reconnect");}finally{finish(warm);}
    }
    void resources(MainActivity activity,GameView game)throws Exception{
        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession seed=session(activity,backend,invite,"parent");v(seed).start(true,"Resources",2);finish(seed);
        // Model an already-migrated original world: no terrain field, no block overrides.
        backend.firestore.docs.get("onlineWorlds/"+invite.id).getJSONObject("fields").remove("terrain");backend.firestore.docs.get("onlineWorlds/"+invite.id).getJSONObject("fields").put("seed",FirebaseRest.value(1234));backend.firestore.docs.get("onlineWorlds/"+invite.id+"/sync/v5").getJSONObject("fields").put("seed",FirebaseRest.value(1234));
        OnlineSession a=session(activity,backend,invite,"parent"),b=session(activity,backend,invite,"child");
        try{
            v(a).start(false,"",2);ready(a);v(b).start(false,"",2);ready(b);OnlineV6 engine=v(a);check(a.terrainVersion==1,"resource retrofit retains the original generator of an existing migrated world");Story inventory=OnlineInventory.copy(a.inventory);
            World original=OnlineTerrain.region(a.seed,0,2,1);ResourceRetrofit.Space clear=new ResourceRetrofit.Space(){public int get(int x,int y,int z){return original.get(x,y,z);}public boolean occupied(int x,int y,int z){return false;}public boolean touched(int x,int y,int z){return false;}};
            java.util.List<ResourceRetrofit.Cell> vulnerable=ResourceRetrofit.plan(original,clear,0,2,0);if(!vulnerable.isEmpty()){
                ResourceRetrofit.Cell cell=vulnerable.get(0);OnlineChange building=new OnlineChange(engine.sequence+1,0,cell.x,cell.y,cell.z,7,false,OnlineWorld.randomId(),"","parent","user");engine.commit(a.inventory,building);engine.readSync();engine.syncVisible(false);test.onGl(game,()->{});
            }
            int quietReads=backend.reads,quietWrites=backend.writes;a.pending=true;
            engine.resources.tick();engine.nature.changed(0);engine.nature.decay();
            check(backend.reads==quietReads&&backend.writes==quietWrites&&engine.nature.failure==null&&engine.resources.failures==0,"Queued player actions defer nature and resource work before any network call without consuming retries");
            a.pending=false;
            backend.loseReply=true;try{engine.resources.wave();throw new AssertionError("Expected a lost resource reply");}catch(IOException expected){engine.resources=new OnlineResources(engine);}
            engine.resources.wave(true);JSONObject partial=engine.resources.cursors;int progressed=0;for(String key:OnlineV5.keys(partial))if(partial.getJSONObject(key).optInt("next")>0)progressed++;
            check(progressed<=1,"One scheduled enrichment step advances at most one region before yielding the serial worker");
            int waves=0;while(engine.resources.wave()&&waves++<15)test.onGl(game,()->{});test.onGl(game,()->{});check(engine.resources.done&&waves<15,"finite resource work resumes until all region cursors are complete");
            int coal=0,crystals=0,lava=0;JournalHead head=engine.fetchHead();for(String chunk:OnlineV5.keys(head.chunks))for(String value:JournalHead.cells(engine.remote(engine.storage()+"/chunks/"+chunk)).values()){int type=Integer.parseInt(value.split(":")[0]);if(type==17)coal++;if(type==20)crystals++;if(type==15)lava++;}
            check(coal>0&&crystals>0&&lava>0&&OnlineInventory.same(inventory,a.inventory),"existing online world gains coal, crystal caves and lava without resource gifts or deductions");
            if(!vulnerable.isEmpty()){ResourceRetrofit.Cell cell=vulnerable.get(0);check(a.world.get(cell.x,cell.y,cell.z)==7,"existing building blocks its deterministic resource site");}
            v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});check(java.util.Arrays.equals(a.world.blocks,b.world.blocks),"all confirmed visible resource additions reach the second player");long generation=head.generation;int reads=backend.reads;check(!engine.resources.wave()&&backend.reads==reads&&engine.fetchHead().generation==generation,"completed worker does no further polling or enrichment");
            engine.checkpoints.lease();engine.checkpoints.save(true);
        }finally{finish(a);finish(b);}
        OnlineSession warm=session(activity,backend,invite,"parent");try{v(warm).start(false,"",2);long generation=v(warm).fetchHead().generation;check(!v(warm).resources.wave()&&v(warm).fetchHead().generation==generation,"owner restart and checkpoint reload never duplicate deposits");Backend.put(backend.root,v(warm).storage()+"/resources",JSONObject.NULL);v(warm).resources=new OnlineResources(v(warm));int waves=0;while(v(warm).resources.wave()&&waves++<15){}check(waves<15&&v(warm).fetchHead().generation==generation,"checkpointed cell history reconstructs lost secondary progress without refilling deposits");}finally{finish(warm);}
    }
    void materials(MainActivity activity,GameView game)throws Exception{
        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession a=session(activity,backend,invite,"parent"),b=session(activity,backend,invite,"child");
        try{
            OnlineV6 engine=v(a);engine.start(true,"Materials",2);ready(a);v(b).start(false,"",2);ready(b);
            check(a.terrainVersion==2&&b.terrainVersion==2&&a.world.terrainVersion==2,"New online worlds persist and share generation version 2");
            Story stock=OnlineInventory.copy(a.inventory);stock.chapter=7;stock.inventory[18]=2;stock.inventory[15]=5;stock.inventory[4]=2;stock.inventory[5]=1;stock.inventory[10]=2;stock.inventory[8]=2;stock.inventory[20]=2;engine.commit(stock,null);engine.readSync();
            check(engine.head.actors.getJSONObject("parent").getString("inventory").startsWith("2."),"New materials use the versioned authenticated inventory codec");
            engine.edit(48,44,48,15);drain(a,game);a.healthy=true;engine.edit(48,45,48,18);drain(a,game);Story before=OnlineInventory.copy(a.inventory);int poses=backend.liveReads;
            for(int i=0;i<3;i++){engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});}
            check(a.world.get(48,45,48)==21&&OnlineInventory.same(before,a.inventory),"Coal over lava becomes ember stone without additional resource debits or refunds");
            check(backend.liveReads==poses,"Heating existing solids does not fetch redundant player positions");
            check(!engine.nature.reactionPending.contains(0),"Completed fire reaction has no persistent polling task");
            a.healthy=true;engine.edit(95,44,48,8);drain(a,game);a.healthy=true;engine.edit(96,44,48,20);drain(a,game);
            for(int i=0;i<3;i++){engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});}
            v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            check(a.world.get(95,44,48)==22&&a.world.get(96,44,48)==0&&b.world.get(95,44,48)==22&&b.world.get(96,44,48)==0,"Crystal glass consumes its input atomically across a region boundary and reaches peers");
            a.healthy=true;engine.edit(50,44,48,15);drain(a,game);a.healthy=true;engine.edit(51,44,48,10);drain(a,game);engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});check(a.world.get(50,44,48)==19,"Authored online water/lava contact produces durable basalt");
            a.healthy=true;engine.edit(54,44,48,15);drain(a,game);a.healthy=true;engine.edit(54,45,48,5);drain(a,game);Story woodInventory=OnlineInventory.copy(a.inventory);int woodPoses=backend.liveReads;
            for(int i=0;i<3;i++){engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});}
            check(a.world.get(54,45,48)==18&&OnlineInventory.same(woodInventory,a.inventory)&&backend.liveReads==woodPoses,"wood over lava chars into collectible coal without gifts or extra pose reads");
            for(int i=0;i<4;i++){engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});}v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});check(a.world.get(54,45,48)==18&&b.world.get(54,45,48)==18&&a.world.get(54,44,48)==15,"fresh charcoal remains stable over lava and agrees for both players");
            a.healthy=true;engine.edit(60,44,48,15);drain(a,game);a.healthy=true;engine.edit(60,45,48,4);drain(a,game);
            a.healthy=true;engine.edit(95,44,60,15);drain(a,game);a.healthy=true;engine.edit(96,44,60,4);drain(a,game);
            Story sandInventory=OnlineInventory.copy(a.inventory);int sandPoses=backend.liveReads;
            engine.nature.changed(0);engine.nature.changed(1);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});
            v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});
            check(a.world.get(60,45,48)==8&&b.world.get(60,45,48)==8&&a.world.get(60,44,48)==15,"sand directly above lava becomes durable glass for both online players without consuming lava");
            check(a.world.get(96,44,60)==8&&b.world.get(96,44,60)==8&&a.world.get(95,44,60)==15,"side contact across a region boundary also melts sand and reaches peers");
            check(OnlineInventory.same(sandInventory,a.inventory)&&backend.liveReads==sandPoses,"sand conversion grants no inventory items and fetches no redundant player positions");
            engine.checkpoints.lease();engine.checkpoints.save(true);
        }finally{finish(a);finish(b);}
        OnlineSession warm=session(activity,backend,invite,"parent");try{v(warm).start(false,"",2);check(warm.terrainVersion==2&&warm.world.get(95,44,48)==22&&warm.world.get(50,44,48)==19&&warm.world.get(54,45,48)==18&&warm.inventory.inventory[18]==1,"New terrain, reaction outputs and version-2 private resources survive warm restart");for(int i=0;i<3;i++){v(warm).nature.changed(0);v(warm).nature.decay();if(v(warm).nature.failure!=null)throw v(warm).nature.failure;test.onGl(game,()->{});}check(warm.world.get(54,45,48)==18,"checkpointed fresh charcoal stays collectible after restart");check(warm.world.get(60,45,48)==8&&warm.world.get(96,44,60)==8,"online glass produced from sand survives checkpoint and warm restart");}finally{finish(warm);}
    }
    void nature(MainActivity activity,GameView game)throws Exception{
        JSONObject tree=new JSONObject().put("48_30_48",new JSONObject().put("stage",1));JSONArray dense=new JSONArray().put(tree).put(JSONObject.NULL).put(tree);
        JSONObject normalized=OnlineNature.saplingRegions(dense,3);check(normalized.has("0")&&!normalized.has("1")&&normalized.has("2"),"dense numeric regions retain keys and skip RTDB null gaps");
        JSONObject sparse=new JSONObject().put("8",tree);check(OnlineNature.saplingRegions(sparse,3).getJSONObject("8").has("48_30_48"),"sparse high region object remains readable");
        check(OnlineNature.saplingRegions(JSONObject.NULL,3).length()==0&&OnlineNature.saplingRegions(new JSONArray(),3).length()==0,"absent and empty sapling lists are harmless");
        for(Object invalid:List.of(Boolean.TRUE,new JSONArray().put(7),new JSONObject().put("9",tree),new JSONObject().put("0",new JSONArray()))){boolean denied=false;try{OnlineNature.saplingRegions(invalid,3);}catch(IOException expected){denied=true;}check(denied,"malformed numeric-region data is rejected without modifying trees");}

        Backend backend=new Backend();OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());OnlineSession a=session(activity,backend,invite,"parent"),b=session(activity,backend,invite,"child");
        try{
            OnlineV6 engine=v(a);engine.start(true,"Nature",2);ready(a);v(b).start(false,"",2);ready(b);
            Story stock=OnlineInventory.copy(a.inventory);stock.chapter=7;stock.inventory[6]=10;engine.commit(stock,null);engine.readSync();
            OnlineChange soil=new OnlineChange(engine.sequence+1,0,48,29,48,2,false,OnlineWorld.randomId(),"","parent");engine.commit(a.inventory,soil);engine.readSync();engine.syncVisible(false);test.onGl(game,()->{});
            engine.edit(48,30,48,6);drain(a,game);JSONObject record=(JSONObject)Backend.get(backend.root,engine.storage()+"/growth/saplings/0/48_30_48");check(record.getInt("stage")==0&&a.world.get(48,30,48)==6,"Player planting atomically registers a leaf sapling");
            int inventory=a.inventory.inventory[6];long start=engine.day();
            for(int stage=1;stage<=3;stage++){
                engine.nature=new OnlineNature(engine);engine.db.serverOffset=stage*TreeGrowth.DAY;
                if(stage==2){Backend.put(backend.root,engine.base+"/live/players/child",new JSONObject().put("sector",0).put("x",48.5).put("y",33.6).put("z",48.5).put("t",System.currentTimeMillis()+engine.db.serverOffset));engine.nature.daily();JSONObject blocked=(JSONObject)Backend.get(backend.root,engine.storage()+"/growth/saplings/0/48_30_48");check(blocked.getInt("stage")==1&&engine.nature.pendingTrees,"blocked second growth stage remains eligible after the scan finishes");Backend.put(backend.root,engine.base+"/live/players/child",JSONObject.NULL);}
                engine.nature.daily();test.onGl(game,()->{});record=(JSONObject)Backend.get(backend.root,engine.storage()+"/growth/saplings/0/48_30_48");check(record.getInt("stage")==stage&&record.getLong("lastGrowthDay")==start+stage,"Daily worker commits stage "+stage+" with UTC cursor");
                long generation=engine.fetchHead().generation;engine.nature.daily();check(engine.fetchHead().generation==generation,"Same-day login cannot grow again");
            }
            engine.nature.dailyStep();int quiet=backend.reads;engine.nature.checkDay();check(quiet==backend.reads,"completed tree/day checks create no periodic network polling");
            v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});check(b.world.get(48,33,48)==5&&b.world.get(49,34,48)==6&&a.inventory.inventory[6]==inventory,"Whole grown tree reaches peer with unchanged inventory");
            engine.checkpoints.lease();engine.checkpoints.save(true);JSONObject checkpoint=backend.firestore.docs.get(a.base()+"/sync/checkpoint").getJSONObject("fields");boolean provenance=false;for(String id:OnlineV5.keys(OnlineV5.fields(checkpoint,"chunks"))){String path=a.base()+"/saved/"+FirebaseRest.string(OnlineV5.fields(checkpoint,"chunks"),id);JSONObject cells=OnlineV5.fields(backend.firestore.docs.get(path).getJSONObject("fields"),"cells");if(cells.has("48_30_48"))provenance=FirebaseRest.string(cells,"48_30_48").contains(":tree_3_");}check(provenance,"Checkpoint retains root stage and crown provenance");
            for(int y=30;y<=33;y++){a.healthy=true;engine.edit(48,y,48,0);drain(a,game);check(a.world.get(48,y,48)==0,"Trunk harvest committed at height "+y+"; "+FirebaseRest.DIAGNOSTICS.report());}
            int waves=0;while(a.world.get(49,34,48)==6&&waves++<1024){
                long before=engine.fetchHead().region(0);engine.nature.changed(0);engine.nature.decay();if(engine.nature.failure!=null)throw engine.nature.failure;test.onGl(game,()->{});
                long removed=engine.fetchHead().region(0)-before;check(removed>=1&&removed<=4,"One confirmed decay wave removes at most four leaves, including old floating crowns");
            }
            check(waves>1&&waves<1024,"Decay progresses gradually through old floating leaves and the newly grown crown");
            v(b).readSync();v(b).syncVisible(false);test.onGl(game,()->{});check(a.world.get(49,34,48)==0&&b.world.get(49,34,48)==0,"Progressive crown decay is durable and identical for peers; local="+a.world.get(49,34,48)+", peer="+b.world.get(49,34,48)+"; "+FirebaseRest.DIAGNOSTICS.report());
        }finally{finish(a);finish(b);}
        OnlineSession restored=session(activity,backend,invite,"parent");try{v(restored).start(false,"",2);check(restored.world.get(49,34,48)==0,"Nature mutations survive warm restart");}finally{finish(restored);}
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
