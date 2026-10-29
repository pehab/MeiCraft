package de.meiapps.meicraft;

import java.util.Arrays;

public final class OnlineTerrainTests {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void overlap(World a,int ax,int az,World b,int bx,int bz){
        int left=Math.max(ax,bx),right=Math.min(ax+World.W,bx+World.W);
        int north=Math.max(az,bz),south=Math.min(az+World.D,bz+World.D);
        for(int z=north;z<south;z++)for(int x=left;x<right;x++)for(int y=0;y<World.H;y++)
            check(a.get(x-ax,y,z-az)==b.get(x-bx,y,z-bz),"Overlapping loads must match terrain, resources and trees at "+x+","+y+","+z);
    }
    public static void main(String[] args)throws Exception {
        int min=World.H,max=0,snow=0,water=0,wood=0,ore=0,glow=0,seamMax=0;
        for(long seed:new long[]{0,1,7412,20261029,123456789,999999999}) {
            World origin=OnlineTerrain.region(seed,0,3);
            check(origin.online&&origin.story&&!origin.flying&&!origin.nature.flowEnabled&&!origin.survival.enemiesEnabled,"New world keeps finite resources and static walking mode");
            check(!origin.collides(origin.x,origin.y,origin.z),"Starting clearing is safe");
            check(origin.get(48,17,48)==1&&origin.get(48,16,48)==2,"Spawn supplies grass and soil for first tool");
            check(Arrays.equals(origin.blocks,OnlineTerrain.region(seed,0,2).blocks),"World size cannot change overlapping landscape");
            // These sampling windows deliberately cross all old seams and four-way corners.
            for(int edge:new int[]{96,192}) {
                World west=OnlineTerrain.area(seed,edge-World.W,96),east=OnlineTerrain.area(seed,edge,96);
                World straddling=OnlineTerrain.area(seed,edge-3,96);
                overlap(west,edge-World.W,96,straddling,edge-3,96);overlap(east,edge,96,straddling,edge-3,96);
                World north=OnlineTerrain.area(seed,96,edge-World.D),south=OnlineTerrain.area(seed,96,edge);
                World crossing=OnlineTerrain.area(seed,96,edge-3);
                overlap(north,96,edge-World.D,crossing,96,edge-3);overlap(south,96,edge,crossing,96,edge-3);
                for(int n=0;n<288;n++){seamMax=Math.max(seamMax,Math.abs(OnlineTerrain.height(seed,edge,n)-OnlineTerrain.height(seed,edge-1,n)));seamMax=Math.max(seamMax,Math.abs(OnlineTerrain.height(seed,n,edge)-OnlineTerrain.height(seed,n,edge-1)));}
            }
            World corner=OnlineTerrain.region(seed,8,3);OnlineTerrain.region(seed,1,3);
            check(Arrays.equals(corner.blocks,OnlineTerrain.region(seed,8,3).blocks),"Generation order and repeated reconnects cannot change world");
            check(Arrays.equals(OnlineTerrain.region(seed,3,2).blocks,OnlineTerrain.region(seed,4,3).blocks),"Same global region uses same seed at both world sizes");
            for(int z=0;z<288;z++)for(int x=0;x<288;x++){int h=OnlineTerrain.height(seed,x,z);min=Math.min(min,h);max=Math.max(max,h);check(h>=6&&h<=World.H-6,"Terrain leaves vertical room above peaks");}
            int localWood=0;for(byte block:origin.blocks){if(block==5)localWood++;if(block==12)snow++;if(block==10)water++;if(block==5)wood++;if(block==13)ore++;if(block==11)glow++;}
            for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++){check(origin.get(x,0,z)==3,"Continuous solid foundation");check(origin.get(x,World.H-1,z)==0,"No clipped trees at world ceiling");}
            check(localWood>=8,"Every tested starting region supplies enough wood for progression");
        }
        check(max>=38&&min<=10,"Landscapes include high mountains and low water basins");
        check(water>0&&wood>0&&ore>0&&glow>0,"Landscapes contain water, forests, iron and glowstone");
        // Pin the new generation independently of the unchanged offline-format regression tests.
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        for(int id=0;id<9;id++){World tile=OnlineTerrain.region(20261029,id,3);digest.update(tile.blocks);for(byte b:tile.blocks)if(b==12)snow++;}
        check(snow>0,"High mountain areas are snow covered");
        StringBuilder checksum=new StringBuilder();for(byte b:digest.digest())checksum.append(String.format("%02x",b&255));
        check(checksum.toString().equals("e5c32480aa333a78e9084cc076e7d79a6c0970addf352e6bfdc78565e67a0926"),"Protocol 3 terrain must remain identical between releases");
        System.out.println("Protocol 3 terrain SHA-256: "+checksum);
        System.out.println("Terrain range "+min+".."+max+", largest sampled seam step "+seamMax+", snow "+snow);
        System.out.println("PASS: "+checks+" global terrain, overlap, resource and spawn assertions");
    }
}
