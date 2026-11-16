package de.meiapps.meicraft;

import java.util.*;

/** Fixed, seed-derived pockets. One stone cell of clearance in every direction, including the roof. */
final class ResourceRetrofit {
    static final int SITES=12;
    interface Space extends TreeGrowth.Space {boolean touched(int x,int y,int z);}
    static final class Cell {final int x,y,z,type;Cell(int x,int y,int z,int type){this.x=x;this.y=y;this.z=z;this.type=type;}}
    static int surface(World original,int x,int z){for(int y=World.H-2;y>1;y--){int b=original.get(x,y,z);if(b==0||b==5||b==6)continue;return b==10?-1:y;}return -1;}
    static List<Cell> plan(World original,Space current,int region,int side,int site){
        if(site<0||site>=SITES)throw new IllegalArgumentException("Resource site");int kind=site/4,zone=site%4;if(original.terrainVersion>=2&&kind<2)return Collections.emptyList();
        int sx=kind==0?2:3,sz=2;
        // Select solely from the original baseline: edits never move a pocket elsewhere on a retry.
        // Thus even a restored checkpoint without its secondary cursor cannot refill mined resources.
        for(int attempt=0;attempt<64;attempt++){
            long hash=Materials.hash(original.seed^0x5245534f55524345L,region,site,attempt);int x=zone%2*48+6+(int)Math.floorMod(hash,35),z=zone/2*48+6+(int)Math.floorMod(hash>>>12,35);
            int top=World.H;for(int dx=-1;dx<=sx;dx++)for(int dz=-1;dz<=sz;dz++)top=Math.min(top,surface(original,x+dx,z+dz));
            for(int y=top-6;y>=Math.max(3,top-14);y--){
                boolean natural=true;for(int dx=-1;dx<=sx&&natural;dx++)for(int dz=-1;dz<=sz&&natural;dz++)for(int dy=-1;dy<=2;dy++)if(original.get(x+dx,y+dy,z+dz)!=3){natural=false;break;}
                if(!natural)continue;
                for(int dx=-1;dx<=sx;dx++)for(int dz=-1;dz<=sz;dz++)for(int dy=-1;dy<=2;dy++)if(current.get(x+dx,y+dy,z+dz)!=3||current.touched(x+dx,y+dy,z+dz)||current.occupied(x+dx,y+dy,z+dz))return Collections.emptyList();
                List<Cell> cells=new ArrayList<>();for(int dx=0;dx<sx;dx++)for(int dz=0;dz<sz;dz++)for(int dy=0;dy<2;dy++){
                    int type=kind==0?Materials.COAL_ORE:kind==2?(dy==0?15:0):dy==0&&(dx==0&&dz==0||dx==2&&dz==1)?Materials.CRYSTAL:0;
                    cells.add(new Cell(x+dx,y+dy,z+dz,type));
                }return cells;
            }
        }return Collections.emptyList();
    }
    private ResourceRetrofit(){}
}
