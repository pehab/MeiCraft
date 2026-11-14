package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** Protocol 5: one authorized overview, bounded causal tails, two-document cell commits. */
final class OnlineV5 {
    final OnlineSession s;final FirebaseRest f;
    JSONObject state=new JSONObject();long revision,sequence;OnlineInventoryCipher inventoryCipher;
    final Map<String,Long> heads=new HashMap<>();final List<OnlineChange> tail=new ArrayList<>();
    OnlineV5(OnlineSession session){s=session;f=s.firebase;}
    String path(){return s.base()+"/sync/v5";}
    String chunks(int region){return s.base()+"/sectors/"+region+"/chunks";}
    static JSONObject map(JSONObject fields)throws JSONException{return new JSONObject().put("mapValue",new JSONObject().put("fields",fields));}
    static JSONObject fields(JSONObject parent,String key)throws JSONException{JSONObject value=parent.getJSONObject(key).getJSONObject("mapValue");JSONObject result=value.optJSONObject("fields");return result==null?new JSONObject():result;}
    static long integer(JSONObject fields,String key)throws JSONException{return fields.getJSONObject(key).getLong("integerValue");}
    static List<String> keys(JSONObject data){List<String> keys=new ArrayList<>();data.keys().forEachRemaining(keys::add);return keys;}
    OnlineInventoryCipher cipher(boolean create)throws IOException{
        if(inventoryCipher==null){synchronized(f.credentials){
            String saved=f.credentials.getString("inventoryKey","");
            if(saved.isEmpty()&&!create)throw new IOException("Inventarschlüssel fehlt. Geräteanmeldung und App-Daten bitte erhalten.");
            if(saved.isEmpty()){byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);saved=Base64.getEncoder().encodeToString(key);if(!f.credentials.edit().putString("inventoryKey",saved).commit())throw new IOException("Inventarschlüssel konnte nicht gesichert werden.");}
            try{inventoryCipher=new OnlineInventoryCipher(Base64.getDecoder().decode(saved));}catch(IllegalArgumentException e){throw new IOException("Inventarschlüssel ist beschädigt. App-Daten bitte erhalten.",e);}
        }}return inventoryCipher;
    }
    String encrypt(Story value,long rev)throws IOException{return cipher(true).encrypt(value,s.invitation.id,f.uid,rev);}
    JSONObject player(Story ignored)throws JSONException{return FirebaseRest.fields("rev",0,"seq",0,"inventory","").put("log",map(new JSONObject())).put("last",map(new JSONObject())).put("position",map(new JSONObject()));}
    JSONObject initial(JSONObject access,JSONObject players,boolean ready)throws JSONException{
        return FirebaseRest.fields("schema",5,"owner",s.owner,"seed",s.seed,"side",s.side,"name",s.name,"invite",s.invitation.invitation,"open",ready,"phase",ready?"ready":"importing")
            .put("access",map(access)).put("players",map(players)).put("regions",map(new JSONObject()))
            .put("migration",map(FirebaseRest.fields("sector",0,"cursor","","count",0,"hash",OnlineMigration.ZERO)));
    }
    void start(boolean create,String name,int side)throws Exception{
        if(create){
            if(side!=2&&side!=3)throw new IOException("Ungültige Weltgröße.");if(name.trim().isEmpty()||name.trim().length()>48)throw new IOException("Weltname: 1–48 Zeichen.");
            s.owner=f.uid;s.seed=new java.security.SecureRandom().nextInt(1000000000);s.side=side;s.name=name.trim();
            JSONObject root=FirebaseRest.fields("owner",s.owner,"name",s.name,"seed",s.seed,"side",side,"protocol",5,"invite",s.invitation.invitation,"closed",false);
            state=initial(new JSONObject().put(f.uid,FirebaseRest.value(true)),new JSONObject().put(f.uid,map(player(new Story()))),true);
            f.commit(new JSONArray().put(f.write(s.base(),root,null,new JSONObject().put("exists",false),false)).put(s.memberWrite()).put(f.write(path(),state,null,new JSONObject().put("exists",false),false)));
        }else{
            JSONObject member=f.get(s.member(f.uid));
            if(member==null)f.commit(new JSONArray().put(s.memberWrite()));
            else if(member.getJSONObject("fields").getJSONObject("banned").getBoolean("booleanValue"))throw new IOException("Du wurdest aus dieser Welt entfernt.");
            JSONObject document=f.get(s.base());if(document==null)throw new IOException("Diese Welt existiert nicht.");JSONObject root=document.getJSONObject("fields");
            s.owner=FirebaseRest.string(root,"owner");s.name=FirebaseRest.string(root,"name");s.side=FirebaseRest.integer(root,"side");s.seed=integer(root,"seed");s.invitation=new OnlineWorld(s.invitation.id,FirebaseRest.string(root,"invite"));
            int protocol=FirebaseRest.integer(root,"protocol");boolean closed=root.getJSONObject("closed").getBoolean("booleanValue");
            boolean migrating=root.has("migration")&&FirebaseRest.string(root,"migration").equals("v5");
            s.deletionOnly=closed&&!migrating||protocol<4;
            if(s.deletionOnly)throw new IOException(closed?"Diese Welt wurde geschlossen.":"Diese Welt nutzt eine alte Landschaft. Bitte mit 0.8.3 eine neue Welt anlegen.");
            if(protocol==4||closed&&migrating){
                if(!s.owner())throw new IOException("Bitte zuerst den Weltbesitzer mit der neuen Version verbinden lassen, damit Bauten und Vorräte migriert werden.");
                new OnlineMigration(this).run(document);
            }else if(protocol!=5)throw new IOException("Bitte alle Geräte auf dieselbe aktuelle App-Version aktualisieren.");
        }
        if(s.closed)return;
        if(s.side!=2&&s.side!=3)throw new IOException("Ungültige Weltgröße.");
        readSync();
        if(!fields(state,"players").has(f.uid)){
            JSONObject joined=new JSONObject().put("access",map(new JSONObject().put(f.uid,FirebaseRest.value(true)))).put("players",map(new JSONObject().put(f.uid,map(player(new Story())))));
            f.commit(new JSONArray().put(f.write(path(),joined,new String[]{"access."+OnlineSession.quoted(f.uid),"players."+OnlineSession.quoted(f.uid)},new JSONObject().put("exists",true),false)));readSync();
        }
        s.regionCache=new OnlineRegionCache(new File(s.activity.getCacheDir(),"online-regions-v5"),s.invitation.id,s.seed,s.side);
        if(s.closed){s.regionCache.close();return;}
        s.world=World.onlineSpace(s.seed,s.side);World origin=OnlineTerrain.region(s.seed,0,s.side);s.world.x=origin.x;s.world.y=origin.y;s.world.z=origin.z;s.px=origin.x;s.py=origin.y;s.pz=origin.z;
        OnlineInventory.apply(s.world.progress,s.inventory);syncVisible(true);heartbeat();s.remember();
    }
    void readSync()throws Exception{
        List<String> projection=null;
        if(s.world!=null&&!s.inventoryUncertain&&state.has("access")){
            projection=new ArrayList<>(Arrays.asList("phase","open","access","regions"));
            for(String uid:keys(fields(state,"access"))){String prefix="players."+OnlineSession.quoted(uid)+".";projection.add(prefix+"seq");projection.add(prefix+"position");if(!uid.equals(f.uid))projection.add(prefix+"log");}
            projection.add(prefix()+"rev");projection.add(prefix()+"inventory");
        }
        FirebaseRest.Snapshot snapshot=f.snapshot(path(),projection);if(snapshot.document==null)throw new IOException("Neue Firestore-Regeln veröffentlichen und Migration beim Weltbesitzer abschließen.");
        state=snapshot.document.getJSONObject("fields");if(!FirebaseRest.string(state,"phase").equals("ready")||!state.getJSONObject("open").getBoolean("booleanValue"))throw new IOException("Migration läuft oder die Welt wurde geschlossen.");
        s.announcedReadTime=snapshot.readTime;s.announcedRegions=fields(state,"regions");JSONObject people=fields(state,"players"),access=fields(state,"access");
        heads.clear();tail.clear();List<OnlineSession.Player> players=new ArrayList<>();
        for(String uid:keys(people)){
            JSONObject data=fields(people,uid);long seq=integer(data,"seq");if(seq<0||seq>=9000000000000L)throw new IOException("Ungültiger Abgleichstand.");heads.put(uid,seq);
            JSONObject log=data.has("log")?fields(data,"log"):new JSONObject();for(String slot:keys(log)){OnlineChange change=OnlineChange.decode(FirebaseRest.string(log,slot),uid);if(change.sequence>seq||!slot.equals("e"+(change.sequence%OnlineChange.TAIL)))throw new IOException("Ungültige Änderungsfolge.");tail.add(change);}
            JSONObject position=data.has("position")?fields(data,"position"):new JSONObject();if(position.has("updatedAt")&&access.has(uid)&&access.getJSONObject(uid).getBoolean("booleanValue")){
                String time=position.getJSONObject("updatedAt").getString("timestampValue");if(System.currentTimeMillis()-Instant.parse(time).toEpochMilli()<=OnlineSyncPolicy.PRESENCE_TTL_MS&&players.size()<32)players.add(new OnlineSession.Player(uid,new JSONObject(position.toString()).put("banned",FirebaseRest.value(false))));
            }
        }
        s.players=Collections.unmodifiableList(players);
        if(people.has(f.uid)){
            JSONObject me=fields(people,f.uid);long rev=integer(me,"rev");sequence=integer(me,"seq");
            String encrypted=FirebaseRest.string(me,"inventory");
            if(encrypted.isEmpty()){
                JSONObject legacy=f.get(s.inventoryPath());Story adopted=legacy==null?new Story():s.decodeInventory(legacy.getJSONObject("fields"));
                encrypted=encrypt(adopted,rev+1);JSONObject patch=FirebaseRest.fields("inventory",encrypted,"rev",rev+1);
                f.commit(new JSONArray().put(playerPatch(patch,new ArrayList<>(Arrays.asList("inventory","rev")),null)));rev++;me.put("inventory",FirebaseRest.value(encrypted)).put("rev",FirebaseRest.value(rev));
            }
            if(s.inventoryVersion.isEmpty()||rev!=revision){s.inventory=cipher(false).decrypt(encrypted,s.invitation.id,f.uid,rev);revision=rev;s.inventoryVersion=Long.toString(rev);if(s.world!=null)s.applyInventory(s.inventory);}

        }
        s.inventoryUncertain=false;
    }
    void syncVisible(boolean initial)throws Exception{
        World target=s.world;
        try{for(int id:OnlineWorld.visibleSectors(s.px,s.pz,s.side,82)){
            if(s.closed)return;OnlineSession.Region region=s.regions.get(id);
            if(region==null){region=new OnlineSession.Region(id);s.regions.put(id,region);OnlineRegionCache.Snapshot saved=s.regionCache==null?null:s.regionCache.load(id);
                if(saved!=null){region.versions.putAll(saved.versions);region.types.putAll(saved.types);region.renewable.putAll(saved.renewable);region.heads.putAll(saved.heads);region.revision=saved.revision;region.watermark=saved.watermark;region.complete=true;FirebaseRest.DIAGNOSTICS.cacheRestored(saved.types.size());}}
            long announced=s.announcedRegions.has("s"+id)?integer(fields(s.announcedRegions,"s"+id),"revision"):0;
            if(region.revision>announced)throw new IOException("Weltstand zurückgesetzt. Bitte erneut beitreten.");
            World tile=region.generated?null:OnlineTerrain.region(s.seed,id,s.side);if(tile!=null){s.streaming=true;for(String key:region.types.keySet()){String[] xyz=key.split("_");tile.set(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),region.types.get(key));}}
            boolean changed=announced!=region.revision||!region.complete;
            List<OnlineChange> delta=region.complete?OnlineChange.reconcile(id,region.revision,announced,region.heads,region.versions,region.own,tail):null;
            if(delta==null)loadChunks(region,tile);
            else{for(OnlineChange c:delta)cell(region,tile,c.x,c.y,c.z,c.type,c.renewable,c.token);if(region.ownChanges>0)FirebaseRest.DIAGNOSTICS.ownRegionSkipped();if(!delta.isEmpty())FirebaseRest.DIAGNOSTICS.tailApplied(delta.size());}
            if(s.closed)return;region.watermark=s.announcedReadTime;region.revision=announced;region.ownChanges=0;region.own.clear();region.complete=true;region.heads.clear();region.heads.putAll(heads);
            if(changed&&s.regionCache!=null)s.regionCache.offer(id,new OnlineRegionCache.Snapshot(announced,region.watermark,region.versions,region.types,region.renewable,region.heads));
            if(tile!=null){region.generated=true;if(initial){OnlineInventory.install(target,tile,id,s.side);s.markReady(id);}else s.activity.game.gl.queueEvent(()->{if(!s.closed&&s.world==target){synchronized(target){OnlineInventory.install(target,tile,id,s.side);}for(int x=id%s.side*World.W;x<(id%s.side+1)*World.W;x+=16)for(int z=id/s.side*World.D;z<(id/s.side+1)*World.D;z+=16)s.activity.game.renderer.dirty(x,z);s.markReady(id);}});}
        }}finally{s.streaming=false;}
    }
    void cell(OnlineSession.Region region,World tile,int x,int y,int z,int type,boolean renewable,String token){
        String key=x+"_"+y+"_"+z,previous=region.versions.put(key,token);region.types.put(key,type);region.renewable.put(key,renewable);
        if(tile!=null)tile.set(x,y,z,type);else if(!token.equals(previous))s.apply(s.world,region.id,x,y,z,type,false);
    }
    void loadChunks(OnlineSession.Region region,World tile)throws Exception{
        JSONArray cursor=null;
        do{
            JSONObject query=new JSONObject().put("from",new JSONArray().put(new JSONObject().put("collectionId","chunks"))).put("orderBy",new JSONArray().put(OnlineSession.order("updatedAt")).put(OnlineSession.order("__name__"))).put("limit",4);
            if(!region.watermark.isEmpty())query.put("where",new JSONObject().put("fieldFilter",new JSONObject().put("field",OnlineSession.field("updatedAt")).put("op","GREATER_THAN").put("value",new JSONObject().put("timestampValue",region.watermark))));
            if(cursor!=null)query.put("startAt",new JSONObject().put("values",cursor).put("before",false));
            JSONArray page=f.query(s.base()+"/sectors/"+region.id,query,s.announcedReadTime);int count=0;cursor=null;
            for(int i=0;i<page.length();i++)if(page.getJSONObject(i).has("document")){
                JSONObject doc=page.getJSONObject(i).getJSONObject("document"),data=doc.getJSONObject("fields"),cells=fields(data,"cells");String chunk=doc.getString("name").substring(doc.getString("name").lastIndexOf('/')+1);
                for(String key:keys(cells)){
                    String[] xyz=key.split("_"),value=FirebaseRest.string(cells,key).split(":",-1);if(xyz.length!=3||value.length!=3)throw new IOException("Ungültige Blockdaten.");
                    int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]),type=Integer.parseInt(value[0]);boolean renewable=value[1].equals("1");
                    if(!OnlineWorld.validBlock(x,y,z,type)||!value[1].matches("[01]")||!value[2].matches("[a-f0-9]{32}")||!chunk.equals(x/16+"_"+y/16+"_"+z/16)||!key.equals(x+"_"+y+"_"+z))throw new IOException("Ungültige Blockdaten.");
                    cell(region,tile,x,y,z,type,renewable,value[2]);
                }
                count++;cursor=new JSONArray().put(data.getJSONObject("updatedAt")).put(new JSONObject().put("referenceValue",doc.getString("name")));
            }
            if(count<4)cursor=null;
        }while(cursor!=null&&!s.closed);
    }
    JSONObject presence(OnlineSession.Presence p)throws JSONException{return FirebaseRest.fields("name",s.playerName,"sector",p.cell,"x",p.x-(p.cell%s.side)*World.W,"y",p.y,"z",p.z-(p.cell/s.side)*World.D,"yaw",p.angle);}
    String prefix(){return "players."+OnlineSession.quoted(f.uid)+".";}
    JSONObject playerPatch(JSONObject data,List<String> mask,OnlineSession.Presence p)throws JSONException{
        if(p!=null){data.put("position",map(presence(p)));mask.add("position");}
        List<String> full=new ArrayList<>();for(String item:mask)full.add(prefix()+item);
        JSONObject write=f.write(path(),new JSONObject().put("players",map(new JSONObject().put(f.uid,map(data)))),full.toArray(new String[0]),new JSONObject().put("exists",true),false);
        if(p!=null)write.put("updateTransforms",new JSONArray().put(new JSONObject().put("fieldPath",prefix()+"position.updatedAt").put("setToServerValue","REQUEST_TIME")));
        return write;
    }
    JSONObject inventoryPatch(Story next,List<String> mask)throws Exception{
        Collections.addAll(mask,"inventory","rev");return FirebaseRest.fields("rev",revision+1,"inventory",encrypt(next,revision+1));
    }
    void heartbeat()throws Exception{OnlineSession.Presence p=s.duePresence();if(p==null)return;f.commit(new JSONArray().put(playerPatch(new JSONObject(),new ArrayList<>(),p)));s.presenceConfirmed(p);}
    void edit(int x,int y,int z,int type){
        World target=s.world;if(target==null||!target.contains(x,y,z)||y==0||type<0||type>=World.NAMES.length||!s.beginAction())return;
        int region=OnlineWorld.sectorAt(x,z,s.side),lx=x-region%s.side*World.W,lz=z-region/s.side*World.D,expected=target.get(x,y,z);long started=s.actionStarted;
        s.worker.execute(()->{try{
            OnlineSession.Region data=s.regions.get(region);if(data==null||!data.generated)return;String key=lx+"_"+y+"_"+lz;
            if(data.types.containsKey(key)&&data.types.get(key)!=expected){s.apply(target,region,lx,y,lz,data.types.get(key),false);s.activity.game.message("Der Block wurde inzwischen geändert. Bitte erneut versuchen.");return;}
            if(type!=0&&expected!=0){s.activity.game.message("Hier steht bereits ein Block.");return;}
            Story next=OnlineInventory.copy(s.inventory);String problem=type==0?OnlineInventory.harvest(next,expected):OnlineInventory.place(next,type);if(!problem.isEmpty()){s.activity.game.message(problem);return;}
            boolean water=type==0&&expected==10&&(data.renewable.getOrDefault(key,false)||!data.versions.containsKey(key));
            OnlineChange c=new OnlineChange(sequence+1,region,lx,y,lz,water?10:type,water,OnlineWorld.randomId(),OnlineChange.nonce(data.versions.getOrDefault(key,"")),f.uid);
            List<String> mask=new ArrayList<>();JSONObject entry=inventoryPatch(next,mask);entry.put("seq",FirebaseRest.value(c.sequence)).put("last",map(FirebaseRest.fields("sector",region,"x",lx,"y",y,"z",lz,"type",c.type,"renewable",water,"token",c.token,"before",c.before)))
                .put("log",map(new JSONObject().put("e"+(c.sequence%OnlineChange.TAIL),FirebaseRest.value(c.encode()))));Collections.addAll(mask,"seq","last","log.e"+(c.sequence%OnlineChange.TAIL));
            OnlineSession.Presence p=s.duePresence();JSONObject overview=playerPatch(entry,mask,p);String field="regions.s"+region;
            JSONArray transforms=overview.optJSONArray("updateTransforms");if(transforms==null)transforms=new JSONArray();overview.put("updateTransforms",transforms.put(new JSONObject().put("fieldPath",field+".revision").put("increment",FirebaseRest.value(1))).put(new JSONObject().put("fieldPath",field+".updatedAt").put("setToServerValue","REQUEST_TIME")));
            JSONObject chunk=f.write(chunks(region)+"/"+c.chunk(),new JSONObject().put("cells",map(new JSONObject().put(key,FirebaseRest.value(c.value())))),new String[]{"cells."+OnlineSession.quoted(key)},null,true);
            try{f.commit(new JSONArray().put(chunk).put(overview));data.versions.put(key,c.token);data.types.put(key,c.type);data.renewable.put(key,water);data.own.add(c);data.ownChanges++;sequence++;revision++;s.inventory=next;s.inventoryVersion=Long.toString(revision);
                if(p!=null){s.presenceConfirmed(p);FirebaseRest.DIAGNOSTICS.positionMerged();}s.apply(target,region,lx,y,lz,c.type,false);s.applyInventory(next,started);
            }catch(FirebaseRest.Failure error){if(error.status!=403&&error.status!=409&&error.status!=412&&error.status!=400)throw error;readSync();syncVisible(false);s.activity.game.message("Der Block oder dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.");}
        }catch(Exception error){s.mutationFailed(error);}finally{s.pending=false;}});
    }
    void progressAction(int recipe){
        if(!s.beginAction())return;long started=s.actionStarted;s.worker.execute(()->{try{
            Story next=OnlineInventory.copy(s.inventory);String message;if(recipe<0){boolean claimed=next.claim();message=claimed?"Werkzeug / Auftrag freigeschaltet":next.goal();}else message=next.craft(recipe);
            if(!OnlineInventory.same(next,s.inventory)){List<String> mask=new ArrayList<>();JSONObject entry=inventoryPatch(next,mask);try{f.commit(new JSONArray().put(playerPatch(entry,mask,null)));revision++;s.inventory=next;s.inventoryVersion=Long.toString(revision);s.applyInventory(next,started);}
                catch(FirebaseRest.Failure error){if(error.status!=403&&error.status!=409&&error.status!=412&&error.status!=400)throw error;readSync();message="Dein Vorrat wurde inzwischen geändert. Bitte erneut versuchen.";}}
            String notice=message;s.activity.runOnUiThread(()->{if(!s.closed){s.activity.toast(notice);s.activity.online.refreshWorkbench();}});
        }catch(Exception error){s.mutationFailed(error);}finally{s.pending=false;}});
    }
    void ban(String uid){if(!s.owner()||uid.equals(s.owner))return;s.worker.execute(()->{try{f.commit(new JSONArray().put(f.write(path(),new JSONObject().put("access",map(new JSONObject().put(uid,FirebaseRest.value(false)))),new String[]{"access."+OnlineSession.quoted(uid)},new JSONObject().put("exists",true),false)).put(f.write(s.member(uid),FirebaseRest.fields("banned",true),new String[]{"banned"},new JSONObject().put("exists",true),false)));readSync();s.activity.runOnUiThread(()->s.activity.toast("Spieler entfernt und für diese Geräte-ID gesperrt."));}catch(Exception e){s.problem(e,false);}});}
    void deleteWorld(Runnable success,java.util.function.Consumer<String> failure){
        if(!s.owner()||s.closed)return;s.loading=true;s.healthy=false;s.worker.execute(()->{try{
            JSONObject root=f.get(s.base());if(root!=null){
                JSONArray close=new JSONArray().put(f.write(s.base(),FirebaseRest.fields("closed",true),new String[]{"closed"},new JSONObject().put("updateTime",root.getString("updateTime")),false));
                JSONObject current=FirebaseRest.integer(root.getJSONObject("fields"),"protocol")==5?f.get(path()):null;if(current!=null)close.put(f.write(path(),FirebaseRest.fields("open",false),new String[]{"open"},new JSONObject().put("exists",true),false));f.commit(close);
                for(int id=0;id<s.side*s.side;id++){s.deleteCollection(chunks(id),false);s.deleteCollection(s.base()+"/sectors/"+id+"/blocks",false);}s.deleteCollection(s.base()+"/members",true);
                f.commit(new JSONArray().put(new JSONObject().put("delete",f.documentName(s.syncPath()))).put(new JSONObject().put("delete",f.documentName(path()))));f.commit(new JSONArray().put(new JSONObject().put("delete",f.documentName(s.base()))));
            }s.activity.runOnUiThread(()->{if(!s.closed)success.run();});
        }catch(Exception error){s.activity.runOnUiThread(()->{if(!s.closed)failure.accept("Löschen unterbrochen. Die Welt bleibt gesperrt. Bitte erneut versuchen.");});}});
    }
}
