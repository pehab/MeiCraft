package de.meiapps.meicraft;

/** Applied ONLY to fresh generation version 2. Never overlays a loaded/edited world. */
final class MaterialTerrain {
    static void decorate(World w,int ox,int oz,boolean online){
        for(int z=0;z<w.depth;z++)for(int x=0;x<w.width;x++)for(int y=2;y<World.H-5;y++)if(w.get(x,y,z)==3){
            long h=Materials.hash(w.seed,x+ox,y,z+oz);
            if(Math.floorMod(h,1000)<28)w.blocks[w.idx(x,y,z)]=(byte)Materials.COAL_ORE;
            else if(y<7&&Math.floorMod(h>>>12,1000)<18)w.blocks[w.idx(x,y,z)]=(byte)Materials.BASALT;
        }
        for(int cx=Math.floorDiv(ox-4,64);cx<=Math.floorDiv(ox+w.width+3,64);cx++)for(int cz=Math.floorDiv(oz-4,64);cz<=Math.floorDiv(oz+w.depth+3,64);cz++){
            long h=Materials.hash(w.seed^0x4352595354414cL,cx,0,cz);int gx=cx*64+16+(int)Math.floorMod(h,32),gz=cz*64+16+(int)Math.floorMod(h>>>9,32),cy=4+(int)Math.floorMod(h>>>20,3);
            if(gx<5||gz<5)continue;boolean safe=true;
            for(int dx=-4;dx<=4&&safe;dx++)for(int dz=-4;dz<=4;dz++){
                if(online){if(OnlineTerrain.height(w.seed,gx+dx,gz+dz)<cy+6)safe=false;}
                else {int x=gx+dx-ox,z=gz+dz-oz;if(x<1||z<1||x>=w.width-1||z>=w.depth-1){safe=false;break;}for(int y=cy-2;y<=cy+4;y++){int b=w.get(x,y,z);if(b!=3&&b!=13&&b!=11&&b!=Materials.COAL_ORE&&b!=Materials.BASALT)safe=false;}}
            }
            if(!safe)continue;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(dx*dx+dz*dz<=10){int x=gx+dx-ox,z=gz+dz-oz;if(!w.contains(x,cy,z))continue;
                for(int y=cy;y<=cy+2;y++)w.blocks[w.idx(x,y,z)]=0;
                if(Math.abs(dx)+Math.abs(dz)>=2&&Math.floorMod(Materials.hash(w.seed,gx+dx,cy,gz+dz),3)==0)w.blocks[w.idx(x,cy,z)]=(byte)Materials.CRYSTAL;
            }
        }
    }
    private MaterialTerrain(){}
}
