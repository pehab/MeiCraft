package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;

/** One bounded journal transaction per switch. Definitions travel with the switch's checkpointed cell. */
final class OnlineMechanics {
    final OnlineV6 v;final OnlineSession s;
    OnlineMechanics(OnlineV6 v){this.v=v;s=v.s;}
    void configure(Mechanics.Link link){submit(()->configureNow(link));}
    void toggle(Mechanics.Link link){submit(()->toggleNow(link));}
    void remove(Mechanics.Link link){submit(()->{Snapshot snap=new Snapshot(link);String tag=snap.tag(link.button);if(!tag.equals(link.encode()))throw new IOException("Schalter inzwischen geändert. Erneut anvisieren.");v.commit(s.inventory,change(link.button,"user",snap,1));refresh();});}
    interface Work {void run()throws Exception;}
    void submit(Work work){if(!s.beginAction())throw new IllegalArgumentException("Bitte warten, bis die laufende Aktion fertig ist");s.worker.execute(()->{try{work.run();s.activity.game.message("Mechanik gespeichert");}catch(Exception e){if(e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==401)s.activity.game.message("Mechanik abgelehnt: aktuelle RTDB-Regeln installieren oder erneut versuchen");else s.activity.game.message(e.getMessage()==null?"Mechanik konnte nicht gespeichert werden":e.getMessage());try{v.readSync();v.syncVisible(false);}catch(Exception ignored){s.mutationFailed(e);}}finally{s.pending=false;}});}
    void refresh()throws Exception{v.accept(v.fetchHead());v.syncVisible(false);s.applyInventory(s.inventory,s.actionStarted);}
    OnlineChange change(Mechanics.Block b,String tag,Snapshot snap,int index){int r=OnlineWorld.sectorAt(b.x,b.z,s.side),x=b.x-r%s.side*World.W,z=b.z-r/s.side*World.D;String cell=snap.cells.get(r+"/"+x+"_"+b.y+"_"+z);String before=cell==null?"":cell.split(":")[2];return new OnlineChange(v.sequence+index,r,x,b.y,z,b.type,false,OnlineWorld.randomId(),before,v.f.uid,tag);}
    void configureNow(Mechanics.Link link)throws Exception{
        Snapshot snap=new Snapshot(link);Mechanics.plan(link,snap);
        for(Mechanics.Block b:link.blocks)if(!snap.cells.containsKey(snap.key(b.x,b.y,b.z)))throw new IOException("Online bitte selbst gebaute Blöcke auswählen");
        v.commit(s.inventory,change(link.button,link.encode(),snap,1));refresh();
    }
    void toggleNow(Mechanics.Link link)throws Exception{
        Snapshot snap=new Snapshot(link);if(!snap.tag(link.button).equals(link.encode()))throw new IOException("Schalter inzwischen geändert. Erneut anvisieren.");
        List<Mechanics.Block> plan=Mechanics.plan(link,snap);List<Mechanics.Block> all=new ArrayList<>(plan);all.add(link.button);List<OnlineChange> changes=new ArrayList<>();
        for(int i=0;i<all.size();i++){Mechanics.Block b=all.get(i);changes.add(change(b,i==all.size()-1?link.toggle().encode():b.type==0?"":"user",snap,i+1));}
        JSONObject patch=JournalBatch.patch(v,changes);String prefix="head/actors/"+v.f.uid+"/";patch.put(prefix+"mechanic",true).put(prefix+"base",snap.head.generation).put(prefix+"presenceBase",snap.presence.optLong("poseVersion",0));
        int[] d=Mechanics.DIRECTIONS[link.direction];int sign=link.moved?-1:1;
        Map<String,Integer> slots=new HashMap<>();for(int i=0;i<all.size();i++)slots.put(all.get(i).key(),i);
        JSONObject batch=patch.getJSONObject(prefix+"batch");for(int i=0;i<all.size();i++){Mechanics.Block b=all.get(i);int from=-1;if(i==all.size()-1)from=i;else if(b.type!=0){Integer slot=slots.get((b.x-d[0]*link.distance*sign)+"_"+(b.y-d[1]*link.distance*sign)+"_"+(b.z-d[2]*link.distance*sign));if(slot==null)throw new IOException("Ungültiger Bewegungsplan");from=slot;}batch.getJSONObject("b"+i).getJSONObject("last").put("from",from);}
        v.db.request("PATCH",v.storage(),patch);refresh();
    }
    final class Snapshot implements Mechanics.Space {
        final JournalHead head;final World w;final JSONObject presence;final Map<String,String> cells=new HashMap<>();
        Snapshot(Mechanics.Link link)throws Exception{
            v.accept(v.fetchHead());head=v.head;w=World.onlineSpace(s.seed,s.side,s.terrainVersion);w.x=s.px;w.y=s.py;w.z=s.pz;Set<Integer> regions=new HashSet<>();
            List<Mechanics.Block> required=new ArrayList<>(link.blocks);required.add(link.button);int[] d=Mechanics.DIRECTIONS[link.direction];for(Mechanics.Block b:required)for(int n=0;n<=link.distance;n++){int x=b.x+(b==link.button?0:d[0]*n),y=b.y+(b==link.button?0:d[1]*n),z=b.z+(b==link.button?0:d[2]*n);int region=OnlineWorld.sectorAt(x,z,s.side);if(region<0||y<1||y>=World.H||!s.readyRegions.contains(region))throw new IOException("Erst den gesamten Bewegungsbereich laden");regions.add(region);}
            for(int region:regions){World tile=OnlineTerrain.region(s.seed,region,s.side,s.terrainVersion);for(String chunk:OnlineV5.keys(head.chunks))if(chunk.startsWith(region+"_")){Map<String,String> overrides=JournalHead.cells(v.remote(v.storage()+"/chunks/"+chunk));for(String key:overrides.keySet()){String[] xyz=key.split("_");String value=overrides.get(key);tile.set(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),Integer.parseInt(value.split(":")[0]));cells.put(region+"/"+key,value);}}OnlineInventory.install(w,tile,region,s.side);}
            presence=v.remote(v.base+"/live");if(v.fetchHead().generation!=head.generation)throw new IOException("Die Welt hat sich geändert. Bitte erneut schalten.");
        }
        String key(int x,int y,int z){int r=OnlineWorld.sectorAt(x,z,s.side);return r+"/"+(x-r%s.side*World.W)+"_"+y+"_"+(z-r/s.side*World.D);}
        String tag(Mechanics.Block b){String raw=cells.get(key(b.x,b.y,b.z));if(raw==null)return "";String[] values=raw.split(":",4);return values.length>3?values[3]:"";}
        public int get(int x,int y,int z){return w.get(x,y,z);}public boolean inside(int x,int y,int z){return y>0&&w.contains(x,y,z);}
        public boolean occupied(int x,int y,int z){if(x+1>s.px-.28f&&x<s.px+.28f&&y+1>s.py-1.6f&&y<s.py+.15f&&z+1>s.pz-.28f&&z<s.pz+.28f)return true;JSONObject players=JournalHead.object(presence,"players");for(String uid:OnlineV5.keys(players)){JSONObject p=players.optJSONObject(uid);if(p==null||p.optLong("t")<System.currentTimeMillis()+v.db.serverOffset-10000)continue;float px=(float)p.optDouble("x")+p.optInt("sector")%s.side*World.W,py=(float)p.optDouble("y"),pz=(float)p.optDouble("z")+p.optInt("sector")/s.side*World.D;if(x+1>px-.28f&&x<px+.28f&&y+1>py-1.6f&&y<py+.15f&&z+1>pz-.28f&&z<pz+.28f)return true;}return false;}
    }
}
