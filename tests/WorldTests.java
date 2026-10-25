package de.meiapps.meicraft;
import java.util.*;
import java.io.*;
public final class WorldTests {
 static int count;static void check(boolean b,String m){count++;if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception{
  World a=new World(20261025),b=new World(20261025),c=new World(999);
  check(Arrays.equals(a.blocks,b.blocks),"same seed yields identical world");check(!Arrays.equals(a.blocks,c.blocks),"different seeds yield different worlds");
  check(!a.collides(a.x,a.y,a.z),"safe spawn");
  int[] counts=new int[13];for(byte block:a.blocks)counts[block]++;
  for(int t=1;t<=6;t++)check(counts[t]>0,"generated terrain/tree type "+t);check(counts[10]>0,"water generated");
  check(!a.set(0,0,0,0),"indestructible floor");check(!a.set(-1,2,0,1),"negative edit rejected");check(!a.set(96,2,0,1),"far edit rejected");check(!a.set(1,2,0,13),"invalid block rejected");
  Arrays.fill(a.blocks,(byte)0);a.x=10.5f;a.y=20.5f;a.z=10.5f;a.yaw=0;a.pitch=0;
  a.set(10,20,7,3);int[] hit=a.ray(6);check(Arrays.equals(hit,new int[]{10,20,7,10,20,8}),"ray -z and previous empty neighbor");
  check(a.ray(2)==null,"ray distance limit");a.yaw=(float)-Math.PI/2;a.set(13,20,10,1);hit=a.ray(6);check(hit[0]==13&&hit[3]==12,"ray +x");
  a.yaw=0;a.pitch=(float)Math.PI/2;a.set(10,23,10,3);hit=a.ray(6);check(hit[1]==23&&hit[4]==22,"vertical ray");
  check(a.overlapsPlayer(10,19,10),"prevent placing inside player feet");check(!a.overlapsPlayer(12,19,10),"allow placement away from player");
  a.set(10,19,10,3);check(a.collides(10.5f,20.5f,10.5f),"body collision");a.set(10,19,10,10);check(!a.collides(10.5f,20.5f,10.5f),"water traversable");
  check(a.collides(.1f,20,10),"world boundary collision");
  a.selected=11;a.flying=false;a.pitch=-.8f;byte[] save=a.encode();World restored=World.decode(save);
  check(Arrays.equals(restored.blocks,a.blocks),"all blocks persist");check(restored.x==a.x&&restored.y==a.y&&restored.z==a.z&&restored.pitch==a.pitch,"camera persists");check(restored.selected==11&&!restored.flying,"material/movement persists");
  boolean rejected=false;try{World.decode(Arrays.copyOf(save,save.length-9));}catch(IOException e){rejected=true;}check(rejected,"truncated save rejected");
  a.x=Float.NaN;rejected=false;try{World.decode(a.encode());}catch(IOException e){rejected=true;}check(rejected,"NaN player rejected");a.x=10;
  a.blocks[20]=(byte)99;rejected=false;try{World.decode(a.encode());}catch(IOException e){rejected=true;}check(rejected,"invalid block save rejected");
  for(int seed=0;seed<30;seed++){World w=new World(seed);check(!w.collides(w.x,w.y,w.z),"safe spawn seed "+seed);}
  System.out.println("PASS: "+count+" world assertions");
 }
}
