package de.meiapps.meicraft;

import java.util.*;

/** Pure bounded nature plans. Coordinates are local to one region; no partial plans. */
final class TreeGrowth {
    static final long DAY=86400000L;
    interface Space {int get(int x,int y,int z);boolean occupied(int x,int y,int z);}
    static final class Block {final int x,y,z,type;Block(int x,int y,int z,int type){this.x=x;this.y=y;this.z=z;this.type=type;}String key(){return x+"_"+y+"_"+z;}}
    static List<Block> stage(int x,int y,int z,int stage,Space space){
        List<Block> plan=new ArrayList<>();
        if(stage<0||stage>2||x<2||z<2||x>=World.W-2||z>=World.D-2||y<1||y+5>=World.H)return plan;
        int soil=space.get(x,y-1,z);if(soil!=1&&soil!=2)return plan;
        for(int h=0;h<stage;h++)if(space.get(x,y+h,z)!=5)return plan;
        if(space.get(x,y+stage,z)!=6)return plan;
        plan.add(new Block(x,y+stage,z,5));
        if(stage<2)plan.add(new Block(x,y+stage+1,z,6));
        else{
            plan.add(new Block(x,y+3,z,5));
            for(int h=3;h<=4;h++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                if(Math.abs(dx)+Math.abs(dz)<=1&&!(h==3&&dx==0&&dz==0))plan.add(new Block(x+dx,y+h,z+dz,6));
            plan.add(new Block(x,y+5,z,6));
        }
        for(Block b:plan)if((b.x!=x||b.z!=z||b.y!=y+stage)&&space.get(b.x,b.y,b.z)!=0||space.occupied(b.x,b.y,b.z))return Collections.emptyList();
        return plan;
    }
    static boolean supported(Space space,int x,int y,int z){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-4;dy<=4;dy++)
            if(Math.abs(dx)+Math.abs(dz)<=4&&space.get(x+dx,y+dy,z+dz)==5)return true;
        return false;
    }
    static boolean due(long last,long now){return last<Math.floorDiv(now,DAY);}
}
