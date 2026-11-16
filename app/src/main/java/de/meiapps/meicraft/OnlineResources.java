package de.meiapps.meicraft;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Owner-only, resumable finite enrichment; all blocks use the normal atomic journal. */
final class OnlineResources {
    final OnlineV6 v;final OnlineSession s;final OnlineNature nature;final Map<Integer,World> baselines=new HashMap<>();JSONObject cursors;volatile boolean done,closed,queued;int failures,nextRegion;
    OnlineResources(OnlineV6 v){this.v=v;s=v.s;nature=v.nature;}
    void start(){if(s.owner()&&!s.closed&&!done&&!queued&&!closed){queued=true;s.worker.execute(()->{queued=false;tick();});}}
    void schedule(long delay){if(queued||closed||s.closed||!v.services)return;queued=true;s.worker.schedule(()->{queued=false;tick();},delay,TimeUnit.MILLISECONDS);}
    void tick(){if(closed||s.closed||!s.foreground||done)return;try{nature.yieldToPlayer();boolean more=wave(true);failures=0;if(more)schedule(3000);}catch(OnlineNature.Deferred e){cursors=null;schedule(250);}catch(Exception e){cursors=null;if(++failures<5&&v.services){long delay=OnlineSyncPolicy.retryDelay(failures,e instanceof FirebaseRest.Failure&&((FirebaseRest.Failure)e).status==429);schedule(Math.max(3000,delay));return;}FirebaseRest.DIAGNOSTICS.failed("Rohstoff-Ergänzung · "+e.getClass().getSimpleName());s.activity.runOnUiThread(()->{if(!s.closed)s.activity.toast("Rohstoffe ergänzen unterbrochen. RTDB-Regeln prüfen und als Besitzer erneut beitreten.");});}}
    ResourceRetrofit.Space space(OnlineNature.Tile tile){return new ResourceRetrofit.Space(){public int get(int x,int y,int z){return tile.get(x,y,z);}public boolean occupied(int x,int y,int z){return tile.occupied(x,y,z);}public boolean touched(int x,int y,int z){return tile.values.containsKey(x+"_"+y+"_"+z);}};}
    boolean wave()throws Exception{return wave(false);}
    boolean wave(boolean incremental)throws Exception{
        if(!s.owner()||done)return false;if(cursors==null)cursors=v.remote(v.storage()+"/resources/v1");
        if(s.terrainVersion>=2){JSONObject nativePatch=new JSONObject();JournalHead head=null;for(int region=0;region<s.side*s.side;region++)if(!cursors.has("s"+region)){if(head==null)head=v.fetchHead();nativePatch.put("resources/v1/s"+region,new JSONObject().put("region",region).put("next",8).put("g",head.generation).put("result","native"));}if(nativePatch.length()>0){v.db.request("PATCH",v.storage(),nativePatch);for(String key:OnlineV5.keys(nativePatch))cursors.put(key.substring(key.lastIndexOf('/')+1),nativePatch.getJSONObject(key));}}
        boolean pending=false;
        for(int index=0;index<s.side*s.side;index++){
            int region=incremental?(nextRegion+index)%(s.side*s.side):index;
            JSONObject cursor=cursors.optJSONObject("s"+region);int site=cursor==null?0:cursor.optInt("next",0);if(site>=ResourceRetrofit.SITES)continue;pending=true;if(closed||s.closed||!s.foreground)return false;
            nature.yieldToPlayer();JournalHead head=v.fetchHead();v.accept(head);OnlineNature.Tile tile=nature.load(region,head,false);World baseline=baselines.computeIfAbsent(region,id->OnlineTerrain.region(s.seed,id,s.side,s.terrainVersion));
            ResourceRetrofit.Space space=space(tile);
            List<ResourceRetrofit.Cell> plan=ResourceRetrofit.plan(baseline,space,region,s.side,site);if(!plan.isEmpty()){tile=nature.load(region,head,true);plan=ResourceRetrofit.plan(baseline,space(tile),region,s.side,site);}
            JSONObject next=new JSONObject().put("region",region).put("next",site+1).put("g",head.generation+(plan.isEmpty()?0:1)).put("result",plan.isEmpty()?"skipped":"added");String path="resources/v1/s"+region;
            if(plan.isEmpty())v.db.request("PATCH",v.storage(),new JSONObject().put(path,next));else{
                List<OnlineChange> changes=new ArrayList<>();for(ResourceRetrofit.Cell c:plan)changes.add(new OnlineChange(v.sequence+changes.size()+1,region,c.x,c.y,c.z,c.type,false,OnlineWorld.randomId(),"",v.f.uid,""));
                String p="head/actors/"+v.f.uid+"/";JSONObject patch=JournalBatch.patch(v,changes).put(p+"base",head.generation).put(p+"presenceBase",tile.presenceVersion).put(p+"resourceRegion",region).put(p+"resourceSite",site);next.put("op",patch.getString(p+"op"));patch.put(path,next);nature.publish(patch,changes);
            }cursors.put("s"+region,next);if(incremental){nextRegion=(region+1)%(s.side*s.side);break;}
        }
        done=true;for(int region=0;region<s.side*s.side;region++)if(cursors.optJSONObject("s"+region)==null||cursors.getJSONObject("s"+region).optInt("next")<ResourceRetrofit.SITES)done=false;
        if(done){baselines.clear();if(pending)s.activity.runOnUiThread(()->{if(!s.closed)s.activity.toast("Rohstoffprüfung abgeschlossen · Bauten bleiben erhalten");});}return pending&&!done;
    }
}
