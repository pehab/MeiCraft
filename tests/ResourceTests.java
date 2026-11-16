package de.meiapps.meicraft;

import java.util.*;

public final class ResourceTests {
    static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static final class Space implements ResourceRetrofit.Space {
        final World w;final Set<Integer> touched=new HashSet<>();boolean player;
        Space(World w){this.w=w;}
        public int get(int x,int y,int z){return w.get(x,y,z);}public boolean touched(int x,int y,int z){return touched.contains(w.idx(x,y,z));}public boolean occupied(int x,int y,int z){return player;}
    }
    static final ResourceClues.Visible VISIBLE=new ResourceClues.Visible(){public boolean ready(int x,int z){return true;}public boolean edited(int x,int y,int z){return false;}};
    public static void main(String[] args)throws Exception{
        int[] totals=new int[3];
        for(long seed:new long[]{31,1234,7654321}){
            World baseline=OnlineTerrain.region(seed,0,2,1);Space s=new Space(OnlineTerrain.region(seed,0,2,1));byte[] before=s.w.blocks.clone();
            for(int site=0;site<ResourceRetrofit.SITES;site++){
                List<ResourceRetrofit.Cell> plan=ResourceRetrofit.plan(baseline,s,0,2,site);if(plan.isEmpty())continue;totals[site/4]++;check(plan.size()<=16,"each whole pocket fits one atomic batch");
                int minX=96,maxX=0,minY=48,maxY=0,minZ=96,maxZ=0;for(ResourceRetrofit.Cell c:plan){minX=Math.min(minX,c.x);maxX=Math.max(maxX,c.x);minY=Math.min(minY,c.y);maxY=Math.max(maxY,c.y);minZ=Math.min(minZ,c.z);maxZ=Math.max(maxZ,c.z);check(c.y>1&&baseline.get(c.x,c.y,c.z)==3,"only original natural stone is replaced");}
                int root=s.w.idx(minX,minY,minZ);s.touched.add(root);check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"edited stone equal to its old type is protected");s.touched.clear();s.w.set(minX,minY,minZ,7);check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"existing buildings protected");s.w.set(minX,minY,minZ,3);
                s.touched.add(s.w.idx(minX-1,minY,minZ));check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"one-cell surrounding protection detects nearby edits");s.touched.clear();s.player=true;check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"players in protected volume block enrichment");s.player=false;
                for(ResourceRetrofit.Cell c:plan){s.w.set(c.x,c.y,c.z,c.type);s.touched.add(s.w.idx(c.x,c.y,c.z));}
                for(int x=minX-1;x<=maxX+1;x++)for(int z=minZ-1;z<=maxZ+1;z++)for(int y=minY-1;y<=maxY+1;y++)if(x<minX||x>maxX||z<minZ||z>maxZ||y<minY||y>maxY)check(s.w.get(x,y,z)==3,"whole stone shell, floor and roof remain intact");
                check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"pocket is never applied twice");
                for(ResourceRetrofit.Cell c:plan)s.w.set(c.x,c.y,c.z,3);check(ResourceRetrofit.plan(baseline,s,0,2,site).isEmpty(),"checkpoint-restored cell nonces prevent refill even if every block looks original");s.touched.clear();
            }
            check(Arrays.equals(before,s.w.blocks),"fixtures restored exactly after safety checks");
            World modern=OnlineTerrain.region(seed,0,2,2);for(int site=0;site<8;site++)check(ResourceRetrofit.plan(modern,new Space(modern),0,2,site).isEmpty(),"native generation-2 ores and caves are not added again");
        }
        for(int n:totals)check(n>0,"all three resource kinds can be found in original stone");
        World w=NatureTests.flat(false);for(int x=0;x<w.width;x++)for(int z=0;z<w.depth;z++)for(int y=2;y<15;y++)w.blocks[w.idx(x,y,z)]=(byte)(y==14?1:3);w.set(20,7,20,17);w.set(24,6,20,20);w.set(28,5,20,15);byte[] before=w.blocks.clone();
        List<ResourceClues.Clue> clues=ResourceClues.collect(w,24.5f,20.5f,VISIBLE);Set<Integer> kinds=new HashSet<>();for(ResourceClues.Clue c:clues){kinds.add(c.type);check(c.y==14&&c.depth>=2,"surface clues report actual depth");check(c.text().contains("tiefer"),"discovery hint explains where to dig");}check(kinds.containsAll(Arrays.asList(17,20,15)),"coal, crystals and lava all have visible clues");check(clues.size()<=9&&Arrays.equals(before,w.blocks),"clues are bounded and never mutate terrain");
        check(ResourceClues.collect(w,24,20,new ResourceClues.Visible(){public boolean ready(int x,int z){return false;}public boolean edited(int x,int y,int z){return false;}}).isEmpty(),"unloaded online regions never produce speculative clues");
        check(ResourceClues.collect(w,24,20,new ResourceClues.Visible(){public boolean ready(int x,int z){return true;}public boolean edited(int x,int y,int z){return true;}}).isEmpty(),"player-built surfaces have no decoration clues");
        w.set(20,7,20,0);w.set(24,6,20,0);w.set(28,5,20,0);check(ResourceClues.collect(w,24,20,VISIBLE).isEmpty(),"depleted deposits lose their clues");
        System.out.println("PASS: "+checks+" retrofit, stone-shell, edit-history, player and resource-clue assertions");
    }
}
