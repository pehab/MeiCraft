package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;

/** Selective coherent snapshots despite ongoing builds. No global world download or gameplay lock. */
final class JournalSnapshot {
    final JournalHead head;final Map<String,Map<String,String>> chunks;
    JournalSnapshot(JournalHead head,Map<String,Map<String,String>> chunks){this.head=head;this.chunks=chunks;}
    static boolean sameCounters(JSONObject a,JSONObject b)throws JSONException{if(a.length()!=b.length())return false;for(String key:OnlineV5.keys(a))if(!b.has(key)||a.getLong(key)!=b.getLong(key))return false;return true;}
    static JournalHead head(RtdbRest db,String base)throws Exception{return new JournalHead((JSONObject)db.request("GET",base+"/head",null));}
    static List<OnlineChange> history(RtdbRest db,String base,JournalHead before,JournalHead after)throws Exception{
        List<OnlineChange> events=new ArrayList<>();
        for(String uid:OnlineV5.keys(after.actors)){
            JSONObject a=after.actors.getJSONObject(uid),b=before.actors.optJSONObject(uid);long from=b==null?0:b.getLong("rev"),to=a.getLong("rev");if(to==from||a.getLong("seq")==before.heads.getOrDefault(uid,0L))continue;
            String query="?orderBy=%22rev%22&startAt="+(from+1)+"&endAt="+to+"&limitToFirst=1000";Object raw=db.request("GET",base+"/journal/"+uid+query,null);if(raw==JSONObject.NULL)throw new IOException("Journal wurde inzwischen kompaktiert. Snapshot wird erneut versucht.");JSONObject data=(JSONObject)raw;
            for(String key:OnlineV5.keys(data)){String event=data.getJSONObject(key).getString("event");if(!event.isEmpty())events.add(OnlineChange.decode(event,uid));}
        }return events;
    }
    static JournalSnapshot collect(RtdbRest db,String base,JSONObject savedCounters)throws Exception{
        JournalHead before=head(db,base);Map<String,Map<String,String>> loaded=new HashMap<>();
        for(int attempt=0;attempt<8;attempt++){
            for(String id:OnlineV5.keys(before.chunks))if(before.chunk(id)!=savedCounters.optLong(id,0)&&!loaded.containsKey(id)){
                Object raw=db.request("GET",base+"/chunks/"+id,null);if(!(raw instanceof JSONObject))throw new IOException("Journal-Chunk fehlt.");Map<String,String> cells=JournalHead.cells((JSONObject)raw);if(cells.isEmpty())throw new IOException("Journal-Chunk ist leer.");loaded.put(id,cells);
            }
            JournalHead after=head(db,base);List<OnlineChange> events=after.tail;Map<String,List<OnlineChange>> deltas=proof(loaded,before,after,events);
            if(deltas==null){events=history(db,base,before,after);deltas=proof(loaded,before,after,events);}
            if(deltas==null){loaded.clear();before=after;continue;}
            for(String id:deltas.keySet())for(OnlineChange c:deltas.get(id))loaded.get(id).put(c.key(),c.value());
            boolean complete=true;for(String id:OnlineV5.keys(after.chunks))if(after.chunk(id)!=savedCounters.optLong(id,0)&&!loaded.containsKey(id)){complete=false;break;}
            if(complete)return new JournalSnapshot(after,loaded);before=after;
        }throw new IOException("Checkpoint wird nach vielen gleichzeitigen Änderungen erneut versucht.");
    }
    static Map<String,List<OnlineChange>> proof(Map<String,Map<String,String>> loaded,JournalHead before,JournalHead after,List<OnlineChange> events){
        Map<String,List<OnlineChange>> result=new HashMap<>();for(String id:loaded.keySet()){List<OnlineChange> delta=OnlineJournal.advance(id,before.chunk(id),after.chunk(id),before.heads,JournalHead.versions(loaded.get(id)),events);if(delta==null)return null;result.put(id,delta);}return result;
    }
}
