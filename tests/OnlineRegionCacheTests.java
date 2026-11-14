package de.meiapps.meicraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class OnlineRegionCacheTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static OnlineRegionCache.Snapshot snapshot(int count,long revision){
        Map<String,String> versions=new HashMap<>();Map<String,Integer> types=new HashMap<>();Map<String,Boolean> water=new HashMap<>();
        for(int i=0;i<count;i++){String key=i%96+"_"+(1+i/9216)+"_"+(i/96%96);versions.put(key,"2026-11-14T10:00:00Z");types.put(key,i%World.NAMES.length);water.put(key,i%7==0);}
        return new OnlineRegionCache.Snapshot(revision,"2026-11-14T10:00:01.123456789Z",versions,types,water,Map.of("peer",100L));
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("meicraft-cache-test");String id="a".repeat(32);
        try(OnlineRegionCache cache=new OnlineRegionCache(root.toFile(),id,123,2)){
            check(cache.load(0)==null,"Missing cache is a normal cold join");
            OnlineRegionCache.Snapshot source=snapshot(3000,3200);cache.save(0,source);OnlineRegionCache.Snapshot restored=cache.load(0);
            check(restored!=null&&restored.types.equals(source.types)&&restored.versions.equals(source.versions)&&restored.renewable.equals(source.renewable),"3000 complete deltas, air and renewable water survive restart");
            check(restored.watermark.equals(source.watermark)&&restored.revision==3200&&restored.heads.equals(source.heads),"Server snapshot nanoseconds and revision are exact");
            check(!OnlineSyncPolicy.reload(true,restored.revision,3200),"Unchanged warm join needs no block query");
            check(OnlineSyncPolicy.reload(true,restored.revision,3201),"Single offline peer revision requires delta query");
            try(OnlineRegionCache differentSeed=new OnlineRegionCache(root.toFile(),id,124,2);OnlineRegionCache differentSide=new OnlineRegionCache(root.toFile(),id,123,3);OnlineRegionCache differentWorld=new OnlineRegionCache(root.toFile(),"b".repeat(32),123,2)){
                check(differentSeed.load(0)==null&&differentSide.load(0)==null&&differentWorld.load(0)==null,"Cache never crosses terrain or world identities");
            }
            check(cache.load(1)==null,"Regions have independent checkpoints");
            byte[] valid=Files.readAllBytes(cache.file(0).toPath());
            for(int position:new int[]{0,4,8,90,valid.length/2,valid.length-1}){
                byte[] corrupt=valid.clone();corrupt[position]^=1;Files.write(cache.file(0).toPath(),corrupt);check(cache.load(0)==null,"Corrupt header/body/checksum falls back to cold join at "+position);
            }
            for(int length:new int[]{0,4,valid.length/2,valid.length-1}){Files.write(cache.file(0).toPath(),Arrays.copyOf(valid,length));check(cache.load(0)==null,"Truncated checkpoint is never treated as complete");}
            Files.write(cache.file(0).toPath(),Arrays.copyOf(valid,valid.length+1));check(cache.load(0)==null,"Trailing garbage invalidates checkpoint");
            Files.write(cache.file(0).toPath(),valid);
            OnlineRegionCache.Snapshot invalid=snapshot(1,3201);invalid.types.put("99_1_0",1);cache.save(0,invalid);
            check(cache.load(0).revision==3200,"Failed replacement retains the previous valid atomic checkpoint");
            OnlineRegionCache.Snapshot empty=snapshot(0,0);cache.save(1,empty);check(cache.load(1)!=null&&cache.load(1).types.isEmpty(),"Completed empty regions also avoid empty remote queries");
            // Queued snapshots are detached from live mutable region state and coalesced.
            OnlineRegionCache.Snapshot next=snapshot(3001,3201);cache.offer(0,next);cache.close();check(cache.writer.awaitTermination(10,TimeUnit.SECONDS),"Queued writes finish after session close without blocking rendering");
            check(cache.load(0).revision==3201&&cache.load(0).types.size()==3001,"Newest complete checkpoint survives close and reopen");
            System.out.println("Online cache tests: "+checks+" checks; warm unchanged join: 3000 block reads -> 0");
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
    }
}
