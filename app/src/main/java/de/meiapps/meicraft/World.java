package de.meiapps.meicraft;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Finite, deterministic creative world. No Android dependencies. */
public final class World {
    public static final int W=96,H=48,D=96,SEA=12;
    public static final String[] NAMES={"Luft","Gras","Erde","Stein","Sand","Holz","Laub","Ziegel","Glas","Bretter","Wasser","Leuchtstein","Schnee"};
    public final byte[] blocks=new byte[W*H*D];
    public long seed;
    public float x=48.5f,y=27,z=48.5f,yaw=0,pitch=-.15f;
    public int selected=1;
    public boolean flying=true;
    public int revision=1;
    public World(long seed){this.seed=seed;generate();}
    private World(){}
    public static int index(int x,int y,int z){return (y*D+z)*W+x;}
    public int get(int x,int y,int z){return inside(x,y,z)?blocks[index(x,y,z)]&255:0;}
    public static boolean inside(int x,int y,int z){return x>=0&&z>=0&&y>=0&&x<W&&z<D&&y<H;}
    public boolean set(int x,int y,int z,int type){if(!inside(x,y,z)||y==0||type<0||type>=NAMES.length)return false;blocks[index(x,y,z)]=(byte)type;revision++;return true;}
    public boolean solid(int x,int y,int z){int b=get(x,y,z);return b!=0&&b!=10;}
    private double hash(int x,int z){long a=seed+x*374761393L+z*668265263L;a=(a^(a>>>13))*1274126177L;return (a&0xffff)/65535.;}
    private double noise(double x,double z){int a=(int)Math.floor(x),b=(int)Math.floor(z);double u=x-a,v=z-b;u=u*u*(3-2*u);v=v*v*(3-2*v);return (hash(a,b)*(1-u)+hash(a+1,b)*u)*(1-v)+(hash(a,b+1)*(1-u)+hash(a+1,b+1)*u)*v;}
    private void generate(){
        int[][] top=new int[W][D];
        for(int a=0;a<W;a++)for(int c=0;c<D;c++){
            int h=8+(int)(noise(a/23.,c/23.)*13+noise(a/8.,c/8.)*3);top[a][c]=h;
            for(int b=0;b<=Math.max(h,SEA);b++) blocks[index(a,b,c)]=(byte)(b>h?10:b==h?(h<=SEA+1?4:1):b>h-4?2:3);
        }
        Random r=new Random(seed^91238);
        for(int n=0;n<180;n++){int a=3+r.nextInt(W-6),c=3+r.nextInt(D-6),h=top[a][c];if(h<SEA+2||Math.abs(a-48)+Math.abs(c-48)<5)continue;
            for(int b=1;b<5;b++)blocks[index(a,h+b,c)]=5;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=3;dy<=6;dy++)if(Math.abs(dx)+Math.abs(dz)+(dy==6?2:0)<=4&&get(a+dx,h+dy,c+dz)==0)blocks[index(a+dx,h+dy,c+dz)]=6;
        }
        y=top[48][48]+3.5f;
    }
    public boolean collides(float a,float b,float c){
        if(a<.3f||a>W-.3f||c<.3f||c>D-.3f||b<1.62f||b>H+16)return true;
        for(int xx=(int)Math.floor(a-.28f);xx<=Math.floor(a+.28f);xx++)for(int yy=(int)Math.floor(b-1.60f);yy<=Math.floor(b+.15f);yy++)for(int zz=(int)Math.floor(c-.28f);zz<=Math.floor(c+.28f);zz++)if(solid(xx,yy,zz))return true;
        return false;
    }
    public boolean overlapsPlayer(int a,int b,int c){return a+1>x-.28f&&a<x+.28f&&b+1>y-1.6f&&b<y+.15f&&c+1>z-.28f&&c<z+.28f;}
    public int[] ray(float distance){
        double dx=-Math.sin(yaw)*Math.cos(pitch),dy=Math.sin(pitch),dz=-Math.cos(yaw)*Math.cos(pitch);
        int a=(int)Math.floor(x),b=(int)Math.floor(y),c=(int)Math.floor(z),pa=a,pb=b,pc=c;
        int sx=dx>=0?1:-1,sy=dy>=0?1:-1,sz=dz>=0?1:-1;
        double tx=dx==0?1e20:((dx>0?a+1:a)-x)/dx,ty=dy==0?1e20:((dy>0?b+1:b)-y)/dy,tz=dz==0?1e20:((dz>0?c+1:c)-z)/dz;
        double ax=dx==0?1e20:Math.abs(1/dx),ay=dy==0?1e20:Math.abs(1/dy),az=dz==0?1e20:Math.abs(1/dz),t=0;
        while(t<=distance){if(get(a,b,c)!=0)return new int[]{a,b,c,pa,pb,pc};pa=a;pb=b;pc=c;if(tx<=ty&&tx<=tz){a+=sx;t=tx;tx+=ax;}else if(ty<=tz){b+=sy;t=ty;ty+=ay;}else{c+=sz;t=tz;tz+=az;}}
        return null;
    }
    public byte[] encode()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){out.writeInt(0x4d454943);out.writeInt(1);out.writeLong(seed);out.writeFloat(x);out.writeFloat(y);out.writeFloat(z);out.writeFloat(yaw);out.writeFloat(pitch);out.writeInt(selected);out.writeBoolean(flying);out.writeInt(blocks.length);out.write(blocks);}return bytes.toByteArray();
    }
    public static World decode(byte[] bytes)throws IOException{
        World w=new World();try(DataInputStream in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))){
            if(in.readInt()!=0x4d454943||in.readInt()!=1)throw new IOException("Unbekannter Spielstand");w.seed=in.readLong();w.x=in.readFloat();w.y=in.readFloat();w.z=in.readFloat();w.yaw=in.readFloat();w.pitch=in.readFloat();w.selected=in.readInt();w.flying=in.readBoolean();
            if(!Float.isFinite(w.x)||!Float.isFinite(w.y)||!Float.isFinite(w.z)||!Float.isFinite(w.yaw)||!Float.isFinite(w.pitch)||w.x<.3f||w.x>W-.3f||w.z<.3f||w.z>D-.3f||w.y<1.62f||w.y>H+16||w.selected<1||w.selected>=NAMES.length)throw new IOException("Ungültiger Spielstand");
            if(in.readInt()!=w.blocks.length)throw new IOException("Ungültige Weltgröße");in.readFully(w.blocks);for(byte b:w.blocks)if(b<0||b>=NAMES.length)throw new IOException("Ungültiger Block");if(in.read()!=-1)throw new IOException("Zusätzliche Daten");
        }return w;
    }
}
