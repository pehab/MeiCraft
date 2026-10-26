package de.meiapps.meicraft;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class SurvivalTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static World flat(){World w=new World(37,true);Arrays.fill(w.blocks,(byte)0);for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)w.blocks[World.index(x,4,z)]=3;w.x=48.5f;w.y=6.62f;w.z=48.5f;return w;}
    static void advance(World w,float seconds){for(int i=0;i<Math.round(seconds*20);i++)w.survival.tick(w,.05f);}
    static byte[] oldSave(World w,int format)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){out.writeInt(0x4d454943);out.writeInt(format);out.writeLong(w.seed);out.writeFloat(w.x);out.writeFloat(w.y);out.writeFloat(w.z);out.writeFloat(w.yaw);out.writeFloat(w.pitch);out.writeInt(w.selected);out.writeBoolean(w.flying);out.writeInt(w.blocks.length);out.write(w.blocks);if(format==2){out.writeBoolean(w.story);out.writeInt(w.progress.chapter);out.writeInt(w.progress.iron);out.writeInt(w.progress.placed);out.writeInt(w.progress.glassPlaced);out.writeInt(15);for(int i=0;i<15;i++)out.writeInt(w.progress.inventory[i]);}}return bytes.toByteArray();
    }
    static void rejects(World w,String message)throws Exception{boolean bad=false;try{World.decode(w.encode());}catch(IOException e){bad=true;}check(bad,message);}
    public static void main(String[]args)throws Exception{
        World w=flat();Survival s=w.survival;
        check(!s.night()&&s.daylight()==1&&s.health==10,"new world begins in daylight with full health");
        s.time=479.96f;s.tick(w,.05f);check(s.night()&&!s.notice.isEmpty(),"dusk announces night");
        advance(w,16);check(s.mobs.size()>0&&s.mobs.size()<=Survival.MAX_MOBS,"night spawns a bounded number of enemies");
        for(Survival.Mob m:s.mobs){check(Math.hypot(m.x-w.x,m.z-w.z)>7,"spawns are away from the player");check(!w.collides(m.x,m.y,m.z),"spawns have a clear body volume");}
        advance(w,120);check(s.mobs.size()<=Survival.MAX_MOBS,"population remains bounded during a full night encounter");
        s.mobs.clear();s.time=Survival.DAWN-.02f;s.mobs.add(new Survival.Mob(47.5f,6.62f,45.5f));advance(w,3);check(!s.night()&&s.mobs.isEmpty(),"enemies fade at dawn");
        s.time=719.98f;s.tick(w,.05f);check(s.time<1&&s.daylight()==1,"cycle wraps to daylight");
        float clock=s.time;s.tick(w,Float.NaN);check(s.time==clock,"invalid simulation delta ignored");
        World creative=new World(18);creative.survival.time=500;advance(creative,20);check(creative.survival.time==500&&creative.survival.mobs.isEmpty()&&creative.survival.health==10,"creative has no clock or hostile simulation");
        w=flat();s=w.survival;s.time=500;s.mobs.add(new Survival.Mob(48.5f,6.62f,45.5f));
        for(int x=40;x<=56;x++)for(int y=5;y<=8;y++)w.set(x,y,47,3);
        advance(w,6);check(s.health==10&&s.mobs.get(0).z<46.8f,"wall blocks approach and contact damage");
        w.yaw=0;w.pitch=-.2f;check(s.aimed(w)==null&&!s.attack(w),"cannot hit an enemy through a wall");
        for(int x=40;x<=56;x++)for(int y=5;y<=8;y++)w.set(x,y,47,0);
        s.mobs.clear();Survival.Mob target=new Survival.Mob(48.5f,6.62f,46.5f);s.mobs.add(target);check(s.aimed(w)==target&&s.attack(w)&&target.health==4,"aimed hand attack deals damage");
        s.attack(w);check(target.health==4,"attack cooldown prevents repeated same-frame damage");advance(w,.6f);w.progress.sword=true;check(s.attack(w)&&s.mobs.isEmpty(),"crafted sword defeats a weakened enemy");
        s.mobs.add(new Survival.Mob(51.5f,6.62f,48.5f));check(!s.attack(w),"off-axis enemy cannot be hit");
        w=flat();s=w.survival;s.time=500;Survival.Mob burning=new Survival.Mob(48.5f,6.62f,46.5f);s.mobs.add(burning);w.set(48,5,46,15);advance(w,.7f);check(s.mobs.isEmpty(),"lava moat defeats an enemy");
        w.progress.inventory[9]=40;w.set(10,7,10,7);w.set(48,5,48,15);advance(w,8);check(s.health>0&&!w.touchesLava(w.x,w.y,w.z),"lava damage leads to a safe respawn");check(w.progress.inventory[9]==40&&w.get(10,7,10)==7,"respawn preserves inventory and buildings");
        w=flat();s=w.survival;w.set(40,6,40,11);check(Survival.lit(w,40.5f,7.62f,42.5f),"glowstone protects nearby outdoor spawn locations");
        for(int y=5;y<=10;y++)w.set(40,y,41,3);check(!Survival.lit(w,40.5f,7.62f,42.5f),"solid wall blocks a light source's spawn protection");
        s.time=500;s.toggleEnemies();advance(w,20);check(s.mobs.isEmpty()&&!s.enemiesEnabled&&s.night(),"peaceful toggle preserves day/night and disables spawning");
        w.progress.chapter=5;w.progress.iron=5;w.progress.inventory[5]=1;
        check(w.progress.craft(6).contains("hergestellt")&&w.progress.iron==2&&w.progress.buckets==1,"bucket recipe consumes three iron bars");
        check(w.progress.craft(7).contains("hergestellt")&&w.progress.sword&&w.progress.iron==0,"sword recipe consumes iron and wood");
        w.set(20,5,20,15);check(!w.mine(20,5,20)&&w.get(20,5,20)==15,"ordinary mining cannot create lava items");
        check(w.scoopLava(20,5,20).contains("gefüllt")&&w.get(20,5,20)==0&&w.progress.inventory[15]==1&&w.progress.buckets==0,"scooping transfers one source into an empty bucket");
        w.set(21,5,20,15);check(!w.scoopLava(21,5,20).contains("gefüllt")&&w.get(21,5,20)==15,"scooping without a bucket cannot remove a source");
        check(!w.place(48,5,48,15)&&w.progress.inventory[15]==1,"pouring inside player is atomic");
        check(w.place(20,5,20,15)&&w.progress.inventory[15]==0&&w.progress.buckets==1,"pouring consumes lava and returns the empty bucket");
        check(!w.solid(20,5,20)&&w.touchesLava(20.5f,6.62f,20.5f),"lava is traversable and harmful rather than solid");
        check(!w.place(22,5,20,15),"empty lava stock cannot duplicate a source");
        w.progress.inventory[11]=1;w.progress.inventory[3]=4;w.progress.inventory[5]=2;check(w.progress.craft(8).contains("geschmolzen")&&w.progress.inventory[15]==1&&w.progress.buckets==0&&w.progress.inventory[11]==0,"old worlds can obtain lava with finite furnace ingredients");
        w.progress.craft(8);check(w.progress.inventory[15]==1,"failed lava recipe cannot create extra buckets");
        w.progress.buckets=2;s.health=6;s.time=545;World restored=World.decode(w.encode());
        check(restored.survival.time==545&&restored.survival.health==6&&!restored.survival.enemiesEnabled,"clock, health and difficulty survive saving");
        check(restored.progress.sword&&restored.progress.buckets==2&&restored.progress.inventory[15]==1&&restored.get(20,5,20)==15,"equipment and lava terrain survive saving");
        s.toggleEnemies();s.mobs.add(new Survival.Mob(30.5f,6.62f,30.5f));restored=World.decode(w.encode());check(restored.survival.mobs.size()==1&&restored.survival.mobs.get(0).x==30.5f,"enemy positions survive saving");
        w=flat();w.progress.chapter=5;w.progress.inventory[9]=23;w.set(20,8,20,7);for(int format=1;format<=2;format++){restored=World.decode(oldSave(w,format));check(Arrays.equals(restored.blocks,w.blocks),"format "+format+" migration preserves every existing block");check(restored.survival.time==0&&restored.survival.health==10&&restored.progress.buckets==0,"format "+format+" migration initializes survival safely");if(format==2)check(restored.progress.chapter==5&&restored.progress.inventory[9]==23,"old Story inventory and tools migrate");}
        w.survival.time=Float.NaN;rejects(w,"corrupt clock rejected");w.survival.time=0;w.survival.health=-1;rejects(w,"corrupt health rejected");w.survival.health=10;w.progress.buckets=-1;rejects(w,"corrupt buckets rejected");w.progress.buckets=0;for(int i=0;i<7;i++)w.survival.mobs.add(new Survival.Mob(30,6,30));rejects(w,"unbounded enemy save rejected");
        for(int seed=0;seed<10;seed++){World generated=new World(seed,true);check(!generated.touchesLava(generated.x,generated.y,generated.z),"seed "+seed+" has a safe spawn");int lava=0;for(byte b:generated.blocks)if(b==15)lava++;check(lava>0,"seed "+seed+" contains lava sources");}
        System.out.println("PASS: "+checks+" survival assertions");
    }
}
