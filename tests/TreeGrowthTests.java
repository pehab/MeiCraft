package de.meiapps.meicraft;
import java.util.*;

public final class TreeGrowthTests {
    static int checks;
    static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static final class Space implements TreeGrowth.Space {
        final World w=NatureTests.flat(false);final Set<String> occupied=new HashSet<>();
        Space(){w.set(20,2,20,1);w.set(20,3,20,6);}
        public int get(int x,int y,int z){return w.get(x,y,z);}
        public boolean occupied(int x,int y,int z){return occupied.contains(x+"_"+y+"_"+z);}
        void apply(List<TreeGrowth.Block> p){for(TreeGrowth.Block b:p)w.set(b.x,b.y,b.z,b.type);}
    }
    public static void main(String[] args)throws Exception{
        Space s=new Space();
        for(int stage=0;stage<3;stage++){List<TreeGrowth.Block> plan=TreeGrowth.stage(20,3,20,stage,s);check(!plan.isEmpty()&&plan.size()<16,"bounded stage "+stage);s.apply(plan);check(s.get(20,3+stage,20)==5,"growing trunk "+stage);}
        check(s.get(21,7,20)==6&&s.get(20,8,20)==6,"complete crown");
        check(TreeGrowth.stage(20,3,20,3,s).isEmpty(),"mature tree stops");
        s=new Space();s.w.set(20,4,20,7);byte[] before=s.w.blocks.clone();check(TreeGrowth.stage(20,3,20,0,s).isEmpty()&&Arrays.equals(before,s.w.blocks),"building blocks entire stage without edits");
        s.w.set(20,4,20,0);s.occupied.add("20_4_20");check(TreeGrowth.stage(20,3,20,0,s).isEmpty(),"player body blocks growth");s.occupied.clear();check(!TreeGrowth.stage(20,3,20,0,s).isEmpty(),"blocked sapling retries");
        check(!TreeGrowth.due(10,10*TreeGrowth.DAY+1234)&&TreeGrowth.due(10,11*TreeGrowth.DAY),"UTC day boundary");check(TreeGrowth.due(10,99*TreeGrowth.DAY),"missed days only produce a due flag");
        s=new Space();s.w.set(20,5,20,6);check(!TreeGrowth.supported(s,20,5,20),"unsupported leaf");s.w.set(22,4,20,5);check(TreeGrowth.supported(s,20,5,20),"nearby wood protects");s.w.set(22,4,20,0);check(!TreeGrowth.supported(s,20,5,20),"trunk mining enables decay");
        World w=NatureTests.flat(false);w.set(20,2,20,1);check(w.place(20,3,20,6),"plant sapling");w.place(30,8,30,6);w.set(40,8,40,6);w.nature.naturalLeaves.set(World.index(40,8,40));
        World restored=World.decode(w.encode());check(restored.nature.placedLeaves.get(World.index(20,3,20))&&restored.nature.placedLeaves.get(World.index(30,8,30)),"planted provenance survives restart");check(restored.nature.naturalLeaves.get(World.index(40,8,40)),"natural crown provenance survives restart");
        NatureTests.advance(restored,120);check(restored.get(30,8,30)==6,"decorative floating leaves survive");check(restored.get(40,8,40)==0,"natural leaf eventually decays");check(restored.get(20,3,20)!=0,"sapling survives or grows");
        String token="a".repeat(32);OnlineChange c=new OnlineChange(1,0,20,3,20,6,false,token,"","child","tree_0_10_1234");check(OnlineChange.decode(c.encode(),"child").value().equals(c.value()),"provenance journal roundtrip");
        List<OnlineChange> batch=new ArrayList<>();for(int i=0;i<13;i++)batch.add(new OnlineChange(i+1,0,20+i,5,20,6,false,String.format("%032x",i+1),"","child","leaf"));
        List<OnlineChange> delta=OnlineChange.reconcile(0,0,13,Map.of(),Map.of(),List.of(),batch);check(delta!=null&&delta.size()==13,"all batch cells reconcile for peer and fresh cache");
        check(OnlineChange.reconcile(0,0,13,Map.of(),Map.of(),List.of(),batch.subList(1,13))==null,"missing event requires authoritative chunk recovery");
        System.out.println("PASS: "+checks+" tree-growth assertions");
    }
}
