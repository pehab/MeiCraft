package de.meiapps.meicraft;

import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Event-driven nature work on the session's serial worker; no idle timer or extra stream. */
final class OnlineNature {
    final OnlineV6 v;final OnlineSession s;boolean queued,closed;final Set<Integer> decayRegions=new HashSet<>();
    final Map<Integer,World> terrain=new HashMap<>();final Map<Integer,Map<String,String>> cellCache=new HashMap<>();final Map<String,Long> counters=new HashMap<>();
    OnlineNature(OnlineV6 v){this.v=v;s=v.s;}
    void join(){s.worker.execute(()->{try{daily();}catch(Exception e){FirebaseRest.DIAGNOSTICS.failed("Baumprüfung · "+e.getClass().getSimpleName());}for(int id:s.readyRegions)decayRegions.add(id);schedule();});}
    void changed(int region){decayRegions.add(region);schedule();}
    void schedule(){if(!v.services||queued||closed||s.closed||decayRegions.isEmpty())return;queued=true;s.worker.schedule(this::decay,3,TimeUnit.SECONDS);}
    long now(){return System.currentTimeMillis()+v.db.serverOffset;}
    void daily()throws Exception{
        String path=v.storage()+"/growth/run";RtdbRest.Version version=v.db.version(path);JSONObject old=v.object(version.value);long day=v.day();
        if(old.optLong("day",-1)==day&&(old.optBoolean("done")||old.optLong("until")>now()))return;
        String token=OnlineWorld.randomId();JSONObject run=new JSONObject().put("day",day).put("uid",v.f.uid).put("token",token).put("until",now()+15000).put("done",false);
        try{v.db.compare(path,run,version.etag);}catch(FirebaseRest.Failure e){if(e.status==412||e.status==401||e.status==403)return;throw e;}
        repairIndex();JSONObject saplings=v.remote(v.storage()+"/growth/saplings");
        for(String region:OnlineV5.keys(saplings))for(String key:OnlineV5.keys(saplings.getJSONObject(region))){
            if(closed||s.closed)return;
            // Renew only while processing actual trees. A killed client can be replaced on the next join.
            run.put("until",now()+15000);v.db.request("PUT",path,run);
            grow(Integer.parseInt(region),key,day,token);
        }
        run.put("done",true).put("until",0);v.db.request("PUT",path,run);v.readSync();v.syncVisible(false);
    }
    void repairIndex()throws Exception{
        if(Boolean.TRUE.equals(v.db.request("GET",v.storage()+"/growth/indexReady",null)))return;
        JournalHead head=v.fetchHead();JSONObject patch=new JSONObject();
        for(String chunk:OnlineV5.keys(head.chunks)){
            JSONObject cells=JournalHead.object(v.remote(v.storage()+"/chunks/"+chunk),"cells");String region=chunk.substring(0,chunk.indexOf('_'));
            for(String key:OnlineV5.keys(cells)){
                JSONObject cell=cells.getJSONObject(key);String metadata=cell.optString("nature");if(!metadata.startsWith("tree_"))continue;String[] p=metadata.split("_");
                patch.put("saplings/"+region+"/"+key,new JSONObject().put("stage",Integer.parseInt(p[1])).put("lastGrowthDay",Long.parseLong(p[2])).put("plantedAt",Long.parseLong(p[3])).put("token",cell.getString("token")).put("chunk",chunk));
            }
        }
        patch.put("indexReady",true);v.db.request("PATCH",v.storage()+"/growth",patch);
    }
    final class Tile implements TreeGrowth.Space {
        final long presenceVersion;final World world;final Map<String,String> values;final JSONObject players;final int region,side;final OnlineSession s;
        Tile(World w,Map<String,String> values,JSONObject players,int region,OnlineSession s){world=w;this.values=values;this.presenceVersion=players.optLong("poseVersion",0);this.players=JournalHead.object(players,"players");this.region=region;side=s.side;this.s=s;}
        public int get(int x,int y,int z){
            if(x>=0&&x<World.W&&z>=0&&z<World.D)return world.get(x,y,z);
            int gx=region%side*World.W+x,gz=region/side*World.D+z;if(gx<0||gz<0||gx>=side*World.W||gz>=side*World.D)return 0;
            int r=gz/World.D*side+gx/World.W;World neighbor=terrain.get(r);return neighbor==null?5:neighbor.get(gx%World.W,y,gz%World.D);
        }
        public boolean occupied(int x,int y,int z){
            float gx=region%side*World.W+x,gz=region/side*World.D+z;
            if(overlap(gx,y,gz,s.px,s.py,s.pz))return true;
            for(String uid:OnlineV5.keys(players)){JSONObject p=players.optJSONObject(uid);if(p==null||now()-p.optLong("t",0)>8000)continue;int r=p.optInt("sector",-1);if(r<0)continue;
                if(overlap(gx,y,gz,(float)p.optDouble("x")+r%side*World.W,(float)p.optDouble("y"),(float)p.optDouble("z")+r/side*World.D))return true;
            }return false;
        }
        static boolean overlap(float x,float y,float z,float px,float py,float pz){return x+1>px-.4f&&x<px+.4f&&y+1>py-1.7f&&y<py+.3f&&z+1>pz-.4f&&z<pz+.4f;}
        String nature(String key){String value=values.get(key);if(value==null)return "";String[] parts=value.split(":",-1);return parts.length>3?parts[3]:"";}
        String nonce(String key){String value=values.get(key);return value==null?"":value.split(":")[2];}
    }
    void loadTerrain(int region,JournalHead head)throws Exception{
        World world=terrain.computeIfAbsent(region,id->OnlineTerrain.region(s.seed,id,s.side));Map<String,String> values=cellCache.computeIfAbsent(region,id->new HashMap<>());
        for(String chunk:OnlineV5.keys(head.chunks))if(chunk.startsWith(region+"_")&&counters.getOrDefault(chunk,-1L)!=head.chunk(chunk)){
            Map<String,String> changed=JournalHead.cells(v.remote(v.storage()+"/chunks/"+chunk));values.putAll(changed);
            for(String key:changed.keySet()){String[] xyz=key.split("_");world.set(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),Integer.parseInt(changed.get(key).split(":")[0]));}
            counters.put(chunk,head.chunk(chunk));
        }
    }
    Tile load(int region,JournalHead head)throws Exception{
        loadTerrain(region,head);return new Tile(terrain.get(region),cellCache.get(region),v.remote(v.base+"/live"),region,s);
    }
    void grow(int region,String key,long day,String lease)throws Exception{
        for(int retry=0;retry<3;retry++){
            v.readSync();JournalHead h=v.fetchHead();Tile tile=load(region,h);JSONObject record=v.remote(v.storage()+"/growth/saplings/"+region+"/"+key);
            int stage=record.optInt("stage",-1);if(stage<0||stage>=3||record.optLong("lastGrowthDay",day)>=day)return;
            String[] xyz=key.split("_");int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);
            // Old registrations are accepted only while the original planted leaf still exists.
            if(stage>0&&!tile.nature(xyzKey(key,stage)).equals("leaf"))return;
            if(record.has("token")&&!record.getString("token").equals(tile.nonce(key)))return;
            List<TreeGrowth.Block> plan=TreeGrowth.stage(x,y,z,stage,tile);if(plan.isEmpty())return;
            String nature="tree_"+(stage+1)+"_"+day+"_"+record.optLong("plantedAt",now());
            List<OnlineChange> changes=new ArrayList<>();
            // The root also carries durable provenance, backed up with its normal chunk.
            if(stage>0)changes.add(change(region,x,y,z,5,tile,changes.size(),nature));
            for(TreeGrowth.Block b:plan)changes.add(change(region,b.x,b.y,b.z,b.type,tile,changes.size(),b.x==x&&b.y==y&&b.z==z?nature:b.type==6?"leaf":""));
            JSONObject patch=JournalBatch.patch(v,changes);String p="head/actors/"+v.f.uid+"/";
            patch.put(p+"base",h.generation).put(p+"growthToken",lease).put(p+"growthDay",day).put(p+"presenceBase",tile.presenceVersion);
            patch.put("growth/saplings/"+region+"/"+key,new JSONObject().put("stage",stage+1).put("lastGrowthDay",day).put("plantedAt",record.optLong("plantedAt",now())).put("token",changes.get(0).token));
            try{publish(patch,changes);return;}catch(FirebaseRest.Failure e){if(e.status!=401&&e.status!=403)throw e;}
        }
    }
    String xyzKey(String key,int stage){String[] xyz=key.split("_");return xyz[0]+"_"+(Integer.parseInt(xyz[1])+stage)+"_"+xyz[2];}
    OnlineChange change(int region,int x,int y,int z,int type,Tile tile,int offset,String nature){return new OnlineChange(v.sequence+offset+1,region,x,y,z,type,false,OnlineWorld.randomId(),tile.nonce(x+"_"+y+"_"+z),v.f.uid,nature);}
    void publish(JSONObject patch,List<OnlineChange> changes)throws Exception{
        v.db.request("PATCH",v.storage(),patch);v.revision+=changes.size();v.sequence+=changes.size();s.inventoryVersion=Long.toString(v.revision);
        // Do not paint speculative blocks. The ordinary head/tail/region path applies confirmed changes.
        v.accept(v.fetchHead());v.syncVisible(false);
    }
    boolean candidate(int region){
        World w=s.world;if(w==null)return false;int ox=region%s.side*World.W,oz=region/s.side*World.D;
        synchronized(w){TreeGrowth.Space local=new TreeGrowth.Space(){public int get(int x,int y,int z){return w.get(ox+x,y,oz+z);}public boolean occupied(int x,int y,int z){return false;}};
            for(int y=2;y<World.H;y++)for(int z=0;z<World.D;z++)for(int x=0;x<World.W;x++)if(local.get(x,y,z)==6&&local.get(x,y-1,z)!=1&&local.get(x,y-1,z)!=2&&!TreeGrowth.supported(local,x,y,z))return true;
        }return false;
    }
    void decay(){
        queued=false;if(closed||s.closed||!s.foreground)return;Set<Integer> work=new HashSet<>(decayRegions);decayRegions.clear();
        for(int region:work)try{
            if(!candidate(region))continue;
            v.readSync();JournalHead h=v.fetchHead();Tile tile=load(region,h);
            boolean border=false;for(int y=2;y<World.H&&!border;y++)for(int z=0;z<World.D&&!border;z++)for(int x=0;x<World.W;x++)if((x<4||x>=World.W-4||z<4||z>=World.D-4)&&tile.get(x,y,z)==6){border=true;break;}
            if(border)for(int rz=Math.max(0,region/s.side-1);rz<=Math.min(s.side-1,region/s.side+1);rz++)for(int rx=Math.max(0,region%s.side-1);rx<=Math.min(s.side-1,region%s.side+1);rx++)loadTerrain(rz*s.side+rx,h);
            List<OnlineChange> changes=new ArrayList<>();boolean more=false;
            for(int y=2;y<World.H;y++)for(int z=0;z<World.D;z++)for(int x=0;x<World.W;x++){
                if(tile.get(x,y,z)!=6||tile.get(x,y-1,z)==1||tile.get(x,y-1,z)==2)continue;
                String key=x+"_"+y+"_"+z,nature=tile.nature(key);
                // A modified leaf of unknown provenance belongs to the player. Generated leaves and grown crowns may decay.
                if(tile.values.containsKey(key)&&!nature.equals("leaf"))continue;
                if(TreeGrowth.supported(tile,x,y,z))continue;
                if(changes.size()<4)changes.add(change(region,x,y,z,0,tile,changes.size(),""));else more=true;
            }
            if(!changes.isEmpty()){
                JSONObject patch=JournalBatch.patch(v,changes).put("head/actors/"+v.f.uid+"/base",h.generation).put("head/actors/"+v.f.uid+"/presenceBase",tile.presenceVersion);
                patch.put("head/actors/"+v.f.uid+"/decayRegion",region).put("growth/decay/"+region,new JSONObject().put("at",new JSONObject().put(".sv","timestamp")));
                publish(patch,changes);if(more)decayRegions.add(region);
            }
        }catch(Exception e){FirebaseRest.DIAGNOSTICS.failed("Laubprüfung · "+e.getClass().getSimpleName());/* Next join / block change retries; no permanent failure polling. */}
        schedule();
    }
}
