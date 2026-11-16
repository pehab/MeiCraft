package de.meiapps.meicraft;

import java.util.*;

/** Local clues derived from actual, loaded blocks. No saved markers or speculative deposits. */
final class ResourceClues {
    interface Visible {boolean ready(int x,int z);boolean edited(int x,int y,int z);}
    static final class Clue {final int x,y,z,depth,type;Clue(int x,int y,int z,int target,int type){this.x=x;this.y=y;this.z=z;depth=y-target;this.type=type;}float distance(float px,float pz){float dx=x+.5f-px,dz=z+.5f-pz;return dx*dx+dz*dz;}String text(){return (type==15?"Wärmespur · Lava":type==20?"Kristallsplitter":"Kohlespuren")+" · ca. "+depth+" Blöcke tiefer";}}
    static int kind(int block){return block==17?0:block==20?1:block==15?2:-1;}
    static List<Clue> collect(World w,float px,float pz,Visible visible){
        List<Clue> candidates=new ArrayList<>();
        for(int gz=Math.floorDiv((int)pz-16,8);gz<=Math.floorDiv((int)pz+16,8);gz++)for(int gx=Math.floorDiv((int)px-16,8);gx<=Math.floorDiv((int)px+16,8);gx++){
            int x0=gx*8,z0=gz*8;if(!visible.ready(Math.max(0,x0),Math.max(0,z0)))continue;Clue[] best=new Clue[3];
            for(int x=Math.max(0,x0);x<Math.min(w.width,x0+8);x++)for(int z=Math.max(0,z0);z<Math.min(w.depth,z0+8);z++){
                int surface=-1;for(int y=World.H-2;y>1;y--){int b=w.get(x,y,z);if(b==0||b==5||b==6)continue;if(b==1||b==2||b==3||b==4||b==12||b==19)surface=y;break;}
                if(surface<3||visible.edited(x,surface,z))continue;
                for(int y=surface-2;y>=Math.max(1,surface-32);y--){int type=w.get(x,y,z),k=kind(type);if(k<0||best[k]!=null&&best[k].depth<=surface-y)continue;best[k]=new Clue(x,surface,z,y,type);}
            }
            for(Clue clue:best)if(clue!=null&&clue.distance(px,pz)<=16*16)candidates.add(clue);
        }
        candidates.sort(Comparator.comparingDouble(c->c.distance(px,pz)));List<Clue> result=new ArrayList<>();int[] counts=new int[3];for(Clue c:candidates)if(counts[kind(c.type)]++<3)result.add(c);return result;
    }
    private ResourceClues(){}
}
