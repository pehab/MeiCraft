package de.meiapps.meicraft;

import java.io.IOException;
import java.util.*;

/** Causal reconciliation shared by live chunks and immutable checkpoint snapshots. */
final class OnlineJournal {
    static String chunk(OnlineChange c){return c.region+"_"+c.chunk();}
    static List<OnlineChange> advance(String chunk,long before,long after,Map<String,Long> heads,Map<String,String> versions,List<OnlineChange> tail){
        List<OnlineChange> selected=new ArrayList<>();int region=Integer.parseInt(chunk.substring(0,chunk.indexOf('_')));
        for(OnlineChange c:tail)if(chunk(c).equals(chunk))selected.add(c);
        return OnlineChange.reconcile(region,before,after,heads,versions,Collections.emptyList(),selected);
    }
    static final class Lease {
        static final long LENGTH=15000,RENEW=5000;
        static boolean claim(String holder,String self,long until,long now){return holder.equals(self)||until<=now;}
        static boolean valid(String holder,String self,long until,long now){return holder.equals(self)&&until>now;}
    }
    static String eventKey(long revision){if(revision<0||revision>=9000000000000L)throw new IllegalArgumentException("revision");return Long.toString(revision);}
    static void validateCell(String key,int type,boolean water,String token)throws IOException{
        try{String[] xyz=key.split("_",-1);if(xyz.length!=3||!OnlineWorld.validBlock(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]),type)||!token.matches("[a-f0-9]{32}")||water&&type!=10)throw new IllegalArgumentException();}
        catch(RuntimeException e){throw new IOException("Ungültige Journal-Zelle.",e);}
    }
}
