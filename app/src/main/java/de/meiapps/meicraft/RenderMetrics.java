package de.meiapps.meicraft;

import java.util.Arrays;
import java.util.Locale;

/** Bounded local CPU/frame timings. No per-frame allocation or automatic uploads. */
final class RenderMetrics {
    private static final int WINDOW=256;
    private final long[] cpu=new long[WINDOW],intervals=new long[WINDOW],meshes=new long[WINDOW];
    private long frames,meshCount,slowFrames,drawnChunks,culledChunks,maxCpu,maxInterval,maxMesh;
    void frame(long cpuNanos,long intervalNanos){frame(cpuNanos,intervalNanos,0,0);}
    synchronized void frame(long cpuNanos,long intervalNanos,int drawn,int culled){
        drawnChunks+=drawn;culledChunks+=culled;maxCpu=Math.max(maxCpu,cpuNanos);maxInterval=Math.max(maxInterval,intervalNanos);
        int i=(int)(frames%WINDOW);cpu[i]=Math.max(0,cpuNanos);intervals[i]=Math.max(0,intervalNanos);frames++;
        if(intervalNanos>50000000)slowFrames++;
    }
    synchronized void mesh(long nanos){maxMesh=Math.max(maxMesh,nanos);meshes[(int)(meshCount%WINDOW)]=Math.max(0,nanos);meshCount++;}
    private static double percentile(long[] values,long count,int p){
        int n=(int)Math.min(count,values.length);if(n==0)return 0;
        long[] sorted=Arrays.copyOf(values,n);Arrays.sort(sorted);return sorted[Math.min(n-1,(int)Math.ceil(n*p/100.0)-1)]/1e6;
    }
    synchronized String report(){
        return String.format(Locale.GERMAN,"Rendering (lokal, letzte bis zu 256 Messungen)\nAktive Frames: %d · Frameabstände über 50 ms: %d\nCPU pro Frame p50/p95/p99: %.2f / %.2f / %.2f ms\nFrameabstand p50/p95/p99: %.2f / %.2f / %.2f ms\nGeometrie inkl. GPU-Upload p50/p95: %.2f / %.2f ms · Aufbauten: %d\nChunk-Zeichnungen: %d · außerhalb des Kamerabilds ausgelassen: %d\nMaxima seit Start (CPU / Frameabstand / Geometrie): %.2f / %.2f / %.2f ms\nCPU-Zeiten messen keine GPU-Ausführungsdauer.\n",frames,slowFrames,percentile(cpu,frames,50),percentile(cpu,frames,95),percentile(cpu,frames,99),percentile(intervals,frames,50),percentile(intervals,frames,95),percentile(intervals,frames,99),percentile(meshes,meshCount,50),percentile(meshes,meshCount,95),meshCount,drawnChunks,culledChunks,maxCpu/1e6,maxInterval/1e6,maxMesh/1e6);
    }
}
