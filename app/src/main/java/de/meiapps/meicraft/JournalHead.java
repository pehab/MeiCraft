package de.meiapps.meicraft;

import org.json.*;
import java.io.*;
import java.util.*;

/** Immutable decoded atomic RTDB head. JSON copies never cross mutable thread boundaries. */
final class JournalHead {
    final JSONObject raw,actors,regions,chunks;final long generation;
    final Map<String,Long> heads=new HashMap<>();final List<OnlineChange> tail=new ArrayList<>();
    JournalHead(JSONObject raw)throws Exception{
        this.raw=raw;generation=raw.getLong("g");if(generation<0||generation>=9000000000000L)throw new IOException("Ungültiger Journal-Stand.");
        actors=object(raw,"actors");regions=object(raw,"regions");chunks=object(raw,"chunks");if(actors.length()>256||chunks.length()>972)throw new IOException("Journal zu groß.");
        for(String uid:OnlineV5.keys(actors)){
            if(!uid.matches("[A-Za-z0-9_-]{1,128}"))throw new IOException("Ungültiger Spieler.");JSONObject a=actors.getJSONObject(uid);long rev=a.getLong("rev"),seq=a.getLong("seq");if(rev<0||seq<0||seq>rev||rev>=9000000000000L)throw new IOException("Ungültiger Vorratsstand.");heads.put(uid,seq);
            JSONObject log=object(a,"tail");if(log.length()>16)throw new IOException("Journal-Ring zu groß.");for(String slot:OnlineV5.keys(log)){OnlineChange c=OnlineChange.decode(log.getString(slot),uid);if(c.sequence>seq||!slot.equals("e"+c.sequence%16))throw new IOException("Ungültiger Journal-Ring.");tail.add(c);}
        }
        for(String key:OnlineV5.keys(chunks))if(!key.matches("[0-8]_[0-5]_[0-2]_[0-5]")||chunks.getLong(key)<1)throw new IOException("Ungültiger Chunk-Stand.");
    }
    static JSONObject object(JSONObject parent,String key){JSONObject object=parent.optJSONObject(key);return object==null?new JSONObject():object;}
    long region(int id){return regions.optLong("s"+id,0);}
    long chunk(String key){return chunks.optLong(key,0);}
    static Map<String,String> cells(JSONObject chunk)throws Exception{
        Map<String,String> result=new HashMap<>();JSONObject cells=object(chunk,"cells");if(cells.length()>4096)throw new IOException("Chunk zu groß.");
        for(String key:OnlineV5.keys(cells)){JSONObject c=cells.getJSONObject(key);int type=c.getInt("type");boolean water=c.getBoolean("water");String token=c.getString("token");OnlineJournal.validateCell(key,type,water,token);result.put(key,type+":"+(water?1:0)+":"+token);}
        return result;
    }
    static JSONObject jsonCells(Map<String,String> cells)throws Exception{
        JSONObject result=new JSONObject();for(String key:cells.keySet()){String[] v=cells.get(key).split(":");result.put(key,new JSONObject().put("type",Integer.parseInt(v[0])).put("water",v[1].equals("1")).put("token",v[2]));}return new JSONObject().put("cells",result);
    }
    static Map<String,String> versions(Map<String,String> cells){Map<String,String> result=new HashMap<>();for(String key:cells.keySet())result.put(key,cells.get(key).split(":")[2]);return result;}
}
