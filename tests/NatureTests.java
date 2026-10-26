package de.meiapps.meicraft;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class NatureTests {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static World flat(boolean story){
        World w=new World(31,story,false);Arrays.fill(w.blocks,(byte)0);
        for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)w.set(x,1,z,3);
        w.x=60.5f;w.y=3.62f;w.z=60.5f;return w;
    }
    static void advance(World w,float seconds){for(int i=0;i<Math.round(seconds*20);i++)w.nature.tick(w,.05f);}
    static byte[] legacy(World w,int format)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){
            out.writeInt(0x4d454943);out.writeInt(format);out.writeLong(w.seed);out.writeFloat(w.x);out.writeFloat(w.y);out.writeFloat(w.z);out.writeFloat(w.yaw);out.writeFloat(w.pitch);out.writeInt(w.selected);out.writeBoolean(w.flying);out.writeInt(w.blocks.length);out.write(w.blocks);
            if(format>=2){out.writeBoolean(w.story);out.writeInt(w.progress.chapter);out.writeInt(w.progress.iron);out.writeInt(w.progress.placed);out.writeInt(w.progress.glassPlaced);int count=format==2?15:16;out.writeInt(count);for(int i=0;i<count;i++)out.writeInt(w.progress.inventory[i]);}
            if(format>=3){out.writeInt(w.progress.buckets);out.writeBoolean(w.progress.sword);w.survival.write(out);}
        }
        return bytes.toByteArray();
    }
    static void rejects(World w,String message)throws IOException{
        boolean rejected=false;try{World.decode(w.encode());}catch(IOException e){rejected=true;}check(rejected,message);
    }
    public static void main(String[] args)throws Exception{
        for(int seed=0;seed<40;seed++){
            World w=new World(seed,true);int count=w.nature.springs.cardinality();check(count>=1&&count<=6,"bounded underground springs seed "+seed);
            check(!w.touchesLava(w.x,w.y,w.z),"new caves preserve safe spawn seed "+seed);
            for(int i=w.nature.springs.nextSetBit(0);i>=0;i=w.nature.springs.nextSetBit(i+1))check(i/(World.W*World.D)>=3&&i/(World.W*World.D)<=5&&w.blocks[i]==15,"underground spring depth and material");
        }
        World w=new World(31,true,false);w.progress.chapter=7;w.progress.inventory[9]=42;
        w.set(10,4,10,7);w.set(12,4,12,0);w.set(14,4,14,8);
        for(int format=1;format<=3;format++){
            World old=World.decode(legacy(w,format));check(Arrays.equals(w.blocks,old.blocks),"loading old format "+format+" never modifies landscape");check(!old.nature.cavesAdded,"old world offers explicit cave addition");
            World reloaded=World.decode(old.encode());check(Arrays.equals(old.blocks,reloaded.blocks),"legacy roundtrip remains exact");
        }
        byte[] before=w.blocks.clone();int added=w.nature.addCaves(w);check(added>0,"old world can gain caves");
        check(w.get(10,4,10)==7&&w.get(12,4,12)==0&&w.get(14,4,14)==8,"migration retains structures and excavations");
        check(w.progress.chapter==7&&w.progress.inventory[9]==42,"migration preserves progress and inventory");
        boolean surfaceUnchanged=true;for(int y=10;y<World.H;y++)for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)if(w.blocks[World.index(x,y,z)]!=before[World.index(x,y,z)])surfaceUnchanged=false;check(surfaceUnchanged,"migration leaves surface and buildings untouched");
        before=w.blocks.clone();check(w.nature.addCaves(w)==0&&Arrays.equals(before,w.blocks),"cave migration only happens once");
        World changed=new World(99,true,false);for(int y=2;y<=9;y++)for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)changed.set(x,y,z,7);
        before=changed.blocks.clone();check(changed.nature.addCaves(changed)==0&&Arrays.equals(before,changed.blocks),"fully developed underground is never overwritten");
        w=flat(true);w.progress.buckets=2;w.set(20,2,20,10);
        check(w.scoopLiquid(20,2,20).contains("gefüllt")&&w.get(20,2,20)==10,"natural water refills");
        check(w.place(22,2,20,10)&&w.progress.buckets==2&&w.progress.inventory[10]==0,"pour water returns bucket");
        check(w.scoopLiquid(22,2,20).contains("gefüllt")&&w.get(22,2,20)==0,"placed water is finite");
        w.set(20,2,22,15);w.nature.springs.set(World.index(20,2,22));
        check(w.scoopLiquid(20,2,22).contains("gefüllt")&&w.get(20,2,22)==15&&w.nature.spring(20,2,22),"lava spring refills after scooping");
        check(!w.scoopLiquid(20,2,22).contains("gefüllt")&&w.progress.inventory[15]==1,"renewable source still needs an empty bucket");
        w.progress.inventory[10]=1000000;w.progress.buckets=1;check(!w.scoopLiquid(20,2,20).contains("gefüllt")&&w.progress.buckets==1,"full inventory cannot consume bucket");
        check(!w.mine(20,2,20),"water cannot be mined into unlimited inventory");
        w=flat(false);w.set(20,2,20,15);w.set(21,2,20,10);advance(w,1);
        check(w.get(20,2,20)==3&&w.get(21,2,20)==10,"water cools touching lava, water remains");
        w.set(30,2,30,15);w.set(31,2,30,4);advance(w,4);check(w.get(31,2,30)==8&&w.get(30,2,30)==15,"lava heat makes glass without consuming lava");
        w.set(40,2,40,4);w.set(42,2,40,15);advance(w,4);check(w.get(40,2,40)==4,"heat needs face contact");
        w=flat(false);w.set(20,2,20,1);w.set(21,2,20,2);w.set(22,2,20,2);w.set(22,3,20,3);w.set(24,2,20,2);
        advance(w,40);check(w.get(21,2,20)==1,"exposed dirt next to grass grows grass");check(w.get(22,2,20)==2,"covered dirt stays dirt");check(w.get(24,2,20)==2,"isolated dirt stays dirt");
        w=flat(false);check(w.place(20,2,20,10),"creative water source placed");advance(w,12);
        check(w.get(24,2,20)==10&&w.get(25,2,20)==0,"water horizontal reach is four blocks");
        check(w.nature.stream(21,2,20)&&!w.nature.stream(20,2,20),"stream and source are distinct");
        World saved=World.decode(w.encode());check(Arrays.equals(w.blocks,saved.blocks)&&Arrays.equals(w.nature.levels,saved.nature.levels),"flow metadata persists");
        w.story=true;w.progress.buckets=1;check(!w.scoopLiquid(21,2,20).contains("gefüllt")&&w.progress.buckets==1,"stream cannot duplicate buckets");
        check(w.scoopLiquid(20,2,20).contains("gefüllt"),"source can be recovered");advance(w,16);
        check(w.get(24,2,20)==0&&w.nature.active.isEmpty(),"removing source drains all dependent flow");
        w=flat(false);w.place(20,2,20,15);advance(w,20);check(w.get(22,2,20)==15&&w.get(23,2,20)==0,"lava horizontal reach is two blocks");
        w=flat(false);w.place(20,8,20,10);advance(w,16);check(w.get(20,2,20)==10&&w.get(24,2,20)==10,"water falls before spreading along floor");
        w=flat(false);for(int z=10;z<30;z++)for(int y=2;y<6;y++)w.set(22,y,z,3);w.place(20,2,20,10);advance(w,10);check(w.get(22,2,20)==3&&w.get(23,2,20)==0,"solid walls contain flow");
        w=flat(false);w.place(20,2,20,10);w.nature.flowEnabled=false;advance(w,3);check(w.get(21,2,20)==0,"flow toggle freezes spread");
        saved=World.decode(w.encode());check(!saved.nature.flowEnabled,"flow preference persists");w.nature.flowEnabled=true;advance(w,3);check(w.get(21,2,20)==10,"flow resumes when enabled");
        before=w.blocks.clone();w.nature.tick(w,Float.NaN);w.nature.tick(w,-1);check(Arrays.equals(w.blocks,before),"invalid time is ignored");
        w=flat(false);w.set(20,2,20,10);w.nature.source(20,2,20);w.nature.levels[World.index(20,2,20)]=9;rejects(w,"invalid flow level rejected");
        w=flat(false);w.nature.springs.set(World.index(20,2,20));rejects(w,"spring without lava rejected");
        w=flat(false);w.place(15,2,15,10);long chunks=w.nature.takeDirtyChunks();advance(w,2);check(chunks!=0&&w.nature.takeDirtyChunks()!=0,"nature changes invalidate renderer chunks");
        w=flat(false);for(int x=5;x<85;x+=10)for(int z=5;z<85;z+=10)w.place(x,30,z,10);
        advance(w,35);check(w.nature.active.cardinality()<=Nature.MAX_FLOW,"many waterfalls respect global fluid budget");
        saved=World.decode(w.encode());check(saved.nature.active.cardinality()==w.nature.active.cardinality(),"budget-sized simulation roundtrips");
        for(int i=w.nature.active.nextSetBit(0);i>=0;){int next=w.nature.active.nextSetBit(i+1);if(w.nature.levels[i]==Nature.SOURCE)w.set(i%World.W,i/(World.W*World.D),i/World.W%World.D,0);i=next;}
        advance(w,40);check(w.nature.active.isEmpty(),"large waterfall network drains after source removal");
        System.out.println("PASS: "+checks+" nature assertions");
    }
}
