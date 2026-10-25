package de.meiapps.meicraft;
import java.util.*;
public final class StoryTests {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static World flat(){World w=new World(31,true);Arrays.fill(w.blocks,(byte)0);w.x=10.5f;w.y=20.5f;w.z=10.5f;return w;}
 static void collect(World w,int type,int count){for(int i=0;i<count;i++){w.set(20,20,20,type);check(w.mine(20,20,20),"collect unlocked material "+type);}}
 public static void main(String[]args)throws Exception{
  World w=flat();Story s=w.progress;check(w.story&&!w.flying&&s.tool()==0,"story starts walking with bare hands");check(!w.place(20,20,20,2),"building needs owned material");w.set(20,20,20,5);check(!w.mine(20,20,20),"wood locked initially");check(w.get(20,20,20)==5&&s.inventory[5]==0,"failed mining is atomic");
  collect(w,1,8);check(s.inventory[2]==8&&s.inventory[1]==0,"grass drops dirt");check(s.ready()&&s.claim()&&s.tool()==1,"dirt trade grants first tool");check(s.inventory[2]==0,"trade consumes dirt");check(!s.claim(),"cannot claim next chapter without its resources");
  collect(w,2,8);collect(w,5,6);check(s.claim()&&s.tool()==2,"workbench and wooden pick unlocked");w.set(20,20,20,13);check(!w.mine(20,20,20),"iron ore needs stone pick");
  collect(w,3,12);collect(w,5,4);check(s.claim()&&s.tool()==3,"stone tool grants ore access");collect(w,13,4);check(s.craft(3).contains("Ofen"),"cannot smelt before furnace");
  collect(w,3,8);collect(w,5,4);check(s.claim()&&s.furnace(),"furnace unlocked");collect(w,5,8);for(int i=0;i<4;i++)check(s.craft(3).contains("hergestellt"),"smelt iron");check(s.iron==4&&s.inventory[13]==0,"smelting consumes ore and fuel");check(s.claim()&&s.tool()==4,"iron pick crafted with bars and wood");collect(w,11,4);check(s.inventory[11]==4,"hard materials unlocked");
  collect(w,2,1);check(w.place(20,20,21,2)&&s.inventory[2]==0,"placing consumes one block");check(!w.place(20,20,22,2)&&s.inventory[2]==0,"cannot place from empty stack");collect(w,2,1);check(!w.place(10,19,10,2)&&s.inventory[2]==1,"cannot place inside player, no item loss");
  s.inventory[5]=1;check(s.craft(0).contains("hergestellt")&&s.inventory[9]==4&&s.inventory[5]==0,"wood becomes planks");int before=s.inventory[9];s.craft(0);check(s.inventory[9]==before,"failed recipe does not create items");
  s.inventory[4]=2;s.inventory[5]=1;s.craft(2);check(s.inventory[8]==2&&s.inventory[4]==0&&s.inventory[5]==0,"sand and fuel become glass");check(w.place(21,20,21,8)&&w.place(22,20,21,8),"glass can be placed");s.placed=30;check(s.claim()&&s.chapter==6,"building chapter uses placed blocks and windows");check(s.claim()&&s.chapter==7&&s.inventory[11]==4,"final collection keeps earned glowing blocks");check(!s.claim(),"completed story cannot be reclaimed");
  s.iron=9;check(s.craft(4).contains("hergestellt")&&s.inventory[14]==1&&s.iron==0,"iron block recipe");
  World restored=World.decode(w.encode());check(restored.story&&!restored.flying&&restored.progress.chapter==7&&restored.progress.inventory[14]==1,"story, tools and inventory survive reload");check(restored.progress.placed==s.placed&&restored.progress.glassPlaced==2,"building progress survives reload");
  World creative=new World(31);creative.set(20,30,20,11);check(creative.mine(20,30,20)&&creative.place(20,30,20,11),"creative retains unlimited instant mining and building");check(creative.progress.inventory[11]==0,"creative does not consume story resources");
  int ore=0,glow=0;World generated=new World(20261026,true);for(byte block:generated.blocks){if(block==13)ore++;if(block==11)glow++;}check(ore>100&&glow>10,"generated story world has ores and rare resources");
  for(int seed=0;seed<12;seed++){World spawn=new World(seed,true);check(!spawn.collides(spawn.x,spawn.y,spawn.z)&&spawn.y>World.SEA+3,"dry safe story spawn "+seed);}
  w.progress.inventory[2]=-1;boolean rejected=false;try{World.decode(w.encode());}catch(java.io.IOException e){rejected=true;}check(rejected,"invalid inventory rejected");
  System.out.println("PASS: "+checks+" story assertions");
 }
}
