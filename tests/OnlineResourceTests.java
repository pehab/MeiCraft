package de.meiapps.meicraft;

import java.util.Arrays;

public final class OnlineResourceTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        World tile=OnlineInventory.terrain(123),base=new World(123,false,false);
        check(tile.online&&tile.story&&!tile.flying,"Online starts walking with finite resources");
        check(Arrays.stream(tile.progress.inventory).sum()==0&&tile.progress.tool()==0,"No free blocks or tools");
        int ores=0,glow=0;for(int i=0;i<tile.blocks.length;i++){if(tile.blocks[i]==13)ores++;if(tile.blocks[i]==11)glow++;if(tile.blocks[i]!=base.blocks[i])check(base.blocks[i]==3&&(tile.blocks[i]==13||tile.blocks[i]==11),"Only untouched underground stone receives resources");}
        check(ores>0&&glow>0,"Online terrain supplies iron and glowstone");
        Story p=new Story();check(!OnlineInventory.place(p,2).isEmpty(),"Empty inventory cannot build");
        Story unchanged=OnlineInventory.copy(p);check(!OnlineInventory.harvest(p,5).isEmpty()&&OnlineInventory.same(p,unchanged),"Hand cannot mine wood or mutate inventory on failure");
        for(int n=0;n<8;n++)check(OnlineInventory.harvest(p,1).isEmpty(),"Grass gives soil");
        check(p.inventory[2]==8&&p.claim()&&p.tool()==1&&p.inventory[2]==0,"First tool consumes gathered soil");
        for(int n=0;n<6;n++)check(OnlineInventory.harvest(p,5).isEmpty(),"Wood tool can collect wood");
        for(int n=0;n<8;n++)OnlineInventory.harvest(p,2);
        check(p.claim()&&p.tool()==2,"Wooden pickaxe is earned with resources");
        p.inventory[5]=1;Story isolated=OnlineInventory.copy(p);isolated.craft(0);
        check(p.inventory[5]==1&&p.inventory[9]==0&&isolated.inventory[5]==0&&isolated.inventory[9]==4,"Crafting candidate never mutates committed inventory");
        check(OnlineInventory.place(isolated,9).isEmpty()&&isolated.inventory[9]==3&&isolated.placed==1,"Building consumes one block after confirmation");
        Story full=OnlineInventory.copy(p);full.inventory[2]=1000000;unchanged=OnlineInventory.copy(full);
        check(!OnlineInventory.harvest(full,2).isEmpty()&&OnlineInventory.same(full,unchanged),"Full inventory does not lose mined block");
        Story liquid=new Story();liquid.buckets=1;check(OnlineInventory.harvest(liquid,10).isEmpty()&&liquid.inventory[10]==1&&liquid.buckets==0,"Scoop consumes empty bucket");
        check(OnlineInventory.place(liquid,10).isEmpty()&&liquid.inventory[10]==0&&liquid.buckets==1,"Pour returns empty bucket");
        check(OnlineInventory.valid(p),"Valid inventory accepted");p.inventory[1]=-1;check(!OnlineInventory.valid(p),"Negative inventory rejected");

        World wide=World.onlineSpace(123,3);check(wide.width==288&&wide.depth==288,"One continuous 288 by 288 world");
        OnlineInventory.install(wide,tile,0,3);World neighbor=OnlineInventory.terrain(OnlineWorld.sectorSeed(123,1));OnlineInventory.install(wide,neighbor,1,3);
        check(wide.get(96,8,30)==neighbor.get(0,8,30),"Neighbor data uses continuous coordinates");
        for(int x=93;x<=99;x++)for(int z=45;z<=51;z++)for(int y=32;y<=36;y++)wide.set(x,y,z,0);
        wide.x=95.8f;wide.y=34;wide.z=48.5f;check(!wide.collides(96.2f,34,48.5f),"No artificial wall at old sector boundary");
        wide.set(96,34,48,7);check(wide.collides(96.2f,34,48.5f),"Real blocks still collide across the boundary");
        check(wide.set(270,20,270,9)&&wide.get(270,20,270)==9,"Building coordinates extend beyond the old 96 by 96 area");
        check(!wide.set(288,20,270,9)&&wide.collides(287.9f,34,48.5f),"Actual outer world boundary is retained");
        try{wide.encode();throw new AssertionError("Large online world must not overwrite offline saves");}catch(java.io.IOException expected){checks++;}
        int[] first=OnlineWorld.visibleSectors(48.5f,48.5f,3,82);check(first.length<9&&Arrays.stream(first).noneMatch(n->n==8),"Distant world data is not fetched at the start");
        check(OnlineWorld.sectorAt(95.9f,10,3)==0&&OnlineWorld.sectorAt(96.1f,10,3)==1,"Position changes continuously across streaming boundary");
        check(Arrays.stream(OnlineWorld.visibleSectors(240,240,3,82)).anyMatch(n->n==8),"Moving streams the far corner");
        byte[] staticBlocks=wide.blocks.clone();float time=wide.survival.time;for(int n=0;n<1200;n++){wide.nature.tick(wide,.1f);wide.survival.tick(wide,.1f);}
        check(Arrays.equals(staticBlocks,wide.blocks)&&wide.survival.time==time&&wide.survival.mobs.isEmpty(),"Streaming/resource worlds remain entirely static");
        for(String uid:new String[]{"parent","child","sibling","a-long-uid"})check(OnlineWorld.colorIndex(uid)>=0&&OnlineWorld.colorIndex(uid)<5&&OnlineWorld.color(uid)==OnlineWorld.color(uid),"Stable player clothing color");
        System.out.println("PASS: "+checks+" online resource and continuous-world assertions");
    }
}
