package de.meiapps.meicraft;

/** Protocol 3 terrain: every feature depends on world coordinates, never tile order or size. */
public final class OnlineTerrain {
    private OnlineTerrain() {}

    private static long hash(long seed,int x,int y,int z,int salt) {
        long value=seed ^ x*0x632be59bd9b4e019L ^ y*0x9e3779b97f4a7c15L ^ z*0x85157af5d66d3e27L ^ salt*0x94d049bb133111ebL;
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double unit(long value) {return (value>>>11)*0x1.0p-53;}
    private static double smooth(double low,double high,double value) {
        double t=Math.max(0,Math.min(1,(value-low)/(high-low)));return t*t*(3-2*t);
    }
    private static double noise(long seed,double x,double z,int salt) {
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z);
        double u=smooth(0,1,x-ix),v=smooth(0,1,z-iz);
        double north=unit(hash(seed,ix,0,iz,salt))*(1-u)+unit(hash(seed,ix+1,0,iz,salt))*u;
        double south=unit(hash(seed,ix,0,iz+1,salt))*(1-u)+unit(hash(seed,ix+1,0,iz+1,salt))*u;
        return north*(1-v)+south*v;
    }

    /** Shared elevation field, also evaluated outside a tile for slopes and tree canopies. */
    public static int height(long seed,int x,int z) {
        double continent=noise(seed,x/110.,z/110.,1);
        double hills=noise(seed,x/38.,z/38.,2);
        double ridge=1-Math.abs(2*noise(seed,x/90.,z/90.,3)-1);
        double mountains=smooth(.38,.76,noise(seed,x/150.,z/150.,4));
        double h=14+continent*7+(hills-.5)*5+mountains*(9+ridge*15);
        h+=(noise(seed,x/13.,z/13.,5)-.5)*(1+mountains*3);
        double lake=1-smooth(.23,.38,noise(seed,x/78.,z/78.,6));
        h=h*(1-lake)+8.5*lake;
        // A continuous warped contour creates winding rivers through several tiles.
        double warp=(noise(seed,x/100.,z/100.,7)-.5)*22;
        double river=1-smooth(.022,.065,Math.abs(noise(seed,(x+warp)/68.,(z-warp)/68.,8)-.5));
        h=h*(1-river)+10*river;
        // One global, gentle starting clearing, not a new clearing in every loaded tile.
        double distance=Math.hypot(x-48.5,z-48.5);
        double natural=smooth(10,25,distance);h=17*(1-natural)+h*natural;
        return Math.max(6,Math.min(World.H-6,(int)Math.round(h)));
    }
    private static int surface(long seed,int x,int z,int h) {
        if(h<=World.SEA+1)return 4;
        if(h>=30+(int)(noise(seed,x/24.,z/24.,9)*3))return 12;
        int slope=Math.max(Math.abs(h-height(seed,x+1,z)),Math.abs(h-height(seed,x,z+1)));
        return h>=27||slope>=3?3:1;
    }
    private static boolean tree(long seed,int x,int z,int h) {
        if(h<=World.SEA+1||h>=27||surface(seed,x,z,h)!=1||Math.hypot(x-48.5,z-48.5)<6)return false;
        return Math.abs(h-height(seed,x-1,z))<=1&&Math.abs(h-height(seed,x,z-1))<=1;
    }
    private static void leaf(World tile,int x,int y,int z) {
        if(tile.contains(x,y,z)&&tile.get(x,y,z)==0)tile.blocks[tile.idx(x,y,z)]=6;
    }
    public static World region(long seed,int sector,int side) {
        if((side!=2&&side!=3)||sector<0||sector>=side*side)throw new IllegalArgumentException("Invalid online region");
        return area(seed,sector%side*World.W,sector/side*World.D);
    }
    /** An arbitrary sampling window; useful for verifying overlapping and reordered loads. */
    static World area(long seed,int originX,int originZ) {
        World tile=World.emptyOnlineTile(seed);
        for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++) {
            int gx=originX+x,gz=originZ+z,h=height(seed,gx,gz),top=surface(seed,gx,gz,h);
            for(int y=0;y<=Math.max(h,World.SEA);y++) {
                int block=y>h?10:y==h?top:y>h-4&&top!=3&&top!=12?2:3;
                if(y>=2&&y<h-3&&block==3) {
                    double ore=unit(hash(seed,gx,y,gz,10));
                    if(ore<.035)block=13;else if(y<9&&ore<.04)block=11;
                }
                tile.blocks[tile.idx(x,y,z)]=(byte)block;
            }
        }
        // Include roots outside the sampling window so crowns cross loading seams intact.
        // All trunks are placed before leaves, making overlaps independent of load order.
        for(int pass=0;pass<2;pass++)for(int cx=Math.floorDiv(originX-3,7);cx<=Math.floorDiv(originX+World.W+2,7);cx++)
            for(int cz=Math.floorDiv(originZ-3,7);cz<=Math.floorDiv(originZ+World.D+2,7);cz++) {
                long placement=hash(seed,cx,0,cz,11);
                if(unit(placement)>.62)continue;
                int gx=cx*7+(int)Math.floorMod(placement,5L)+1,gz=cz*7+(int)Math.floorMod(placement>>>8,5L)+1;
                int h=height(seed,gx,gz);if(!tree(seed,gx,gz,h))continue;
                int x=gx-originX,z=gz-originZ;
                if(pass==0){for(int y=h+1;y<=h+4;y++)if(tile.contains(x,y,z))tile.blocks[tile.idx(x,y,z)]=5;}
                else for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=3;dy<=6;dy++)
                    if(Math.abs(dx)+Math.abs(dz)+(dy==6?2:0)<=4)leaf(tile,x+dx,h+dy,z+dz);
            }
        tile.x=48.5f;tile.z=48.5f;tile.y=height(seed,originX+48,originZ+48)+2.62f;
        tile.forge.initialize(tile);return tile;
    }
}
