package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

/** Durable RTDB cells + atomic resource journal; Firestore contains immutable coherent checkpoints. */
final class OnlineV6 extends OnlineV5 implements AutoCloseable {
    RtdbRest db;String base,epoch;volatile boolean activated,journalConnected,stopped;volatile String latest="";
    boolean cachePrimed=true;JournalHead head;final ExecutorService journalReader=Executors.newSingleThreadExecutor();OnlineCheckpoint checkpoints;
    final OnlineMechanics mechanisms=new OnlineMechanics(this);
    final boolean services;OnlineNature nature;OnlineResources resources;
    OnlineV6(OnlineSession s){this(s,true);}
    OnlineV6(OnlineSession s,boolean services){super(s);this.services=services;}
    String storage(){return base+"/storage";}
    JSONObject object(Object raw)throws IOException{if(raw==JSONObject.NULL)return new JSONObject();if(!(raw instanceof JSONObject))throw new IOException("Ungültige RTDB-Antwort.");return (JSONObject)raw;}
    JSONObject remote(String path)throws Exception{return object(db.request("GET",path,null));}
    @Override void start(boolean create,String name,int side)throws Exception{
        int protocol=5;boolean migrating=false;
        if(!create){
            JSONObject member=f.get(s.member(f.uid)),root=null;
            if(member!=null&&member.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue"))throw new IOException("Du wurdest aus dieser Welt entfernt.");
            if(member==null){try{root=f.get(s.base());}catch(FirebaseRest.Failure e){if(e.status!=403)throw e;}if(root==null)f.commit(new JSONArray().put(s.memberWrite()));}
            if(root==null)root=f.get(s.base());if(root==null)throw new IOException("Diese Welt existiert nicht.");JSONObject data=root.getJSONObject("fields");
            s.terrainVersion=data.has("terrain")?FirebaseRest.integer(data,"terrain"):1;if(s.terrainVersion<1||s.terrainVersion>2)throw new IOException("Bitte App aktualisieren: Landschaftsversion.");s.owner=FirebaseRest.string(data,"owner");s.name=FirebaseRest.string(data,"name");s.seed=integer(data,"seed");s.side=FirebaseRest.integer(data,"side");s.invitation=new OnlineWorld(s.invitation.id,FirebaseRest.string(data,"invite"));protocol=FirebaseRest.integer(data,"protocol");migrating=data.has("migration")&&FirebaseRest.string(data,"migration").equals("rtdb6");s.deletionOnly=data.getJSONObject("closed").getBoolean("booleanValue");
            if(s.deletionOnly&&protocol<=5&&data.has("migration")&&FirebaseRest.string(data,"migration").equals("v5"))s.deletionOnly=false;
            if(s.deletionOnly)throw new IOException("Diese Welt wurde geschlossen.");
            if(protocol<6&&!s.owner())throw new IOException("Bitte zuerst den Weltbesitzer mit dieser Version verbinden lassen, damit der dauerhafte RTDB-Spielstand eingerichtet wird.");
            if(protocol==6){JSONObject frozen=f.get(path());state=frozen.getJSONObject("fields");}else if(protocol>6)throw new IOException("Bitte App aktualisieren.");
        }
        if(create||protocol<6)super.start(create,name,side);
        db=new RtdbRest(f);base="worlds/"+s.owner+"/"+s.invitation.id;
        // First-time guests must prove their invitation before reading protected storage.
        try(OnlineRealtime presence=new OnlineRealtime(s)){presence.initialize();}
        JSONObject status=remote(storage()+"/control");
        if(protocol==6&&!migrating&&!status.optString("phase").equals("ready"))throw new IOException("Der dauerhafte RTDB-Spielstand fehlt. Kein automatischer Rückgriff auf einen älteren Firestore-Stand. Bitte Datenbank und Checkpoint prüfen.");
        if(create||protocol<6||migrating){
            if(!s.owner())throw new IOException("Die Migration läuft noch. Bitte den Weltbesitzer verbinden lassen.");new JournalMigration(this).run();
        }
        JSONObject control=remote(storage()+"/control");if(!control.getString("phase").equals("ready"))throw new IOException("Migration noch nicht abgeschlossen.");epoch=control.getString("epoch");
        activated=true;readSync();
        if(s.world!=null)cachePrimed=false;if(s.regionCache!=null)s.regionCache.close();s.regionCache=new OnlineRegionCache(new File(s.activity.getCacheDir(),"online-regions-v6-"+epoch+"-t"+s.terrainVersion),s.invitation.id,s.seed,s.side);
        boolean fresh=s.world==null;if(fresh){s.world=World.onlineSpace(s.seed,s.side,s.terrainVersion);World origin=OnlineTerrain.region(s.seed,0,s.side,s.terrainVersion);s.world.x=origin.x;s.world.y=origin.y;s.world.z=origin.z;s.px=origin.x;s.py=origin.y;s.pz=origin.z;}
        OnlineInventory.apply(s.world.progress,s.inventory);syncVisible(fresh);s.remember();
        nature=new OnlineNature(this);resources=new OnlineResources(this);if(services){resources.start();nature.join();}checkpoints=new OnlineCheckpoint(this);if(services){checkpoints.start();journalReader.execute(this::listen);}
    }
    void listen(){java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();RealtimeTree tree=new RealtimeTree();
        while(!stopped&&!s.closed){
            if(!s.foreground){journalConnected=false;LockSupport.parkNanos(200000000);continue;}
            try{
                db.listen(storage()+"/head",(type,payload)->{
                    if(stopped||!s.foreground)throw new IOException("Journal pausiert.");if(type.equals("keep-alive")){failures.set(0);return;}
                    if(type.equals("auth_revoked")){f.invalidateToken(f.token);throw new IOException("Journal-Anmeldung erneuern.");}if(type.equals("cancel"))throw new IOException("Journal-Zugriff beendet.");
                    if(!type.equals("put")&&!type.equals("patch"))return;
                    try{tree.apply(type,payload);FirebaseRest.DIAGNOSTICS.realtimeEvent();long g=tree.root.optLong("g",-1);if(g>=0){latest=tree.root.toString();boolean resumed=!journalConnected;journalConnected=true;failures.set(0);if(resumed){FirebaseRest.DIAGNOSTICS.journal(true);s.realtimeStatus();}s.requestRealtimeSync();}}
                    catch(JSONException e){throw new IOException("Ungültiger Journal-Stream.",e);}
                });
            }catch(Exception e){if(!stopped&&s.foreground){try{if(Boolean.TRUE.equals(db.request("GET",base+"/meta/closed",null))){s.problem(new IOException("Diese Welt wurde geschlossen."),true);return;}}catch(Exception ignored){/* Own membership / Firestore diagnostics explain revoked access. */}failures.updateAndGet(n->Math.min(6,n+1));}}
            finally{boolean lost=journalConnected;journalConnected=false;FirebaseRest.DIAGNOSTICS.journal(false);if(lost)s.realtimeStatus();}
            long until=System.nanoTime()+Math.min(60000,1000L<<Math.min(5,failures.get()))*1000000L;while(!stopped&&s.foreground&&System.nanoTime()<until)LockSupport.parkNanos(100000000);
        }
    }
    JournalHead fetchHead()throws Exception{return new JournalHead(remote(storage()+"/head"));}
    void accept(JournalHead next)throws Exception{
        if(head!=null&&next.generation<head.generation)return;JSONObject me=next.actors.optJSONObject(f.uid);
        if(me!=null&&me.getLong("rev")<revision)return;
        if(nature!=null&&head!=null&&s.world!=null&&next.generation>head.generation)for(OnlineChange c:next.tail){
            if(c.sequence<=head.heads.getOrDefault(c.actor,0L)||c.type!=0||!s.readyRegions.contains(c.region))continue;
            int gx=c.region%s.side*World.W+c.x,gz=c.region/s.side*World.D+c.z;
            if(s.world.get(gx,c.y,gz)==5)nature.changed(c.region);
        }
        head=next;heads.clear();heads.putAll(next.heads);tail.clear();tail.addAll(next.tail);s.announcedReadTime=java.time.Instant.EPOCH.toString();
        if(me==null||me.optString("inventory").isEmpty()){
            Story initial=new Story();JSONObject legacy=f.get(s.inventoryPath());if(legacy!=null)initial=s.decodeInventory(legacy.getJSONObject("fields"));
            revision=me==null?0:me.getLong("rev");sequence=me==null?0:me.getLong("seq");commit(initial,null);head=fetchHead();me=head.actors.getJSONObject(f.uid);heads.clear();heads.putAll(head.heads);tail.clear();tail.addAll(head.tail);
        }
        long rev=me.getLong("rev");if(s.inventoryVersion.isEmpty()||revision!=rev){s.inventory=cipher(false).decrypt(me.getString("inventory"),s.invitation.id,f.uid,rev);revision=rev;s.inventoryVersion=Long.toString(rev);if(s.world!=null)s.applyInventory(s.inventory);}sequence=me.getLong("seq");s.inventoryUncertain=false;
    }
    @Override void readSync()throws Exception{
        if(!activated){super.readSync();return;}
        JournalHead next=!journalConnected||s.inventoryUncertain||latest.isEmpty()?fetchHead():new JournalHead(new JSONObject(latest));accept(next);
    }
    @Override void heartbeat()throws Exception{if(!activated)super.heartbeat();}
    @Override void syncVisible(boolean initial)throws Exception{
        if(!activated){super.syncVisible(initial);return;}World target=s.world;if(target==null)return;
        try{for(int id:OnlineWorld.visibleSectors(s.px,s.pz,s.side,82)){
            if(s.closed)return;OnlineSession.Region r=s.regions.get(id);if(r==null){r=new OnlineSession.Region(id);s.regions.put(id,r);OnlineRegionCache.Snapshot cache=s.regionCache==null?null:s.regionCache.load(id);if(cache!=null){r.versions.putAll(cache.versions);r.types.putAll(cache.types);r.renewable.putAll(cache.renewable);r.heads.putAll(cache.heads);r.tags.putAll(cache.tags);r.revision=cache.revision;r.complete=true;FirebaseRest.DIAGNOSTICS.cacheRestored(cache.types.size());}}
            long announced=head.region(id);if(r.revision>announced)throw new IOException("Weltstand zurückgesetzt. Bitte erneut beitreten.");World tile=r.generated?null:OnlineTerrain.region(s.seed,id,s.side,s.terrainVersion);if(tile!=null){s.streaming=true;for(String key:r.types.keySet()){String[] xyz=key.split("_");int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);tile.set(x,y,z,r.types.get(key));metadata(r,x,y,z,r.types.get(key),r.tags.getOrDefault(key,""));}}
            boolean changed=announced!=r.revision||!r.complete;List<OnlineChange> delta=r.complete?OnlineChange.reconcile(id,r.revision,announced,r.heads,r.versions,r.own,tail):null;
            if(delta==null)loadRegion(r,tile);else{for(OnlineChange c:delta)cell(r,tile,c.x,c.y,c.z,c.type,c.renewable,c.token,c.nature);if(!delta.isEmpty())FirebaseRest.DIAGNOSTICS.tailApplied(delta.size());}
            if(changed&&nature!=null)nature.changed(id);
            r.revision=head.region(id);r.ownChanges=0;r.own.clear();r.complete=true;r.heads.clear();r.heads.putAll(head.heads);
            if((changed||!cachePrimed)&&s.regionCache!=null)s.regionCache.offer(id,new OnlineRegionCache.Snapshot(r.revision,java.time.Instant.EPOCH.toString(),r.versions,r.types,r.renewable,r.heads,r.tags));
            if(tile!=null){r.generated=true;if(initial){OnlineInventory.install(target,tile,id,s.side);s.markReady(id);}else s.activity.game.gl.queueEvent(()->{if(!s.closed&&s.world==target){synchronized(target){OnlineInventory.install(target,tile,id,s.side);}for(int x=id%s.side*World.W;x<(id%s.side+1)*World.W;x+=16)for(int z=id/s.side*World.D;z<(id/s.side+1)*World.D;z+=16)s.activity.game.renderer.dirty(x,z);s.markReady(id);}});}
        }cachePrimed=true;}finally{s.streaming=false;}
    }
    void loadRegion(OnlineSession.Region region,World tile)throws Exception{
        for(int attempt=0;attempt<5;attempt++){
            JournalHead before=fetchHead();Map<String,String> values=new HashMap<>();
            for(String id:keys(before.chunks))if(id.startsWith(region.id+"_"))values.putAll(JournalHead.cells(remote(storage()+"/chunks/"+id)));
            JournalHead after=fetchHead();List<OnlineChange> delta=OnlineChange.reconcile(region.id,before.region(region.id),after.region(region.id),before.heads,JournalHead.versions(values),Collections.emptyList(),after.tail);
            if(delta==null){delta=OnlineChange.reconcile(region.id,before.region(region.id),after.region(region.id),before.heads,JournalHead.versions(values),Collections.emptyList(),JournalSnapshot.history(db,storage(),before,after));}if(delta==null)continue;for(OnlineChange c:delta)values.put(c.key(),c.value());
            for(String key:values.keySet()){String[] xyz=key.split("_"),value=values.get(key).split(":");cell(region,tile,Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),Integer.parseInt(value[0]),value[1].equals("1"),value[2],value.length>3?value[3]:"");}
            accept(after);return;
        }throw new IOException("Viele gleichzeitige Änderungen. Bereich wird erneut abgeglichen.");
    }
    void metadata(OnlineSession.Region r,int x,int y,int z,int type,String tag)throws Exception{
        String key=x+"_"+y+"_"+z;if(tag.isEmpty())r.tags.remove(key);else r.tags.put(key,tag);
        World world=s.world;if(world==null)return;int gx=r.id%s.side*World.W+x,gz=r.id/s.side*World.D+z;
        synchronized(world){world.mechanics.update(world,gx,y,gz,type,tag);}
    }
    void cell(OnlineSession.Region r,World tile,int x,int y,int z,int type,boolean water,String token,String tag)throws Exception{
        super.cell(r,tile,x,y,z,type,water,token);metadata(r,x,y,z,type,tag);
    }
    JSONObject increment()throws JSONException{return new JSONObject().put(".sv",new JSONObject().put("increment",1));}
    void commit(Story inventory,OnlineChange c)throws Exception{commit(inventory,c,"");}
    void commit(Story inventory,OnlineChange c,String operation)throws Exception{
        long rev=revision+1;String uid=f.uid,eventKey=OnlineJournal.eventKey(rev),op=OnlineWorld.randomId(),prefix="head/actors/"+uid+"/",encrypted=encrypt(inventory,rev);
        JSONObject patch=new JSONObject().put(prefix+"mechanic",JSONObject.NULL).put(prefix+"resourceRegion",JSONObject.NULL).put(prefix+"resourceSite",JSONObject.NULL).put(prefix+"batch",JSONObject.NULL).put(prefix+"count",JSONObject.NULL).put(prefix+"base",JSONObject.NULL).put(prefix+"presenceBase",JSONObject.NULL).put(prefix+"growthToken",JSONObject.NULL).put(prefix+"growthDay",JSONObject.NULL).put(prefix+"decayRegion",JSONObject.NULL).put(prefix+"reactionRegion",JSONObject.NULL).put(prefix+"needsSpace",JSONObject.NULL).put("head/g",increment()).put(prefix+"schemaRev",rev).put(prefix+"rev",rev).put(prefix+"seq",sequence+(c==null?0:1)).put(prefix+"inventory",encrypted).put(prefix+"op",op).put(prefix+"eventKey",eventKey);
        if(c!=null){JSONObject last=JournalBatch.last(c);
            patch.put(prefix+"last",last).put(prefix+"tail/e"+(c.sequence%16),c.encode()).put("head/regions/s"+c.region,increment()).put("head/chunks/"+OnlineJournal.chunk(c),increment()).put("chunks/"+OnlineJournal.chunk(c)+"/cells/"+c.key(),JournalBatch.cell(c,null));}
        if(c!=null){
            String saplingPath="growth/saplings/"+c.region+"/"+c.key();
            if(c.nature.startsWith("tree_"))patch.put(saplingPath,new JSONObject().put("stage",0).put("plantedAt",new JSONObject().put(".sv","timestamp")).put("lastGrowthDay",day()).put("token",c.token));
            else patch.put(saplingPath,JSONObject.NULL);
        }
        patch.put("journal/"+uid+"/"+eventKey,new JSONObject().put("rev",rev).put("seq",sequence+(c==null?0:1)).put("inventory",encrypted).put("op",op).put("event",c==null?"":c.encode()));
        if(operation.isEmpty())db.request("PATCH",storage(),patch);else db.request("PATCH",storage(),patch,operation);
    }
    long day(){return Math.floorDiv(System.currentTimeMillis()+db.serverOffset,TreeGrowth.DAY);}
    String designation(int type,int x,int y,int z){if(type!=6)return "user";World w=s.world;boolean single=w!=null&&w.get(x+1,y,z)!=6&&w.get(x-1,y,z)!=6&&w.get(x,y,z+1)!=6&&w.get(x,y,z-1)!=6&&w.get(x,y+1,z)!=6;return single&&(w.get(x,y-1,z)==1||w.get(x,y-1,z)==2)?"tree_0_"+day()+"_"+(System.currentTimeMillis()+db.serverOffset):"decor";}
    @Override void edit(int x,int y,int z,int type){
        if(!activated){super.edit(x,y,z,type);return;}World target=s.world;if(target==null||!target.contains(x,y,z)||y==0||type<0||type>=World.NAMES.length||!s.beginAction())return;int r=OnlineWorld.sectorAt(x,z,s.side),lx=x-r%s.side*World.W,lz=z-r/s.side*World.D,expected=target.get(x,y,z);long started=s.actionStarted;
        s.worker.execute(()->{try{OnlineSession.Region data=s.regions.get(r);if(data==null||!data.generated)return;String key=lx+"_"+y+"_"+lz;if(data.types.containsKey(key)&&data.types.get(key)!=expected||type!=0&&expected!=0){s.activity.game.message("Der Block wurde inzwischen geändert. Bitte erneut versuchen.");return;}
            Story next=OnlineInventory.copy(s.inventory);String problem=type==0?OnlineInventory.harvest(next,expected):OnlineInventory.place(next,type);if(!problem.isEmpty()){s.activity.game.message(problem);return;}
            boolean water=type==0&&expected==10&&(data.renewable.getOrDefault(key,false)||!data.versions.containsKey(key));OnlineChange c=new OnlineChange(sequence+1,r,lx,y,lz,water?10:type,water,OnlineWorld.randomId(),OnlineChange.nonce(data.versions.getOrDefault(key,"")),f.uid,designation(type,x,y,z));
            commit(next,c);if(expected==5&&type==0&&nature!=null)nature.changed(r);data.versions.put(key,c.token);data.types.put(key,c.type);data.renewable.put(key,water);data.own.add(c);data.ownChanges++;metadata(data,lx,y,lz,c.type,c.nature);sequence++;revision++;s.inventory=next;s.inventoryVersion=Long.toString(revision);s.apply(target,r,lx,y,lz,c.type,false);s.applyInventory(next,started);
        }catch(Exception e){s.mutationFailed(e);}finally{s.pending=false;}});
    }
    @Override void progressAction(int recipe){if(!activated){super.progressAction(recipe);return;}if(!s.beginAction())return;long started=s.actionStarted;s.worker.execute(()->{try{Story next=OnlineInventory.copy(s.inventory);String message=recipe<0?(next.claim()?"Werkzeug / Auftrag freigeschaltet":next.goal()):next.craft(recipe);if(!OnlineInventory.same(next,s.inventory)){commit(next,null);revision++;s.inventory=next;s.inventoryVersion=Long.toString(revision);s.applyInventory(next,started);}String text=message;s.activity.runOnUiThread(()->{if(!s.closed){s.activity.toast(text);s.activity.online.refreshWorkbench();}});}catch(Exception e){s.mutationFailed(e);}finally{s.pending=false;}});}
    void pause(){if(db!=null)db.interrupt();if(checkpoints!=null)checkpoints.pause();}
    @Override void deleteWorld(Runnable success,java.util.function.Consumer<String> failure){
        if(!s.owner()||s.closed)return;s.loading=true;s.healthy=false;s.worker.execute(()->{try{
            JSONObject root=f.get(s.base());if(root!=null&&FirebaseRest.integer(root.getJSONObject("fields"),"protocol")==6){
                if(checkpoints!=null)checkpoints.stop();if(db==null)db=new RtdbRest(f);if(base==null)base="worlds/"+s.owner+"/"+s.invitation.id;
                if(s.realtime==null)s.realtime=new OnlineRealtime(s);s.realtime.closeWorld();
                f.commit(new JSONArray().put(f.write(s.base(),FirebaseRest.fields("closed",true),new String[]{"closed"},new JSONObject().put("exists",true),false)));
                db.request("DELETE",storage(),null);db.request("DELETE",base+"/live",null);
                s.deleteCollection(s.base()+"/saved",false);s.deleteCollection(s.base()+"/uploads",false);f.commit(new JSONArray().put(new JSONObject().put("delete",f.documentName(s.base()+"/sync/checkpoint"))));
            }super.deleteWorld(success,failure);
        }catch(Exception e){s.activity.runOnUiThread(()->failure.accept("Löschen unterbrochen. Bitte erneut versuchen."));}});
    }
    @Override public void close(){stopped=true;if(resources!=null)resources.closed=true;if(nature!=null)nature.closed=true;journalConnected=false;if(activated)FirebaseRest.DIAGNOSTICS.journal(false);if(db!=null)db.close();journalReader.shutdownNow();if(checkpoints!=null)checkpoints.close();}
}
