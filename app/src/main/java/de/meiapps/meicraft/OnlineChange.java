package de.meiapps.meicraft;

import java.io.IOException;
import java.util.*;

/** Compact, bounded commit tail. Cell nonces provide causal ordering even within one millisecond. */
final class OnlineChange {
    static final int TAIL=16;
    final long sequence;final int region,x,y,z,type;final boolean renewable;
    final String token,before,actor,nature;
    OnlineChange(long sequence,int region,int x,int y,int z,int type,boolean renewable,String token,String before,String actor){
        this(sequence,region,x,y,z,type,renewable,token,before,actor,"");
    }
    OnlineChange(long sequence,int region,int x,int y,int z,int type,boolean renewable,String token,String before,String actor,String nature){
        this.nature=nature;this.sequence=sequence;this.region=region;this.x=x;this.y=y;this.z=z;this.type=type;this.renewable=renewable;this.token=token;this.before=before;this.actor=actor;
    }
    String key(){return x+"_"+y+"_"+z;}
    String chunk(){return x/16+"_"+y/16+"_"+z/16;}
    String value(){return type+":"+(renewable?1:0)+":"+token+(nature.isEmpty()?"":":"+nature);}
    String encode(){return sequence+"/"+region+"/"+key()+"/"+type+"/"+(renewable?1:0)+"/"+token+"/"+before+(nature.isEmpty()?"":"/"+nature);}
    static OnlineChange decode(String text,String actor)throws IOException{
        try{
            String[] p=text.split("/",-1),xyz=p[2].split("_");if((p.length!=7&&p.length!=8)||xyz.length!=3)throw new IllegalArgumentException();
            OnlineChange c=new OnlineChange(Long.parseLong(p[0]),Integer.parseInt(p[1]),Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),Integer.parseInt(p[3]),p[4].equals("1"),p[5],p[6],actor,p.length==8?p[7]:"");
            if(!c.nature.matches("(leaf|decor|user|charcoal|(wood|coal|glass|ore|ritual)_[0-9]_[a-f0-9]{1,16}|tree_[0-3]_[0-9]{1,8}_[0-9]{1,16})?")||c.sequence<1||c.region<0||c.region>8||!OnlineWorld.validBlock(c.x,c.y,c.z,c.type)||!p[4].matches("[01]")||!c.token.matches("[a-f0-9]{32}")||!c.before.matches("([a-f0-9]{32})?")||c.token.equals(c.before)||!text.equals(c.encode()))throw new IllegalArgumentException();
            return c;
        }catch(RuntimeException e){throw new IOException("Ungültige Änderungsfolge.",e);}
    }
    static String nonce(String version){return version.matches("[a-f0-9]{32}")?version:"";}
    /** Return final changes, or null if a gap/branch cannot be proved complete. Never mutates the caller. */
    static List<OnlineChange> reconcile(int region,long seen,long announced,Map<String,Long> heads,Map<String,String> versions,List<OnlineChange> own,List<OnlineChange> tail){
        if(seen<0||announced<seen)return null;
        Map<String,OnlineChange> all=new LinkedHashMap<>();Set<String> confirmed=new HashSet<>();
        for(OnlineChange c:own){all.put(c.token,c);confirmed.add(c.token);}
        long extra=0;
        for(OnlineChange c:tail)if(c.region==region&&c.sequence>heads.getOrDefault(c.actor,0L)&&!all.containsKey(c.token)){all.put(c.token,c);extra++;}
        if(announced-seen!=confirmed.size()+extra)return null;
        Map<String,List<OnlineChange>> cells=new HashMap<>();for(OnlineChange c:all.values())cells.computeIfAbsent(c.key(),key->new ArrayList<>()).add(c);
        List<OnlineChange> result=new ArrayList<>();
        for(Map.Entry<String,List<OnlineChange>> cell:cells.entrySet()){
            Map<String,OnlineChange> after=new HashMap<>(),before=new HashMap<>();
            for(OnlineChange c:cell.getValue())if(after.put(c.token,c)!=null||before.put(c.before,c)!=null)return null;
            String current=nonce(versions.getOrDefault(cell.getKey(),"")),cursor=current;Set<String> visited=new HashSet<>();
            while(after.containsKey(cursor)){OnlineChange c=after.get(cursor);if(!visited.add(c.token))return null;cursor=c.before;}
            cursor=current;OnlineChange last=null;
            while(before.containsKey(cursor)){OnlineChange c=before.get(cursor);if(!visited.add(c.token))return null;last=c;cursor=c.token;}
            if(visited.size()!=after.size())return null;
            if(last!=null)result.add(last);
        }
        return result;
    }
}
