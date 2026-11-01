package de.meiapps.meicraft;

import java.util.Arrays;

/** Pure geometry kernel. The caller owns the world lock and reuses this scratch buffer.
 * Indexed expansion is the exact ordered vertex stream of the original renderer, not a new LOD.
 */
final class VoxelMesher {
    static final int[][] N={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final float[][] F={
        {1,0,0,1,1,0,1,1,1,1,0,1}, {0,0,1,0,1,1,0,1,0,0,0,0},
        {0,1,0,0,1,1,1,1,1,1,1,0}, {0,0,1,0,0,0,1,0,0,1,0,1},
        {1,0,1,1,1,1,0,1,1,0,0,1}, {0,0,0,0,1,0,1,1,0,1,0,0}
    };
    static final float[] SHADE={.82f,.66f,1,.49f,.88f,.73f};
    static final int[] ORDER={0,1,2,0,2,3};
    float[] values=new float[8192];
    int size,growths;
    long visited,neighborChecks,heatLookups;
    private void add(float a,float b,float c,float u,float v,float l){
        if(size+6>values.length){values=Arrays.copyOf(values,values.length*2);growths++;}
        values[size++]=a;values[size++]=b;values[size++]=c;values[size++]=u;values[size++]=v;values[size++]=l;
    }
    void build(World world,int cx,int cz){
        if(cx<0||cz<0||(cx+1)*16>world.width||(cz+1)*16>world.depth)throw new IllegalArgumentException("Invalid mesh chunk");
        size=0;visited=0;neighborChecks=0;heatLookups=0;
        final byte[] blocks=world.blocks;final int width=world.width,depth=world.depth,layer=width*depth;
        final int x0=cx*16,z0=cz*16;
        for(int y=0;y<World.H;y++)for(int z=z0;z<z0+16;z++){
            int index=(y*depth+z)*width+x0;
            for(int x=x0;x<x0+16;x++,index++){
                visited++;int block=blocks[index]&255;if(block==0)continue;
                int exposed=0;
                for(int face=0;face<6;face++){
                    if(y==0&&face==3)continue;
                    neighborChecks++;
                    int neighbor;
                    switch(face){
                        case 0:neighbor=x+1<width?blocks[index+1]&255:0;break;
                        case 1:neighbor=x>0?blocks[index-1]&255:0;break;
                        case 2:neighbor=y+1<World.H?blocks[index+layer]&255:0;break;
                        case 3:neighbor=blocks[index-layer]&255;break;
                        case 4:neighbor=z+1<depth?blocks[index+width]&255:0;break;
                        default:neighbor=z>0?blocks[index-width]&255:0;
                    }
                    if(neighbor==0||((neighbor==10||neighbor==15)&&block!=neighbor))exposed|=1<<face;
                }
                if(exposed==0)continue;
                // Buried blocks have no visible heat or UVs, so do not consult Forge.
                heatLookups++;float heat=world.forge.heat(x,y,z);
                for(int face=0;face<6;face++)if((exposed&(1<<face))!=0){
                    int tile=block==16?21:block==13?(heat>0?22:16):block==14?(heat>0?23:17):block==15?18:block;if(block==1)tile=face==2?1:face==3?2:13;if(block==5&&face>=2&&face<=3)tile=14;
                    float u0=(tile*32+.5f)/1024f,u1=(tile*32+31.5f)/1024f;
                    float shade=block==11||block==15||block==16||heat>0?1.2f:SHADE[face];
                    for(int corner=0;corner<4;corner++){float[] f=F[face];float u=(corner==0||corner==1)?u0:u1;float v=(corner==0||corner==3)?31.5f/32:.5f/32;add(x+f[corner*3],y+f[corner*3+1],z+f[corner*3+2],u,v,shade);}
                }
            }
        }
    }
}
