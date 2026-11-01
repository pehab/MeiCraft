package de.meiapps.meicraft;

import java.util.Arrays;
import java.util.Random;

/** Executable compatibility contract: identical ordered vertices, UVs and lighting. */
public final class PerformanceContractTests {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static void equal(World world,int cx,int cz,VoxelMesher mesher){
        float[] expected=ReferenceMesher.build(world,cx,cz);mesher.build(world,cx,cz);
        check(expected.length==mesher.size/4*6,"Expanded indexed vertex count at "+cx+","+cz);
        for(int i=0;i<expected.length;i++)if(Float.floatToIntBits(expected[i])!=Float.floatToIntBits(mesher.values[(i/36)*24+ReferenceMesher.ORDER[i/6%6]*6+i%6]))throw new AssertionError("Vertex/UV/light mismatch at float "+i+" chunk "+cx+","+cz);
        checks++;
        float[] scratch=mesher.values;int growths=mesher.growths;
        mesher.build(world,cx,cz);
        check(scratch==mesher.values&&growths==mesher.growths,"Warm repeated builds allocate no new geometry array");
    }
    public static void main(String[] args){
        VoxelMesher mesher=new VoxelMesher();
        World local=new World(7412,true);
        for(int z=0;z<6;z++)for(int x=0;x<6;x++)equal(local,x,z,mesher);
        World wide=World.onlineSpace(7412,3);
        for(int id:new int[]{0,1,3,4,8})OnlineInventory.install(wide,OnlineTerrain.region(7412,id,3),id,3);
        for(int[] c:new int[][]{{0,0},{5,2},{6,2},{5,5},{6,6},{11,11},{12,12},{17,17}})equal(wide,c[0],c[1],mesher);
        World mixed=World.emptyOnlineTile(0);Random random=new Random(1000);
        for(int y=0;y<World.H;y++)for(int z=0;z<17;z++)for(int x=0;x<17;x++)mixed.blocks[mixed.idx(x,y,z)]=(byte)random.nextInt(World.NAMES.length);
        // Heat on every material, not only ore: the hidden ritual also heats stone/glass.
        for(int y=1;y<World.H;y+=4)for(int z=0;z<17;z+=3)for(int x=0;x<17;x+=3)mixed.forge.glow.put(World.index(x,y,z),.5f);
        equal(mixed,0,0,mesher);equal(mixed,1,0,mesher);equal(mixed,0,1,mesher);
        benchmark(mixed,0,0,"mixed-materials-with-heat");
        Arrays.fill(mixed.blocks,(byte)3);equal(mixed,2,2,mesher);
        check(mesher.heatLookups==256,"Solid interior consults heat only for the visible top, not 12288 blocks");
        check(mesher.neighborChecks==16L*16*(World.H*6-1),"Foundation underside skips even the neighbor check");
        short[] indices=TerrainIndices.pattern();
        check(indices.length==TerrainIndices.MAX_FACES*6,"Shared index pattern has exactly two triangles per quad");
        for(int face=0;face<TerrainIndices.MAX_FACES;face++)for(int corner=0;corner<6;corner++)check(Short.toUnsignedInt(indices[face*6+corner])==face*4+ReferenceMesher.ORDER[corner],"Unsigned indices preserve the original triangle ordering without restart values");
        for(int y=0;y<World.H;y++)for(int z=0;z<World.D;z++)for(int x=0;x<World.W;x++)mixed.blocks[mixed.idx(x,y,z)]=(byte)(((x+y+z)&1)==0?10:15);
        equal(mixed,2,2,mesher);
        int vertices=mesher.size/6,covered=0,batches=0;
        for(int first=0;first<vertices;first+=TerrainIndices.MAX_VERTICES){int count=Math.min(TerrainIndices.MAX_VERTICES,vertices-first);check(count%4==0&&count<=65532,"Every dense-world draw uses complete quads and valid unsigned-short indices");covered+=count;batches++;}
        check(covered==vertices&&batches>1,"Pathological all-liquid boundaries are covered fully over multiple 16-bit batches");
        benchmark(mixed,2,2,"dense-liquid-boundaries");
        Arrays.fill(mixed.blocks,(byte)0);equal(mixed,0,0,mesher);check(mesher.size==0,"Empty chunk clears the previous vertex count");
        RenderMetrics metrics=new RenderMetrics();
        check(metrics.report().contains("Aktive Frames: 0"),"Empty metrics do not invent samples");
        for(int i=0;i<512;i++){metrics.frame(1000000,16000000);metrics.mesh(2000000);}
        metrics.frame(3000000,60000000);
        check(metrics.report().contains("Aktive Frames: 513 · Frameabstände über 50 ms: 1"),"Bounded timing window preserves session counters");
        check(metrics.report().contains("1,00 / 1,00 ms"),"Single outlier does not replace the 95th percentile");
        System.out.println("PASS: "+checks+" exact geometry, boundary, heat and allocation contracts");
        benchmark(local,2,2,"terrain");benchmark(wide,6,6,"online-boundary");benchmark(mixed,0,0,"empty");
    }
    static volatile int sink;
    static void benchmark(World world,int cx,int cz,String name){
        VoxelMesher mesher=new VoxelMesher();
        for(int i=0;i<40;i++){sink=ReferenceMesher.build(world,cx,cz).length;mesher.build(world,cx,cz);}
        long[] old=new long[101],next=new long[101];
        for(int i=0;i<old.length;i++){
            // Alternate order to reduce systematic JIT/GC bias. Times are evidence,
            // not a flaky wall-clock CI threshold; tablet acceptance is separate.
            if((i&1)==0){old[i]=reference(world,cx,cz);next[i]=candidate(world,cx,cz,mesher);}
            else{next[i]=candidate(world,cx,cz,mesher);old[i]=reference(world,cx,cz);}
        }
        Arrays.sort(old);Arrays.sort(next);
        System.out.printf(java.util.Locale.ROOT,"MESH BENCH %s: reference p50/p95 %.3f/%.3f ms, candidate %.3f/%.3f ms; floats %d; scratch growths %d%n",name,old[50]/1e6,old[95]/1e6,next[50]/1e6,next[95]/1e6,mesher.size,mesher.growths);
    }
    static long reference(World world,int x,int z){long t=System.nanoTime();sink=ReferenceMesher.build(world,x,z).length;return System.nanoTime()-t;}
    static long candidate(World world,int x,int z,VoxelMesher mesher){long t=System.nanoTime();mesher.build(world,x,z);sink=mesher.size;return System.nanoTime()-t;}
}
