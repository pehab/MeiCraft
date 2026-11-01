package de.meiapps.meicraft;

import java.util.Random;

/** Independent oracle: transform all 8 box corners into GPU clip coordinates. */
public final class FrustumContractTests {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static boolean reference(float[] m,float x0,float y0,float z0,float x1,float y1,float z1){
        for(int plane=0;plane<6;plane++){
            boolean outside=true;
            for(int corner=0;corner<8;corner++){
                float x=(corner&1)==0?x0:x1,y=(corner&2)==0?y0:y1,z=(corner&4)==0?z0:z1;
                float w=m[3]*x+m[7]*y+m[11]*z+m[15];
                int row=plane/2;float clip=m[row]*x+m[4+row]*y+m[8+row]*z+m[12+row];
                if(w+((plane&1)==0?clip:-clip)>=0){outside=false;break;}
            }
            if(outside)return false;
        }
        return true;
    }
    public static void main(String[] args){
        ViewFrustum f=new ViewFrustum();float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};f.update(identity);
        check(f.intersects(-.5f,-.5f,-.5f,.5f,.5f,.5f),"Interior stays visible");
        check(f.intersects(1,0,0,2,1,1),"Touching clip plane stays visible");
        check(f.intersects(1.0005f,0,0,2,1,1),"Conservative tolerance retains near-plane boxes");
        check(!f.intersects(1.1f,0,0,2,1,1),"Entirely clipped box is rejected");
        int considered=0,drawn=0;
        for(int turn=0;turn<72;turn++){
            float[] m=matrix(144,30,144,turn*(float)Math.PI/36,-.15f);f.update(m);
            for(int z=0;z<18;z++)for(int x=0;x<18;x++){
                boolean ref=reference(m,x*16,0,z*16,x*16+16,World.H,z*16+16),candidate=f.intersects(x*16,0,z*16,x*16+16,World.H,z*16+16);
                check(!ref||candidate,"Frustum never rejects a potentially visible terrain chunk");
                float dx=x*16+8-144,dz=z*16+8-144;
                if(dx*dx+dz*dz<=72*72){considered++;if(candidate)drawn++;}
            }
        }
        check(drawn*100<considered*65,"Conservative culling saves at least 35 percent of terrain draws in the camera sweep");
        Random random=new Random(1000000);
        for(int i=0;i<100000;i++){
            float[] m=matrix(random.nextFloat()*288,2+random.nextFloat()*62,random.nextFloat()*288,random.nextFloat()*6.28f,random.nextFloat()*3-1.5f);f.update(m);
            float x=random.nextFloat()*288,y=random.nextFloat()*48,z=random.nextFloat()*288;
            boolean ref=reference(m,x,y,z,x+16,y+16,z+16),candidate=f.intersects(x,y,z,x+16,y+16,z+16);
            check(!ref||candidate,"Random camera/box retains every reference-visible box");
        }
        System.out.println("PASS: "+checks+" conservative clip contracts; terrain draws "+considered+" -> "+drawn+" across 72 camera headings (meshes still prewarmed)");
    }
    static float[] matrix(float x,float y,float z,float yaw,float pitch){
        // Independently construct a conventional perspective and camera basis.
        float sx=(float)Math.cos(yaw),sz=-(float)Math.sin(yaw),ux=(float)Math.sin(yaw)*(float)Math.sin(pitch),uy=(float)Math.cos(pitch),uz=(float)Math.cos(yaw)*(float)Math.sin(pitch);
        float fx=-(float)Math.sin(yaw)*(float)Math.cos(pitch),fy=(float)Math.sin(pitch),fz=-(float)Math.cos(yaw)*(float)Math.cos(pitch);
        float[] v={sx,ux,-fx,0,0,uy,-fy,0,sz,uz,-fz,0,-sx*x-sz*z,-ux*x-uy*y-uz*z,fx*x+fy*y+fz*z,1};
        float q=1/(float)Math.tan(Math.toRadians(36)),near=.08f,far=70;
        float[] p={q/(16f/9),0,0,0,0,q,0,0,0,0,(far+near)/(near-far),-1,0,0,2*far*near/(near-far),0};
        float[] m=new float[16];for(int c=0;c<4;c++)for(int r=0;r<4;r++)for(int k=0;k<4;k++)m[c*4+r]+=p[k*4+r]*v[c*4+k];return m;
    }
}
