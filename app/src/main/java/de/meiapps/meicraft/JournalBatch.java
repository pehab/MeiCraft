package de.meiapps.meicraft;

import org.json.*;
import java.util.*;

/** Up to 16 normal journal revisions committed in one conflict-checked RTDB update. */
final class JournalBatch {
    static JSONObject last(OnlineChange c)throws Exception{return new JSONObject().put("region",c.region).put("chunk",OnlineJournal.chunk(c)).put("key",c.key()).put("x",c.x).put("y",c.y).put("z",c.z).put("type",c.type).put("water",c.renewable).put("token",c.token).put("before",c.before).put("nature",c.nature);}
    static JSONObject cell(OnlineChange c,String slot)throws Exception{JSONObject value=new JSONObject().put("type",c.type).put("water",c.renewable).put("token",c.token).put("nature",c.nature);if(slot!=null)value.put("slot",slot);return value;}
    static List<OnlineChange> events(JSONObject entry,String uid)throws Exception{
        List<OnlineChange> result=new ArrayList<>();String event=entry.optString("event");if(!event.isEmpty())result.add(OnlineChange.decode(event,uid));return result;
    }
    static JSONObject patch(OnlineV6 v,List<OnlineChange> changes)throws Exception{
        if(changes.isEmpty()||changes.size()>16)throw new IllegalArgumentException("batch size");
        String uid=v.f.uid,p="head/actors/"+uid+"/",op=OnlineWorld.randomId();JSONObject patch=new JSONObject().put(p+"growthToken",JSONObject.NULL).put(p+"growthDay",JSONObject.NULL).put(p+"decayRegion",JSONObject.NULL),batch=new JSONObject();Set<String> cells=new HashSet<>();
        long rev=v.revision;for(int i=0;i<changes.size();i++){
            OnlineChange c=changes.get(i);if(c.sequence!=v.sequence+i+1||!cells.add(c.region+"/"+c.key()))throw new IllegalArgumentException("batch order");
            String slot="b"+i,encrypted=v.encrypt(v.s.inventory,++rev),event=c.encode();JSONObject entry=new JSONObject().put("rev",rev).put("seq",c.sequence).put("inventory",encrypted).put("op",op).put("event",event).put("slot",slot);
            batch.put(slot,new JSONObject().put("last",last(c)).put("rev",rev).put("seq",c.sequence).put("inventory",encrypted));
            patch.put("journal/"+uid+"/"+rev,entry).put(p+"tail/e"+(c.sequence%16),event).put("chunks/"+OnlineJournal.chunk(c)+"/cells/"+c.key(),cell(c,slot));
            if(i==changes.size()-1)patch.put(p+"rev",rev).put(p+"seq",c.sequence).put(p+"inventory",encrypted).put(p+"last",last(c)).put(p+"eventKey",Long.toString(rev));
        }
        Map<String,Integer> counters=new HashMap<>();for(OnlineChange c:changes){counters.merge("head/regions/s"+c.region,1,Integer::sum);counters.merge("head/chunks/"+OnlineJournal.chunk(c),1,Integer::sum);}
        for(String key:counters.keySet())patch.put(key,new JSONObject().put(".sv",new JSONObject().put("increment",counters.get(key))));
        return patch.put("head/g",v.increment()).put(p+"op",op).put(p+"batch",batch).put(p+"count",changes.size());
    }
}
