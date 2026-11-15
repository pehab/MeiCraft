package de.meiapps.meicraft;

import java.util.*;

public final class JournalTests {
    static int checks;static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static String token(int n){return String.format(Locale.ROOT,"%032x",n);}
    public static void main(String[] args){
        Random random=new Random(702);int serial=1;
        for(int players=2;players<=4;players++)for(int run=0;run<150;run++){
            List<OnlineChange> history=new ArrayList<>();Map<String,Long> seqs=new HashMap<>();Map<String,Map<String,String>> finalCells=new HashMap<>();Map<String,Long> counters=new HashMap<>();int n=20+random.nextInt(150);
            for(int i=0;i<n;i++){String actor="p"+random.nextInt(players);long seq=seqs.getOrDefault(actor,0L)+1;seqs.put(actor,seq);int cx=random.nextInt(4),x=cx*16+random.nextInt(5);String chunk="0_"+cx+"_2_0",key=x+"_44_1";Map<String,String> cells=finalCells.computeIfAbsent(chunk,k->new HashMap<>());String previous=cells.getOrDefault(key,"");OnlineChange c=new OnlineChange(seq,0,x,44,1,2,false,token(serial++),previous.isEmpty()?"":previous.split(":")[2],actor);history.add(c);cells.put(key,c.value());counters.put(chunk,counters.getOrDefault(chunk,0L)+1);}
            Map<String,Long> startHeads=new HashMap<>();
            for(String chunk:finalCells.keySet()){
                Map<String,String> fetched=new HashMap<>();int readAt=random.nextInt(n+1);for(int i=0;i<readAt;i++){OnlineChange c=history.get(i);if(OnlineJournal.chunk(c).equals(chunk))fetched.put(c.key(),c.value());}
                Map<String,String> versions=new HashMap<>();for(String key:fetched.keySet())versions.put(key,fetched.get(key).split(":")[2]);
                List<OnlineChange> changes=OnlineJournal.advance(chunk,0,counters.get(chunk),startHeads,versions,history);check(changes!=null,"Complete history must reconcile independently read chunk snapshots");for(OnlineChange c:changes)fetched.put(c.key(),c.value());check(fetched.equals(finalCells.get(chunk)),"Coherent snapshot equals the serial state of all builders");
                List<OnlineChange> bounded=new ArrayList<>();for(OnlineChange c:history)if(c.sequence>seqs.get(c.actor)-16)bounded.add(c);
                List<OnlineChange> partial=OnlineJournal.advance(chunk,0,counters.get(chunk),startHeads,Collections.emptyMap(),bounded);if(partial!=null){Map<String,String> fromTail=new HashMap<>();for(OnlineChange c:partial)fromTail.put(c.key(),c.value());check(fromTail.equals(finalCells.get(chunk)),"A bounded tail never claims a complete snapshot while dropping a write");}
            }
        }
        check(OnlineJournal.Lease.claim("p1","p2",100,100),"Expired host can be replaced");check(!OnlineJournal.Lease.claim("p1","p2",101,100),"A live host cannot be replaced early");check(OnlineJournal.Lease.claim("p1","p1",101,100),"Host can renew its own lease");check(!OnlineJournal.Lease.valid("p1","p2",1000,100),"Lease identity matters");check(!OnlineJournal.Lease.valid("p1","p1",100,100),"Expired lease cannot publish a checkpoint");
        System.out.println("PASS: "+checks+" journal snapshot and host-fencing assertions for two to four builders");
    }
}
