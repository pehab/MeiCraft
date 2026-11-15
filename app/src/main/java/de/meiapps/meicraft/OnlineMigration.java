package de.meiapps.meicraft;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Owner-only, resumable copy. Legacy documents remain a read-only backup after verification. */
final class OnlineMigration {
    static final String ZERO="0000000000000000000000000000000000000000000000000000000000000000";
    final OnlineV5 v;final OnlineSession s;final FirebaseRest f;
    OnlineMigration(OnlineV5 v){this.v=v;s=v.s;f=v.f;}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)out.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return out.toString();}
    static byte[] hash(String data)throws Exception{return MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));}
    static String mix(String accumulator,String value)throws Exception{byte[] result=new byte[32],digest=hash(value);for(int i=0;i<32;i++)result[i]=(byte)(Integer.parseInt(accumulator.substring(i*2,i*2+2),16)^digest[i]);return hex(result);}
    void notice(String text){s.activity.runOnUiThread(()->{if(!s.closed&&s.activity.online.progress!=null)s.activity.online.progress.setMessage(text);});}
    void run(JSONObject rootDocument)throws Exception{
        if(!s.owner())throw new IOException("Nur der Weltbesitzer kann migrieren.");
        notice("Bestehende Welt wird einmalig optimiert. Bauten und Vorräte bleiben erhalten. Bei Unterbrechung erneut als Besitzer öffnen.");
        JSONObject root=rootDocument.getJSONObject("fields");
        if(!root.getJSONObject("closed").getBoolean("booleanValue")){
            f.commit(new JSONArray().put(f.write(s.base(),FirebaseRest.fields("closed",true,"migration","v5"),new String[]{"closed","migration"},new JSONObject().put("updateTime",rootDocument.getString("updateTime")),false)));
        }
        JSONObject saved=f.get(v.path());
        if(saved==null){
            JSONObject access=new JSONObject(),players=new JSONObject();String token="";
            do{
                JSONObject page=f.list(s.base()+"/members",token);JSONArray members=page.optJSONArray("documents");
                if(members!=null)for(int i=0;i<members.length();i++){
                    JSONObject doc=members.getJSONObject(i),data=doc.getJSONObject("fields");String uid=doc.getString("name").substring(doc.getString("name").lastIndexOf('/')+1);
                    access.put(uid,FirebaseRest.value(!data.getJSONObject("banned").getBoolean("booleanValue")));
                    // Inventories stay in their original private documents until their own device adopts them.
                    players.put(uid,OnlineV5.map(v.player(new Story())));
                }
                token=page.optString("nextPageToken","");
            }while(!token.isEmpty()&&!s.closed);
            if(s.closed)return;if(players.length()>256)throw new IOException("Zu viele gespeicherte Mitglieder für diese Migration.");
            if(!players.has(f.uid))throw new IOException("Besitzer-Mitgliedschaft fehlt; Migration angehalten.");
            JSONObject state=v.initial(access,players,false),legacy=f.get(s.syncPath());if(legacy==null)throw new IOException("Bisherige Änderungsübersicht fehlt.");state.put("regions",legacy.getJSONObject("fields").getJSONObject("regions"));
            f.commit(new JSONArray().put(f.write(v.path(),state,null,new JSONObject().put("exists",false),false)).put(f.write(s.base(),FirebaseRest.fields("protocol",5),new String[]{"protocol"},new JSONObject().put("exists",true),false)));saved=f.get(v.path());
        }
        JSONObject state=saved.getJSONObject("fields");if(FirebaseRest.string(state,"phase").equals("ready")){
            // Recovery after activation response loss: the atomic activation has already completed.
            return;
        }
        JSONObject checkpoint=OnlineV5.fields(state,"migration");int sector=FirebaseRest.integer(checkpoint,"sector");String cursor=FirebaseRest.string(checkpoint,"cursor"),digest=FirebaseRest.string(checkpoint,"hash");long count=OnlineV5.integer(checkpoint,"count");
        if(sector<0||sector>s.side*s.side||count<0||!digest.matches("[a-f0-9]{64}"))throw new IOException("Ungültiger Migrationsstand. Alte Daten bleiben erhalten.");
        while(sector<s.side*s.side&&!s.closed){
            notice("Welt wird optimiert · Bereich "+(sector+1)+" / "+(s.side*s.side)+" · "+count+" Blockänderungen gesichert");
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","blocks"))).put("orderBy",new JSONArray().put(OnlineSession.order("__name__"))).put("limit",200);
            if(!cursor.isEmpty())query.put("startAt",new JSONObject().put("values",new JSONArray().put(new JSONObject().put("referenceValue",cursor))).put("before",false));
            JSONArray page=f.query(s.base()+"/sectors/"+sector,query);Map<String,JSONObject> groups=new LinkedHashMap<>();int received=0;
            for(int i=0;i<page.length();i++)if(page.getJSONObject(i).has("document")){
                JSONObject doc=page.getJSONObject(i).getJSONObject("document"),data=doc.getJSONObject("fields");int x=FirebaseRest.integer(data,"x"),y=FirebaseRest.integer(data,"y"),z=FirebaseRest.integer(data,"z"),type=FirebaseRest.integer(data,"type");
                if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Ungültiger alter Block; Migration angehalten.");
                String nonce=hex(hash(doc.getString("name")+"|"+doc.getString("updateTime"))).substring(0,32);boolean water=data.optJSONObject("renewable")!=null&&data.getJSONObject("renewable").optBoolean("booleanValue",false);
                OnlineChange change=new OnlineChange(1,sector,x,y,z,type,water,nonce,"",f.uid);JSONObject cells=groups.computeIfAbsent(change.chunk(),key->new JSONObject());cells.put(change.key(),FirebaseRest.value(change.value()));
                digest=mix(digest,sector+"/"+change.key()+"/"+change.value());count++;received++;cursor=doc.getString("name");
            }
            JSONArray writes=new JSONArray();for(Map.Entry<String,JSONObject> group:groups.entrySet()){
                List<String> mask=new ArrayList<>();for(String key:OnlineV5.keys(group.getValue()))mask.add("cells."+OnlineSession.quoted(key));
                writes.put(f.write(v.chunks(sector)+"/"+group.getKey(),new JSONObject().put("cells",OnlineV5.map(group.getValue())),mask.toArray(new String[0]),null,true));
            }
            if(received<200){sector++;cursor="";}
            JSONObject next=FirebaseRest.fields("sector",sector,"cursor",cursor,"count",count,"hash",digest);writes.put(f.write(v.path(),new JSONObject().put("migration",OnlineV5.map(next)),new String[]{"migration"},new JSONObject().put("exists",true),false));
            // Chunk patches and the cursor advance are one atomic commit. A lost reply is safely resumed.
            f.commit(writes);
        }
        if(s.closed)return;notice("Übertragene Bauten werden vollständig geprüft …");long actualCount=0;String actualDigest=ZERO;
        for(int region=0;region<s.side*s.side;region++){
            String after="";int received;
            do{
                JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","chunks"))).put("orderBy",new JSONArray().put(OnlineSession.order("__name__"))).put("limit",4);
                if(!after.isEmpty())query.put("startAt",new JSONObject().put("values",new JSONArray().put(new JSONObject().put("referenceValue",after))).put("before",false));
                JSONArray page=f.query(s.base()+"/sectors/"+region,query);received=0;
                for(int i=0;i<page.length();i++)if(page.getJSONObject(i).has("document")){
                    JSONObject doc=page.getJSONObject(i).getJSONObject("document"),cells=OnlineV5.fields(doc.getJSONObject("fields"),"cells");for(String key:OnlineV5.keys(cells)){
                        String[] xyz=key.split("_"),value=FirebaseRest.string(cells,key).split(":",-1);String name=doc.getString("name");
                        if(xyz.length!=3||value.length!=3)throw new IOException("Ungültige migrierte Blockdaten.");
                        int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]),type=Integer.parseInt(value[0]);
                        if(!OnlineWorld.validBlock(x,y,z,type)||!value[1].matches("[01]")||!value[2].matches("[a-f0-9]{32}")||!name.substring(name.lastIndexOf('/')+1).equals(x/16+"_"+y/16+"_"+z/16))throw new IOException("Ungültige migrierte Blockdaten.");
                        actualDigest=mix(actualDigest,region+"/"+key+"/"+FirebaseRest.string(cells,key));actualCount++;}after=doc.getString("name");received++;
                }
            }while(received==4&&!s.closed);
        }
        if(s.closed)return;if(count!=actualCount||!digest.equals(actualDigest))throw new IOException("Prüfung der Migration fehlgeschlagen. Alte Daten bleiben erhalten; bitte Diagnose senden.");
        JSONObject verified=f.get(v.path()).getJSONObject("fields"),people=OnlineV5.fields(verified,"players"),access=OnlineV5.fields(verified,"access");String pageToken="";
        do{
            JSONObject page=f.list(s.base()+"/members",pageToken);JSONArray members=page.optJSONArray("documents");
            if(members!=null)for(int i=0;i<members.length();i++){
                JSONObject member=members.getJSONObject(i);String name=member.getString("name"),uid=name.substring(name.lastIndexOf('/')+1);
                if(!people.has(uid)||!access.has(uid)||access.getJSONObject(uid).getBoolean("booleanValue")==member.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue"))throw new IOException("Mitgliedschaftsprüfung fehlgeschlagen. Alte Daten bleiben erhalten.");

            }
            pageToken=page.optString("nextPageToken","");
        }while(!pageToken.isEmpty()&&!s.closed);
        if(s.closed)return;
        f.commit(new JSONArray().put(f.write(v.path(),FirebaseRest.fields("phase","ready","open",true),new String[]{"phase","open"},new JSONObject().put("exists",true),false)).put(f.write(s.base(),FirebaseRest.fields("closed",false),new String[]{"closed","migration"},new JSONObject().put("exists",true),false)));
    }
}
