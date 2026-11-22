package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;

/** Bounded, retryable switch transactions. Only swept-space chunks are read, never whole regions. */
final class OnlineMechanics {
    final OnlineV6 v;final OnlineSession s;
    final Map<Integer,World> terrain=new HashMap<>();final Map<String,CachedChunk> cache=new HashMap<>();
    static final class CachedChunk {final long revision;final Map<String,String> cells;CachedChunk(long revision,Map<String,String> cells){this.revision=revision;this.cells=cells;}}
    static final class Conflict extends IOException {private static final long serialVersionUID=1L;Conflict(){super("Die Welt wird gerade verändert. Bitte kurz warten und erneut versuchen.");}}
    interface Completion {void done(Exception error);}
    interface Work {void run()throws Exception;}
    OnlineMechanics(OnlineV6 v){this.v=v;s=v.s;}
    void configure(Mechanics.Link link){configure(link,null);}
    void configure(Mechanics.Link link,Completion done){submit(()->configureNow(link),done);}
    void toggle(Mechanics.Link link){submit(()->toggleNow(link),null);}
    void rename(Mechanics.Link link,Mechanics.Link named,Completion done){submit(()->{
        Snapshot snap=new Snapshot(named,true,false);for(Mechanics.Block b:named.buttons)if(snap.get(b.x,b.y,b.z)!=b.type)throw new IOException("Ein Schalter wurde verändert");if(!snap.tag(link.button).equals(link.encode()))throw new IOException("Schalter inzwischen geändert. Bitte erneut auswählen.");
        try{v.commit(s.inventory,change(link.button,named.encode(),snap,1),"Mechanik umbenennen");}catch(IOException e){Snapshot check=new Snapshot(link,true,false);if(!check.tag(link.button).equals(named.encode()))throw e;}refresh();
    },done);}
    void remove(Mechanics.Link link){remove(link,null);}
    void remove(Mechanics.Link link,Completion done){submit(()->removeNow(link),done);}
    void submit(Work work,Completion done){
        if(!s.beginAction())throw new IllegalArgumentException("Bitte warten, bis die laufende Aktion fertig ist");
        s.worker.execute(()->{Exception error=null;try{work.run();s.activity.game.message("Mechanik gespeichert");}catch(Exception e){error=e;if(e instanceof IOException&&!(e instanceof Conflict)){s.inventoryUncertain=true;s.requestRealtimeSync();}s.activity.game.message(e instanceof FirebaseRest.Failure?"Mechanik konnte nicht gespeichert werden. Bitte Diagnose prüfen.":e.getMessage());}
            finally{s.pending=false;Exception result=error;if(done!=null)s.activity.runOnUiThread(()->{if(!s.closed)done.done(result);});}});
    }
    void refresh()throws Exception{v.accept(v.fetchHead());v.syncVisible(false);s.applyInventory(s.inventory,s.actionStarted);}
    OnlineChange change(Mechanics.Block b,String tag,Snapshot snap,int index){int r=OnlineWorld.sectorAt(b.x,b.z,s.side),x=b.x-r%s.side*World.W,z=b.z-r/s.side*World.D;String cell=snap.cells.get(snap.key(b.x,b.y,b.z));String before=cell==null?"":cell.split(":")[2];return new OnlineChange(v.sequence+index,r,x,b.y,z,b.type,false,OnlineWorld.randomId(),before,v.f.uid,tag);}
    void configureNow(Mechanics.Link link)throws Exception{
        for(int attempt=0;attempt<3;attempt++)try{
            Snapshot snap=new Snapshot(link,false,false);String prior=snap.tag(link.button);
            if(prior.equals(link.encode())){refresh();return;}
            if(prior.startsWith("mech_"))throw new IOException("Dieser Schalter gehört bereits zu einer Mechanik. Bitte zuerst die bestehende Verbindung verwalten.");
            Mechanics.validateConfiguration(link,snap);
            for(Mechanics.Block b:link.blocks)if(!snap.cells.containsKey(snap.key(b.x,b.y,b.z)))throw new IOException("Online bitte selbst gebaute Blöcke auswählen");
            try{v.commit(s.inventory,change(link.button,link.encode(),snap,1),"Mechanik einrichten");}catch(IOException e){Snapshot check=new Snapshot(link,true,false);if(!check.tag(link.button).equals(link.encode()))throw e;}
            refresh();return;
        }catch(Conflict e){if(attempt==2)throw e;FirebaseRest.DIAGNOSTICS.mechanismRetry();}
    }
    void removeNow(Mechanics.Link link)throws Exception{
        for(int attempt=0;attempt<3;attempt++)try{
            Snapshot snap=new Snapshot(link,true,false);String prior=snap.tag(link.button);if(!prior.startsWith("mech_")){refresh();return;}if(!prior.equals(link.encode()))throw new IOException("Schalter inzwischen geändert. Bitte erneut auswählen.");
            try{v.commit(s.inventory,change(link.button,"user",snap,1),"Mechanik entfernen");}catch(IOException e){Snapshot check=new Snapshot(link,true,false);if(check.tag(link.button).startsWith("mech_"))throw e;}
            refresh();return;
        }catch(Conflict e){if(attempt==2)throw e;FirebaseRest.DIAGNOSTICS.mechanismRetry();}
    }
    boolean pressure(Mechanics.Link link,Runnable retry){
        if(s.closed||!s.foreground||!s.healthy||s.loading||s.pending)return false;
        if(!s.beginAction())return false;
        s.worker.execute(()->{try{toggleNow(link,true);}catch(IllegalArgumentException e){/* Wait for a changed world/pose before retrying a blocked path. */}catch(Exception e){retry.run();FirebaseRest.DIAGNOSTICS.failed("Druckplatte · "+e.getClass().getSimpleName());}finally{s.pending=false;}});return true;
    }
    void toggleNow(Mechanics.Link link)throws Exception{toggleNow(link,false);}
    void toggleNow(Mechanics.Link link,boolean automatic)throws Exception{
        for(int attempt=0;attempt<3;attempt++){
            Snapshot snap;try{snap=new Snapshot(link,false,true);}catch(Conflict e){if(attempt==2)throw e;FirebaseRest.DIAGNOSTICS.mechanismRetry();continue;}
            String current=snap.tag(link.button);
            if(automatic){if(!current.equals(link.encode())&&!current.equals(link.toggle().encode()))return;link=Mechanics.decode(current);if(!link.pressure||snap.pressed(link)==link.moved)return;}
            else{if(current.equals(link.toggle().encode())){refresh();return;}if(!current.equals(link.encode()))throw new IOException("Schalter inzwischen geändert. Bitte erneut auswählen.");}
            JSONObject patch=movement(link,snap);String prefix="head/actors/"+v.f.uid+"/",op=patch.getString(prefix+"op");
            try{v.db.request("PATCH",v.storage(),patch,automatic?"Druckplatte":"Mechanik schalten");refresh();return;}catch(IOException e){
                JournalHead now=v.fetchHead();JSONObject actor=now.actors.optJSONObject(v.f.uid);
                // A lost reply must not repeat a committed toggle or debit the inventory again.
                if(actor!=null&&op.equals(actor.optString("op"))){refresh();return;}
                boolean denied=e instanceof FirebaseRest.Failure&&(((FirebaseRest.Failure)e).status==401||((FirebaseRest.Failure)e).status==403);
                if(!denied)throw e;
                JSONObject live=v.remote(v.base+"/live");boolean conflict=now.generation!=snap.head.generation||live.optLong("poseVersion",0)!=snap.presence.optLong("poseVersion",0);
                if(!conflict)throw e;FirebaseRest.DIAGNOSTICS.mechanismConflict();if(attempt==2)throw new Conflict();FirebaseRest.DIAGNOSTICS.mechanismRetry();
            }
        }
    }
    JSONObject movement(Mechanics.Link link,Snapshot snap)throws Exception{
        List<Mechanics.Block> all=new ArrayList<>(Mechanics.plan(link,snap));all.add(link.button);List<OnlineChange> changes=new ArrayList<>();
        for(int i=0;i<all.size();i++){Mechanics.Block b=all.get(i);changes.add(change(b,i==all.size()-1?link.toggle().encode():b.type==0?"":"user",snap,i+1));}
        JSONObject patch=JournalBatch.patch(v,changes);String prefix="head/actors/"+v.f.uid+"/";patch.put(prefix+"mechanic",true).put(prefix+"base",snap.head.generation).put(prefix+"presenceBase",snap.presence.optLong("poseVersion",0));
        int[] d=Mechanics.DIRECTIONS[link.direction];int sign=link.moved?-1:1;Map<String,Integer> slots=new HashMap<>();for(int i=0;i<all.size();i++)slots.put(all.get(i).key(),i);
        JSONObject batch=patch.getJSONObject(prefix+"batch");for(int i=0;i<all.size();i++){Mechanics.Block b=all.get(i);int from=-1;if(i==all.size()-1)from=i;else if(b.type!=0){Integer slot=slots.get((b.x-d[0]*link.distance*sign)+"_"+(b.y-d[1]*link.distance*sign)+"_"+(b.z-d[2]*link.distance*sign));if(slot==null)throw new IOException("Ungültiger Bewegungsplan");from=slot;}batch.getJSONObject("b"+i).getJSONObject("last").put("from",from);}return patch;
    }
    final class Snapshot implements Mechanics.Space {
        final JournalHead head;final JSONObject presence;final Map<String,String> cells=new HashMap<>();final Set<String> chunks=new LinkedHashSet<>();
        Snapshot(Mechanics.Link link,boolean buttonOnly,boolean positions)throws Exception{
            JournalHead before=v.fetchHead();List<Mechanics.Block> required=new ArrayList<>();if(!buttonOnly)required.addAll(link.blocks);required.addAll(link.buttons);int[] d=Mechanics.DIRECTIONS[link.direction];
            for(Mechanics.Block b:required)for(int n=0;n<=(link.buttons.contains(b)?0:link.distance);n++){
                int x=b.x+d[0]*n,y=b.y+d[1]*n,z=b.z+d[2]*n;int region=OnlineWorld.sectorAt(x,z,s.side);
                if(region<0||y<1||y>=World.H||!s.readyRegions.contains(region))throw new IOException("Erst den gesamten Bewegungsbereich laden");
                terrain.computeIfAbsent(region,id->OnlineTerrain.region(s.seed,id,s.side,s.terrainVersion));chunks.add(chunk(x,y,z));
            }
            for(String chunk:chunks){long revision=before.chunk(chunk);CachedChunk saved=cache.get(chunk);if(saved==null||saved.revision!=revision){
                Map<String,String> values=revision==0?Collections.emptyMap():JournalHead.cells(v.remote(v.storage()+"/chunks/"+chunk));saved=new CachedChunk(revision,values);cache.put(chunk,saved);
            }int region=Integer.parseInt(chunk.substring(0,chunk.indexOf('_')));for(Map.Entry<String,String> e:saved.cells.entrySet())cells.put(region+"/"+e.getKey(),e.getValue());}
            JournalHead after=v.fetchHead();for(String chunk:chunks)if(before.chunk(chunk)!=after.chunk(chunk))throw new Conflict();head=after;v.accept(head);
            presence=positions?v.remote(v.base+"/live"):new JSONObject();
        }
        String chunk(int x,int y,int z){int r=OnlineWorld.sectorAt(x,z,s.side);return r+"_"+((x%World.W)/16)+"_"+(y/16)+"_"+((z%World.D)/16);}
        String key(int x,int y,int z){int r=OnlineWorld.sectorAt(x,z,s.side);return r+"/"+(x-r%s.side*World.W)+"_"+y+"_"+(z-r/s.side*World.D);}
        String tag(Mechanics.Block b){String raw=cells.get(key(b.x,b.y,b.z));if(raw==null)return "";String[] values=raw.split(":",4);return values.length>3?values[3]:"";}
        public int get(int x,int y,int z){if(!inside(x,y,z))return 0;String raw=cells.get(key(x,y,z));if(raw!=null)return Integer.parseInt(raw.split(":",2)[0]);int r=OnlineWorld.sectorAt(x,z,s.side);World tile=terrain.get(r);return tile==null?0:tile.get(x%World.W,y,z%World.D);}
        public boolean inside(int x,int y,int z){return y>0&&y<World.H&&x>=0&&z>=0&&x<s.side*World.W&&z<s.side*World.D&&chunks.contains(chunk(x,y,z));}
        boolean pressed(Mechanics.Link link){
            JSONObject players=JournalHead.object(presence,"players");for(String uid:OnlineV5.keys(players)){JSONObject p=players.optJSONObject(uid);if(p==null||p.optLong("t")<System.currentTimeMillis()+v.db.serverOffset-8000)continue;int region=p.optInt("sector",-1);if(region<0||region>=s.side*s.side)continue;
                if(Mechanics.pressed(link,this,(float)p.optDouble("x")+region%s.side*World.W,(float)p.optDouble("y"),(float)p.optDouble("z")+region/s.side*World.D))return true;}
            return false;
        }
        public boolean occupied(int x,int y,int z){if(x+1>s.px-.28f&&x<s.px+.28f&&y+1>s.py-1.6f&&y<s.py+.15f&&z+1>s.pz-.28f&&z<s.pz+.28f)return true;JSONObject players=JournalHead.object(presence,"players");for(String uid:OnlineV5.keys(players)){JSONObject p=players.optJSONObject(uid);if(p==null||p.optLong("t")<System.currentTimeMillis()+v.db.serverOffset-10000)continue;float px=(float)p.optDouble("x")+p.optInt("sector")%s.side*World.W,py=(float)p.optDouble("y"),pz=(float)p.optDouble("z")+p.optInt("sector")/s.side*World.D;if(x+1>px-.28f&&x<px+.28f&&y+1>py-1.6f&&y<py+.15f&&z+1>pz-.28f&&z<pz+.28f)return true;}return false;}
    }
}
