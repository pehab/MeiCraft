package de.meiapps.meicraft;

/** Stable IDs, atlas slots and codec limits. IDs 0..16 retain their original meanings. */
final class Materials {
    static final int LEGACY_COUNT=17,COAL_ORE=17,COAL=18,BASALT=19,CRYSTAL=20,EMBER=21,CRYSTAL_GLASS=22,POLISHED=23,COUNT=24;
    static final int TERRAIN=2,ATLAS_WIDTH=2048;
    static boolean glass(int block){return block==8||block==CRYSTAL_GLASS;}
    static boolean luminous(int block){return block==11||block==15||block==16||block==CRYSTAL||block==EMBER;}
    static int tile(int block){return block>=COAL_ORE?32+block-COAL_ORE:block==16?21:block==13?16:block==14?17:block==15?18:block;}
    static long hash(long seed,int x,int y,int z){long v=seed^x*0x632be59bd9b4e019L^y*0x9e3779b97f4a7c15L^z*0x85157af5d66d3e27L;v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
    static boolean touching(World w,int x,int y,int z,int block){for(int[] d:MaterialReactions.NEIGHBORS)if(w.get(x+d[0],y+d[1],z+d[2])==block)return true;return false;}
    static boolean burning(World w,int x,int y,int z){return w.get(x,y,z)==COAL&&touching(w,x,y,z,15)&&!touching(w,x,y,z,10);}
    private Materials(){}
}
