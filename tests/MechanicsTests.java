package de.meiapps.meicraft;
import java.util.*;
import java.io.*;
public final class MechanicsTests {
 static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void rejected(Runnable work){boolean failed=false;try{work.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,"invalid movement rejected");}
 public static void main(String[] args)throws Exception{
  for(int dir=0;dir<6;dir++)for(int distance=1;distance<=4;distance++){
   World w=NatureTests.flat(false);w.x=60;w.z=60;int[] d=Mechanics.DIRECTIONS[dir];Mechanics.Block button=new Mechanics.Block(15,10,15,14);w.set(15,10,15,14);List<Mechanics.Block> blocks=List.of(new Mechanics.Block(20,15,20,7),new Mechanics.Block(20,16,20,8));for(Mechanics.Block b:blocks)w.set(b.x,b.y,b.z,b.type);
   Mechanics.Link link=new Mechanics.Link(button,blocks,dir,distance,false);w.mechanics.install(w,link);byte[] before=w.blocks.clone();int[] inventory=w.progress.inventory.clone();w.mechanics.apply(w,link);
   check(w.get(20+d[0]*distance,15+d[1]*distance,20+d[2]*distance)==7,"first block moves in every direction");check(w.get(20+d[0]*distance,16+d[1]*distance,20+d[2]*distance)==8,"second material retained, including overlapping translations");
   World loaded=World.decode(w.encode());Mechanics.Link saved=loaded.mechanics.links.get(loaded.idx(15,10,15));check(saved!=null&&saved.moved&&saved.encode().equals(link.toggle().encode()),"switch definition and position survive save");loaded.mechanics.apply(loaded,saved);check(Arrays.equals(before,loaded.blocks),"return reconstructs exact original world");check(Arrays.equals(inventory,loaded.progress.inventory),"no inventory creation or loss");
   check(Mechanics.decode(link.encode()).encode().equals(link.encode()),"canonical marker roundtrip");
  }
  World w=NatureTests.flat(false);w.x=60;w.z=60;w.set(10,10,10,14);w.set(20,10,20,7);Mechanics.Link link=new Mechanics.Link(new Mechanics.Block(10,10,10,14),List.of(new Mechanics.Block(20,10,20,7)),0,3,false);w.mechanics.install(w,link);
  w.set(22,10,20,3);byte[] before=w.blocks.clone();rejected(()->w.mechanics.apply(w,link));check(Arrays.equals(before,w.blocks)&&!w.mechanics.links.get(w.idx(10,10,10)).moved,"obstacle along swept path rejects entire operation");w.set(22,10,20,0);w.x=22.5f;w.y=11.6f;w.z=20.5f;rejected(()->w.mechanics.apply(w,link));w.x=60;w.z=60;
  w.mechanics.apply(w,link);w.set(20,10,20,3);before=w.blocks.clone();rejected(()->w.mechanics.apply(w,link.toggle()));check(Arrays.equals(before,w.blocks),"obstructed return also preserves every block");
  rejected(()->new Mechanics.Link(link.button,List.of(link.button),0,1,false));rejected(()->new Mechanics.Link(link.button,link.blocks,0,5,false));rejected(()->new Mechanics.Link(link.button,List.of(new Mechanics.Block(20,10,20,10)),0,1,false));
  w.set(10,10,10,0);check(w.mechanics.links.isEmpty(),"mining a switch removes its local connection");
  for(String bad:List.of("mech_!",link.encode()+"A","mech_"+"a".repeat(101))){boolean failed=false;try{Mechanics.decode(bad);}catch(IOException expected){failed=true;}check(failed,"invalid metadata rejected");}
  System.out.println("PASS: "+checks+" reversible mechanics, collision, inventory and persistence checks");
 }
}
