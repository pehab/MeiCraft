package de.meiapps.meicraft;

import org.json.*;
import java.io.*;

/** Freeze old writers before copying. A failed copy can safely repeat from the frozen source. */
final class JournalMigration {
    final OnlineV6 v;final OnlineSession s;final FirebaseRest f;
    JournalMigration(OnlineV6 v){this.v=v;s=v.s;f=v.f;}
    void run()throws Exception{
        try(OnlineRealtime presence=new OnlineRealtime(s)){presence.initialize();if(Boolean.TRUE.equals(presence.db.request("GET",presence.base+"/meta/closed",null))){s.deletionOnly=true;throw new IOException("Diese Welt wurde geschlossen.");}}
        JSONObject root=f.get(s.base());if(FirebaseRest.integer(root.getJSONObject("fields"),"protocol")==5){
            f.commit(new JSONArray().put(f.write(s.base(),FirebaseRest.fields("protocol",6,"migration","rtdb6"),new String[]{"protocol","migration"},new JSONObject().put("updateTime",root.getString("updateTime")),false))
                .put(f.write(v.path(),FirebaseRest.fields("open",false),new String[]{"open"},new JSONObject().put("exists",true),false)));
        }
        FirebaseRest.Snapshot source=f.snapshot(v.path());JSONObject fields=source.document.getJSONObject("fields");v.state=fields;
        try(OnlineRealtime presence=new OnlineRealtime(s)){presence.initialize();}
        JSONObject existing=v.object(v.db.request("GET",v.storage()+"/control",null));
        if(existing.optString("phase").equals("ready")){v.epoch=existing.getString("epoch");finish();return;}
        String stamp=source.document.getString("updateTime"),epoch=existing.optString("epoch",OnlineWorld.randomId());
        if(existing.length()>0&&!existing.optString("source").equals(stamp))throw new IOException("Der eingefrorene Ausgangsstand hat sich geändert. Migration bitte erneut prüfen.");
        JSONObject control=new JSONObject().put("phase","importing").put("epoch",epoch).put("source",stamp);
        if(existing.length()==0)v.db.request("PUT",v.storage(),new JSONObject().put("control",control));
        JSONObject actors=new JSONObject(),regions=new JSONObject(),counters=new JSONObject(),people=OnlineV5.fields(fields,"players");
        for(String uid:OnlineV5.keys(people)){
            JSONObject p=OnlineV5.fields(people,uid),tail=new JSONObject();for(String key:OnlineV5.keys(OnlineV5.fields(p,"log")))tail.put(key,FirebaseRest.string(OnlineV5.fields(p,"log"),key));
            actors.put(uid,new JSONObject().put("rev",OnlineV5.integer(p,"rev")).put("seq",OnlineV5.integer(p,"seq")).put("inventory",FirebaseRest.string(p,"inventory")).put("op",OnlineWorld.randomId()).put("tail",tail));
        }
        for(int region=0;region<s.side*s.side;region++){
            JSONObject sourceRegions=OnlineV5.fields(fields,"regions");regions.put("s"+region,sourceRegions.has("s"+region)?OnlineV5.integer(OnlineV5.fields(sourceRegions,"s"+region),"revision"):0);
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","chunks"))).put("orderBy",new JSONArray().put(OnlineSession.order("__name__"))).put("limit",109);
            JSONArray docs=f.query(s.base()+"/sectors/"+region,query,source.readTime);JSONObject patch=new JSONObject();int count=0;
            for(int i=0;i<docs.length();i++)if(docs.getJSONObject(i).has("document")){
                JSONObject doc=docs.getJSONObject(i).getJSONObject("document"),cells=OnlineV5.fields(doc.getJSONObject("fields"),"cells");String name=doc.getString("name"),id=region+"_"+name.substring(name.lastIndexOf('/')+1);java.util.Map<String,String> values=new java.util.HashMap<>();
                for(String key:OnlineV5.keys(cells)){String value=FirebaseRest.string(cells,key);String[] parts=value.split(":");OnlineJournal.validateCell(key,Integer.parseInt(parts[0]),parts[1].equals("1"),parts[2]);values.put(key,value);}
                patch.put("chunks/"+id,JournalHead.jsonCells(values));counters.put(id,1);if(++count>108)throw new IOException("Ungültige Chunk-Zahl.");
            }
            if(patch.length()>0)v.db.request("PATCH",v.storage(),patch);
        }
        JSONObject head=new JSONObject().put("g",0).put("actors",actors).put("regions",regions).put("chunks",counters);
        v.db.request("PUT",v.storage()+"/head",head);
        // Verify the copied durable cells before allowing a protocol-6 writer.
        for(String id:OnlineV5.keys(counters)){
            JSONObject copy=v.object(v.db.request("GET",v.storage()+"/chunks/"+id,null));java.util.Map<String,String> cells=JournalHead.cells(copy);
            String[] parts=id.split("_",2);JSONObject original=f.get(s.base()+"/sectors/"+parts[0]+"/chunks/"+parts[1]);JSONObject expected=OnlineV5.fields(original.getJSONObject("fields"),"cells");
            if(cells.size()!=expected.length())throw new IOException("Chunk-Prüfung fehlgeschlagen.");for(String key:OnlineV5.keys(expected))if(!FirebaseRest.string(expected,key).equals(cells.get(key)))throw new IOException("Chunk-Prüfung fehlgeschlagen.");
        }
        control.put("phase","ready");v.db.request("PUT",v.storage()+"/control",control);v.epoch=epoch;finish();
    }
    void finish()throws Exception{f.commit(new JSONArray().put(f.write(s.base(),new JSONObject(),new String[]{"migration"},new JSONObject().put("exists",true),false)));}
}
