package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

public final class ForgeTests {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static World flat(){World w=new World(31,false);Arrays.fill(w.blocks,(byte)0);w.forge.initialize(w);w.nature.flowEnabled=false;w.x=70.5f;w.y=8;w.z=70.5f;w.story=true;w.progress.chapter=7;return w;}
    static void steps(World w,int n){for(int i=0;i<n;i++)w.forge.step(w);}
    static int get(World w,int i){return w.get(i%World.W,i/(World.W*World.D),i/World.W%World.D);}
    static void set(World w,int i,int b){w.set(i%World.W,i/(World.W*World.D),i/World.W%World.D,b);}
    static int count(World w,int type){int n=0;for(byte b:w.blocks)if(b==type)n++;return n;}
    static int[] build(World w,int rotation,boolean mirror){
        int[] cells=Forge.pattern(30,6,30,rotation,mirror);
        for(int n=0;n<6;n++){set(w,cells[n]-Forge.LAYER,15);set(w,cells[n],Forge.PATTERN[n][2]);}
        return cells;
    }
    static void rejects(World w,String message)throws Exception{boolean rejected=false;try{World.decode(w.encode());}catch(IOException e){rejected=true;}check(rejected,message);}
    public static void main(String[] args)throws Exception{
        World w=flat();w.set(20,5,20,15);w.set(20,6,20,13);steps(w,29);
        check(w.get(20,6,20)==13&&w.forge.heat(20,6,20)>0,"ore heats but cannot finish before 15 seconds");
        World saved=World.decode(w.encode());steps(saved,1);check(saved.get(20,6,20)==16&&saved.progress.iron==0,"saved smelting resumes and produces a world block, no remote inventory gift");
        saved.progress.chapter=3;check(saved.mine(20,6,20)&&saved.progress.iron==1&&saved.progress.inventory[16]==0,"stone pick collects one iron bar from raw iron");check(!saved.mine(20,6,20)&&saved.progress.iron==1,"raw iron cannot be collected twice");
        w=flat();w.set(20,5,20,15);w.set(20,6,20,13);steps(w,20);w.set(20,5,20,3);w.set(20,5,20,15);steps(w,10);check(w.get(20,6,20)==13,"briefly replacing the heat source resets smelting");steps(w,20);check(w.get(20,6,20)==16,"smelting can restart after interruption");
        w=flat();w.set(20,5,20,15);w.nature.levels[World.index(20,5,20)]=3;w.set(20,6,20,13);steps(w,40);check(w.get(20,6,20)==13,"lava streams are too cool to smelt");
        w=flat();w.set(20,5,20,10);w.set(20,6,20,13);steps(w,40);check(w.get(20,6,20)==13,"water cannot smelt ore");
        w=flat();w.set(20,5,20,15);w.set(21,5,20,13);steps(w,40);check(w.get(21,5,20)==13,"ore must sit directly above lava");
        for(int turn=0;turn<4;turn++)for(boolean mirror:new boolean[]{false,true}){
            w=flat();int[] cells=build(w,turn,mirror);steps(w,Forge.RITUAL_STEPS-1);
            check(count(w,11)==0&&count(w,14)==2,"ritual waits in every rotation and reflection");check(w.forge.heat(cells[2]%World.W,6,cells[2]/World.W%World.D)>0,"iron visibly heats during ritual");
            saved=World.decode(w.encode());steps(saved,1);check(count(saved,11)==1&&count(saved,14)==0&&count(saved,8)==0&&count(saved,3)==0,"one ritual consumes precisely its six ingredients");
            check(count(saved,15)==6,"all lava sources remain reusable");steps(saved,40);check(count(saved,11)==1,"finished ritual cannot duplicate its result");
        }
        w=flat();w.progress.iron=18;w.progress.inventory[3]=2;w.progress.inventory[8]=2;w.progress.inventory[15]=6;
        check(w.progress.craft(4).contains("hergestellt")&&w.progress.craft(4).contains("hergestellt"),"ritual iron blocks cost eighteen bars");
        int[] placed=Forge.pattern(30,6,30,0,false);
        for(int n=0;n<6;n++){int cell=placed[n];int x=cell%World.W,z=cell/World.W%World.D;check(w.place(x,5,z,15)&&w.place(x,6,z,Forge.PATTERN[n][2]),"story player places each lava source and ingredient from inventory");}
        steps(w,16);check(w.progress.iron==0&&w.progress.inventory[14]==0&&w.progress.inventory[3]==0&&w.progress.inventory[8]==0&&w.progress.inventory[15]==0,"ritual never refunds ingredients or iron");
        check(w.progress.buckets==6&&count(w,15)==6,"reusable basin and emptied buckets remain available");
        int output=Math.min(placed[2],placed[3]);check(w.mine(output%World.W,6,output/World.W%World.D)&&w.progress.inventory[11]==1,"crafted glowstone can be mined into inventory");
        w=flat();int[] cells=build(w,0,false);steps(w,10);set(w,cells[0],0);set(w,cells[0],3);steps(w,6);check(count(w,11)==0,"temporarily removing a ritual ingredient resets progress");steps(w,10);check(count(w,11)==1,"restored ritual eventually finishes");
        w=flat();cells=build(w,0,false);set(w,cells[0],8);steps(w,30);check(count(w,11)==0,"wrong material does not activate ritual");
        w=flat();cells=build(w,0,false);w.nature.levels[cells[0]-Forge.LAYER]=2;steps(w,30);check(count(w,11)==0,"each of the six lava tiles must be a source");
        w=flat();cells=build(w,0,false);set(w,cells[0]-Forge.LAYER,0);steps(w,30);check(count(w,11)==0,"partial lava bed does not activate ritual");
        w=flat();cells=build(w,0,false);w.set(cells[0]%World.W-1,5,cells[0]/World.W%World.D,10);NatureTests.advance(w,10);check(count(w,11)==0,"water cooling the basin cancels the reaction");
        w=flat();w.set(20,5,20,15);w.set(20,6,20,16);w.progress.chapter=0;check(!w.mine(20,6,20)&&w.progress.iron==0,"raw iron requires a stone pick");w.progress.chapter=7;w.progress.iron=1000000;check(!w.mine(20,6,20)&&w.get(20,6,20)==16,"full iron inventory cannot destroy the raw iron block");
        w=flat();cells=build(w,0,false);steps(w,7);w.set(20,5,20,15);w.set(20,6,20,13);steps(w,5);
        byte[] packet=WorldTransfer.pack("Glutschmiede",w);saved=WorldTransfer.unpack(packet).world;check(saved.forge.rituals.size()==1&&saved.forge.ores.size()==1,"QR snapshots preserve both in-progress reactions");
        steps(saved,4);check(count(saved,11)==1,"ritual continues from its transferred progress");steps(saved,25);check(saved.get(20,6,20)==16,"smelting continues from its transferred progress");
        w=flat();w.progress.iron=28;w.progress.inventory[14]=2;w.set(10,4,10,7);
        for(int format=1;format<=4;format++){byte[] before=w.blocks.clone();saved=World.decode(NatureTests.legacy(w,format));check(Arrays.equals(before,saved.blocks),"legacy format "+format+" loads without generating ruins or replacing builds");check(saved.progress.inventory.length==17&&saved.progress.inventory[16]==0,"legacy inventory expands without inventing raw iron");check(Arrays.equals(saved.blocks,World.decode(saved.encode()).blocks),"legacy world roundtrips into format 5");}
        for(int seed=0;seed<12;seed++){w=new World(seed,true);saved=new World(seed,true);check(Arrays.equals(w.blocks,saved.blocks),"ruins are seed deterministic");check(count(w,16)>=1,"new Story world contains a raw-iron clue seed "+seed);check(w.forge.rituals.isEmpty(),"incomplete ruin does not start a ritual");check(!w.collides(w.x,w.y,w.z)&&!w.touchesLava(w.x,w.y,w.z),"ruins preserve safe spawn");}
        // Inspect all lava blocks, not only the marked renewable springs: the old surface pools
        // and the recipe ruin previously escaped the cave-only depth checks.
        for(int seed=0;seed<64;seed++){
            w=new World(seed,true);World terrain=new World(seed,true,false);
            int lava=0,clues=0;boolean buried=true,surfaceSame=true;
            for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)for(int y=1;y<World.H;y++){
                int block=w.get(x,y,z);
                if(y>=12&&block!=terrain.get(x,y,z))surfaceSame=false;
                if(block!=15&&block!=16)continue;
                if(block==15)lava++;else clues++;
                int roof=0;
                for(int above=y+1;above<World.H;above++){
                    int cover=terrain.get(x,above,z);
                    if((cover==3||cover==13||cover==11)&&w.solid(x,above,z))roof++;
                }
                if(y>8||roof<3)buried=false;
            }
            check(lava>0&&clues>0,"new Story worlds retain lava and a recipe clue seed "+seed);
            check(buried,"every lava block and raw-iron clue is buried under stone seed "+seed);
            check(surfaceSame,"caves and recipe ruin leave the entire surface intact seed "+seed);
            World roundtrip=World.decode(w.encode());check(Arrays.equals(w.blocks,roundtrip.blocks),"underground placement survives saving seed "+seed);
        }
        w=new World(11,false);check(count(w,16)==0&&count(w,14)==0,"creative terrain remains unchanged");
        w=flat();w.set(20,5,20,15);w.set(20,6,20,13);steps(w,2);w.forge.ores.put(World.index(20,6,20),30);rejects(w,"invalid completed smelting state rejected");
        w=flat();cells=build(w,0,false);steps(w,2);w.forge.rituals.firstEntry().getValue().age=16;rejects(w,"invalid completed ritual state rejected");
        w=flat();w.set(20,5,20,15);w.set(20,6,20,13);NatureTests.advance(w,14);check(w.get(20,6,20)==13,"real Nature clock enforces smelting duration");NatureTests.advance(w,2);check(w.get(20,6,20)==16,"Nature clock completes smelting during play");
        w=flat();cells=build(w,0,false);byte[] before=w.blocks.clone();w.nature.tick(w,Float.NaN);w.nature.tick(w,-1);check(Arrays.equals(before,w.blocks)&&w.forge.rituals.isEmpty(),"invalid elapsed time cannot manufacture glowstones");
        System.out.println("PASS: "+checks+" hidden forge assertions");
    }
}
