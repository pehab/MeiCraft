package de.meiapps.meicraft;

/** Frozen 0.8.3 geometry implementation. Deliberately independent from the replacement. */
final class ReferenceMesher {
    static final int[][] N={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final float[][] F={
        {1,0,0,1,1,0,1,1,1,1,0,1}, {0,0,1,0,1,1,0,1,0,0,0,0},
        {0,1,0,0,1,1,1,1,1,1,1,0}, {0,0,1,0,0,0,1,0,0,1,0,1},
        {1,0,1,1,1,1,0,1,1,0,0,1}, {0,0,0,0,1,0,1,1,0,1,0,0}
    };
    static final float[] SHADE={.82f,.66f,1,.49f,.88f,.73f};
    static final int[] ORDER={0,1,2,0,2,3};
    static final class Floats{float[] values=new float[8192];int size;void add(float a,float b,float c,float u,float v,float l){if(size+6>values.length)values=java.util.Arrays.copyOf(values,values.length*2);values[size++]=a;values[size++]=b;values[size++]=c;values[size++]=u;values[size++]=v;values[size++]=l;}}
    static float[] build(World world,int cx,int cz){
        Floats data=new Floats();
        for(int y=0;y<World.H;y++)for(int z=cz*16;z<cz*16+16;z++)for(int x=cx*16;x<cx*16+16;x++){
            int block=world.get(x,y,z);if(block==0)continue;float heat=world.forge.heat(x,y,z);
            for(int face=0;face<6;face++){
                int neighbor=world.get(x+N[face][0],y+N[face][1],z+N[face][2]);
                if(neighbor!=0 && !((neighbor==10||neighbor==15)&&block!=neighbor))continue;
                if(y==0&&face==3)continue;
                int tile=block==16?21:block==13?(heat>0?22:16):block==14?(heat>0?23:17):block==15?18:block;if(block==1)tile=face==2?1:face==3?2:13;if(block==5&&face>=2&&face<=3)tile=14;
                float u0=(tile*32+.5f)/1024f,u1=(tile*32+31.5f)/1024f;
                float shade=block==11||block==15||block==16||heat>0?1.2f:SHADE[face];
                for(int corner:ORDER){float[] f=F[face];float u=(corner==0||corner==1)?u0:u1;float v=(corner==0||corner==3)?31.5f/32:.5f/32;data.add(x+f[corner*3],y+f[corner*3+1],z+f[corner*3+2],u,v,shade);}
            }
        }
        return java.util.Arrays.copyOf(data.values,data.size);
    }
}
