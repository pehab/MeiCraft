package de.meiapps.meicraft;

import java.util.*;

public final class OnlineDeltaTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static String token(long n){return String.format(java.util.Locale.ROOT,"%032x",n);}
    static OnlineChange change(long seq,int x,String before,String after,String actor){return new OnlineChange(seq,0,x,30,1,7,false,after,before,actor);}
    public static void main(String[] args)throws Exception{
        OnlineChange a=change(1,1,"",token(1),"a"),b=change(1,1,token(1),token(2),"b");
        check(OnlineChange.decode(a.encode(),"a").encode().equals(a.encode()),"Compact event round-trip including empty original nonce");
        check(OnlineChange.reconcile(0,0,2,new HashMap<>(),new HashMap<>(),List.of(),List.of(b,a)).get(0).token.equals(token(2)),"Causal chain orders cross-player edits regardless of tail order and clock precision");
        Map<String,String> current=new HashMap<>();current.put(a.key(),b.token);
        check(OnlineChange.reconcile(0,0,2,new HashMap<>(),current,List.of(b),List.of(a,b)).isEmpty(),"Older peer change cannot overwrite our later confirmed edit");
        current.put(a.key(),a.token);check(OnlineChange.reconcile(0,0,2,new HashMap<>(),current,List.of(a),List.of(b,a)).get(0).token.equals(b.token),"Later peer edit follows confirmed own nonce");
        check(OnlineChange.reconcile(0,0,3,new HashMap<>(),current,List.of(a),List.of(a,b))==null,"Missing event forces chunk fallback");
        check(OnlineChange.reconcile(0,0,2,new HashMap<>(),new HashMap<>(),List.of(),List.of(a,change(1,1,"",token(3),"b")))==null,"Causal fork cannot be accepted as a complete snapshot");
        check(OnlineChange.reconcile(0,2,1,new HashMap<>(),current,List.of(),List.of())==null,"Revision rollback never advances a checkpoint");
        check(OnlineChange.reconcile(0,2,2,Map.of("a",1L,"b",1L),current,List.of(),List.of(a,b)).isEmpty(),"Persisted actor heads exclude already completed old tail entries");
        for(String invalid:new String[]{"",a.encode()+"/extra",a.encode().replace("1_30_1","1_0_1"),a.encode().replace("/7/","/24/"),a.encode().replace("/0/1_","/9/1_"),a.encode().replace("/0/000","/2/000")}){
            boolean failed=false;try{OnlineChange.decode(invalid,"a");}catch(java.io.IOException expected){failed=true;}check(failed,"Reject invalid event: "+invalid);
        }
        for(int n:new int[]{2,3}){simulate(n,3600,10,1);simulate(n,30,1,12);}
        System.out.println("PASS: "+checks+" causal event, reconnect, gap and two/three-builder contracts");
    }
    static void simulate(int players,int cycles,int interval,int burst){
        @SuppressWarnings("unchecked") Map<String,String>[] local=new Map[players];
        @SuppressWarnings("unchecked") Map<String,Long>[] heads=new Map[players];
        @SuppressWarnings("unchecked") List<OnlineChange>[] own=new List[players];
        @SuppressWarnings("unchecked") Deque<OnlineChange>[] logs=new Deque[players];
        Map<String,String> server=new HashMap<>();long[] sequence=new long[players],seen=new long[players];long revision=0,nonce=100,actions=0,overviewReads=0,fallbacks=0;
        for(int p=0;p<players;p++){local[p]=new HashMap<>();heads[p]=new HashMap<>();own[p]=new ArrayList<>();logs[p]=new ArrayDeque<>();}
        for(int cycle=0;cycle<cycles;cycle++){
            if(cycle%interval==0)for(int step=0;step<burst;step++)for(int p=0;p<players;p++){
                int action=cycle/interval*burst+step;int x=p*4+action%4,z=action/4%16;String key=x+"_30_"+z,before=local[p].getOrDefault(key,"");
                check(before.equals(server.getOrDefault(key,"")),"Independent cells in one shared chunk do not cause contention");
                OnlineChange c=new OnlineChange(++sequence[p],0,x,30,z,7,false,token(++nonce),before,"p"+p);server.put(key,c.token);local[p].put(key,c.token);own[p].add(c);logs[p].add(c);if(logs[p].size()>OnlineChange.TAIL)logs[p].removeFirst();revision++;actions++;
            }
            List<OnlineChange> tail=new ArrayList<>();for(Deque<OnlineChange> log:logs)tail.addAll(log);Collections.reverse(tail);
            for(int p=0;p<players;p++){
                overviewReads++;List<OnlineChange> delta=OnlineChange.reconcile(0,seen[p],revision,heads[p],local[p],own[p],tail);
                if(delta==null){fallbacks++;local[p].clear();local[p].putAll(server);}else for(OnlineChange c:delta)local[p].put(c.key(),c.token);
                check(local[p].equals(server),"All clients converge after each two-second snapshot");seen[p]=revision;own[p].clear();for(int peer=0;peer<players;peer++)heads[p].put("p"+peer,sequence[peer]);
            }
        }
        check(fallbacks==0,"Typical collaborative building needs no block/chunk query after the initial base");
        check(actions==(long)((cycles+interval-1)/interval)*burst*players&&overviewReads==(long)cycles*players,"Two-hour scenario keeps the same two-second position loop");
        System.out.println("V5 BUDGET "+players+" builders / "+(cycles*2)+" s, burst="+burst+": "+overviewReads+" overviews, "+actions+" immediate block commits; block writes "+(actions*3)+" -> "+(actions*2)+"; additional chunk queries "+fallbacks);
    }
}
