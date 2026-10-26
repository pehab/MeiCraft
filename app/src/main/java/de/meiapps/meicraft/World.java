package de.meiapps.meicraft;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Finite, deterministic creative world. No Android dependencies. */
public final class World {
    public static final int W=96,H=48,D=96,SEA=12;
    public static final String[] NAMES={"Luft","Gras","Erde","Stein","Sand","Holz","Laub","Ziegel","Glas","Bretter","Wasser","Leuchtstein","Schnee","Eisenerz","Eisenblock","Lava"};
    public final byte[] blocks=new byte[W*H*D];
    public long seed;
    public float x=48.5f,y=27,z=48.5f,yaw=0,pitch=-.15f;
    public int selected=1;
    public boolean flying=true,story;
    public final Story progress=new Story();
    public final Survival survival=new Survival();
    public final Nature nature=new Nature();
    public int revision=1;
    public World(long seed){this(seed,false);}
    public World(long seed,boolean story){this(seed,story,true);}
    World(long seed,boolean story,boolean caves){this.seed=seed;this.story=story;flying=!story;selected=story?2:1;generate();survival.spawnX=x;survival.spawnZ=z;if(caves)nature.addCaves(this);}
    private World(){}
    public static int index(int x,int y,int z){return (y*D+z)*W+x;}
    public int get(int x,int y,int z){return inside(x,y,z)?blocks[index(x,y,z)]&255:0;}
    public static boolean inside(int x,int y,int z){return x>=0&&z>=0&&y>=0&&x<W&&z<D&&y<H;}
    public boolean set(int x,int y,int z,int type){if(!inside(x,y,z)||y==0||type<0||type>=NAMES.length)return false;blocks[index(x,y,z)]=(byte)type;nature.changed(x,y,z);revision++;return true;}
    public String miningProblem(int a,int b,int c){int block=get(a,b,c);if(!inside(a,b,c)||b==0)return "Das Fundament bleibt stehen.";if(block==0)return "Hier ist kein Block.";if(story&&Nature.liquid(block))return "Mit leerem Eiseneimer eine Quelle aufnehmen: − antippen.";if(story&&!progress.canMine(block))return "Dafür brauchst du: "+Story.TOOLS[Story.needed(block)];return "";}
    public boolean mine(int a,int b,int c){if(!miningProblem(a,b,c).isEmpty())return false;int block=get(a,b,c),drop=block==1?2:block;if(story&&!progress.add(drop,1))return false;return set(a,b,c,0);}
    public boolean place(int a,int b,int c,int block){if(!inside(a,b,c)||b==0||get(a,b,c)!=0||block<=0||block>=NAMES.length||overlapsPlayer(a,b,c))return false;if(story&&(progress.inventory[block]<=0||Nature.liquid(block)&&progress.buckets>=1000000))return false;if(Nature.liquid(block)&&nature.active.cardinality()>=Nature.MAX_FLOW)return false;if(!set(a,b,c,block))return false;if(Nature.liquid(block))nature.source(a,b,c);if(story){progress.inventory[block]--;if(Nature.liquid(block))progress.buckets++;progress.placed=Math.min(1000000,progress.placed+1);if(block==8)progress.glassPlaced=Math.min(1000000,progress.glassPlaced+1);}return true;}
    public String scoopLava(int a,int b,int c){return scoopLiquid(a,b,c);}
    public String scoopLiquid(int a,int b,int c){
        int block=get(a,b,c);if(!Nature.liquid(block))return "Hier ist keine Flüssigkeit.";
        if(!inside(a,b,c)||b==0)return "Das Fundament bleibt stehen.";
        if(!story){set(a,b,c,0);return "Flüssigkeit entfernt";}
        if(nature.stream(a,b,c))return "Fließende Flüssigkeit: Suche den Quellblock.";
        if(progress.buckets<=0)return "Stelle an der Werkbank einen Eiseneimer her (3 Eisenbarren).";
        if(progress.inventory[block]>=1000000)return "Dein Vorrat ist voll.";
        // Natural water and marked underground springs refill. Placed sources are finite.
        boolean renewable=nature.spring(a,b,c)||block==10&&nature.levels[index(a,b,c)]==0;
        if(!renewable&&!set(a,b,c,0))return "Hier lässt sich keine Flüssigkeit aufnehmen.";
        progress.buckets--;progress.inventory[block]++;return (block==15?"Lavaeimer":"Wassereimer")+" gefüllt · Material auswählen, + zum Ausgießen";
    }
    public boolean touchesLava(float a,float eye,float c){
        for(int xx=(int)Math.floor(a-.28f);xx<=Math.floor(a+.28f);xx++)
         for(int yy=(int)Math.floor(eye-1.6f);yy<=Math.floor(eye+.15f);yy++)
          for(int zz=(int)Math.floor(c-.28f);zz<=Math.floor(c+.28f);zz++)if(get(xx,yy,zz)==15)return true;
        return false;
    }
    public boolean clearLine(float ax,float ay,float az,float bx,float by,float bz){
        float dx=bx-ax,dy=by-ay,dz=bz-az;int steps=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*8));
        for(int i=1;i<steps;i++){float t=(float)i/steps;if(solid((int)Math.floor(ax+dx*t),(int)Math.floor(ay+dy*t),(int)Math.floor(az+dz*t)))return false;}
        return true;
    }
    public boolean solid(int x,int y,int z){int b=get(x,y,z);return b!=0&&b!=10&&b!=15;}
    private double hash(int x,int z){long a=seed+x*374761393L+z*668265263L;a=(a^(a>>>13))*1274126177L;return (a&0xffff)/65535.;}
    private double noise(double x,double z){int a=(int)Math.floor(x),b=(int)Math.floor(z);double u=x-a,v=z-b;u=u*u*(3-2*u);v=v*v*(3-2*v);return (hash(a,b)*(1-u)+hash(a+1,b)*u)*(1-v)+(hash(a,b+1)*(1-u)+hash(a+1,b+1)*u)*v;}
    private void generate(){
        int[][] top=new int[W][D];
        for(int a=0;a<W;a++)for(int c=0;c<D;c++){
            int h=8+(int)(noise(a/23.,c/23.)*13+noise(a/8.,c/8.)*3);top[a][c]=h;
            for(int b=0;b<=Math.max(h,SEA);b++) blocks[index(a,b,c)]=(byte)(b>h?10:b==h?(h<=SEA+1?4:story&&h>=21?12:1):b>h-4?2:3);
        }
        if(story){
            Random ores=new Random(seed^47127);
            for(int a=0;a<W;a++)for(int c=0;c<D;c++)for(int b=2;b<top[a][c]-3;b++)if(get(a,b,c)==3){int roll=ores.nextInt(1000);if(roll<35)blocks[index(a,b,c)]=13;else if(b<9&&roll<40)blocks[index(a,b,c)]=11;}
        }
        if(story){
            // Surface springs in stone basins, away from the initial build area.
            Random lava=new Random(seed^0x1a7a);
            for(int n=0;n<10;n++){
                int a=5+lava.nextInt(W-10),c=5+lava.nextInt(D-10);
                if(Math.abs(a-48)+Math.abs(c-48)<20||top[a][c]<=SEA+1)continue;
                int h=top[a][c];
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                    for(int yy=h+1;yy<=Math.max(h+1,top[a+dx][c+dz]);yy++)blocks[index(a+dx,yy,c+dz)]=0;
                    blocks[index(a+dx,h-1,c+dz)]=3;
                    blocks[index(a+dx,h,c+dz)]=(byte)(Math.abs(dx)<2&&Math.abs(dz)<2?15:3);
                    top[a+dx][c+dz]=h;
                }
            }
        }
        Random r=new Random(seed^91238);
        for(int n=0;n<180;n++){int a=3+r.nextInt(W-6),c=3+r.nextInt(D-6),h=top[a][c];if(h<SEA+2||get(a,h,c)==15||get(a,h,c)==3||Math.abs(a-48)+Math.abs(c-48)<5)continue;
            for(int b=1;b<5;b++)blocks[index(a,h+b,c)]=5;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=3;dy<=6;dy++)if(Math.abs(dx)+Math.abs(dz)+(dy==6?2:0)<=4&&get(a+dx,h+dy,c+dz)==0)blocks[index(a+dx,h+dy,c+dz)]=6;
        }
        y=top[48][48]+3.5f;
        if(story){int best=Integer.MAX_VALUE;float spawnX=x,spawnY=y,spawnZ=z;for(int a=2;a<W-2;a++)for(int c=2;c<D-2;c++){int distance=Math.abs(a-48)+Math.abs(c-48);if(distance>=best||top[a][c]<=SEA+1||get(a,top[a][c],c)!=1)continue;float yy=top[a][c]+3.5f;if(!collides(a+.5f,yy,c+.5f)){best=distance;spawnX=a+.5f;spawnY=yy;spawnZ=c+.5f;}}x=spawnX;y=spawnY;z=spawnZ;}
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
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){out.writeInt(0x4d454943);out.writeInt(4);out.writeLong(seed);out.writeFloat(x);out.writeFloat(y);out.writeFloat(z);out.writeFloat(yaw);out.writeFloat(pitch);out.writeInt(selected);out.writeBoolean(flying);out.writeInt(blocks.length);out.write(blocks);out.writeBoolean(story);out.writeInt(progress.chapter);out.writeInt(progress.iron);out.writeInt(progress.placed);out.writeInt(progress.glassPlaced);out.writeInt(progress.inventory.length);for(int count:progress.inventory)out.writeInt(count);out.writeInt(progress.buckets);out.writeBoolean(progress.sword);survival.write(out);nature.write(out);}return bytes.toByteArray();
    }
    private static boolean validCount(int value){return value>=0&&value<=1000000;}
    public static World decode(byte[] bytes)throws IOException{
        World w=new World();try(DataInputStream in=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))){
            if(in.readInt()!=0x4d454943)throw new IOException("Unbekannter Spielstand");int format=in.readInt();if(format<1||format>4)throw new IOException("Unbekannte Speicher-Version");w.seed=in.readLong();w.x=in.readFloat();w.y=in.readFloat();w.z=in.readFloat();w.yaw=in.readFloat();w.pitch=in.readFloat();w.selected=in.readInt();w.flying=in.readBoolean();
            if(!Float.isFinite(w.x)||!Float.isFinite(w.y)||!Float.isFinite(w.z)||!Float.isFinite(w.yaw)||!Float.isFinite(w.pitch)||w.x<.3f||w.x>W-.3f||w.z<.3f||w.z>D-.3f||w.y<1.62f||w.y>H+16||w.selected<1||w.selected>=NAMES.length)throw new IOException("Ungültiger Spielstand");
            if(in.readInt()!=w.blocks.length)throw new IOException("Ungültige Weltgröße");in.readFully(w.blocks);for(byte b:w.blocks)if(b<0||b>=NAMES.length)throw new IOException("Ungültiger Block");if(format>=2){w.story=in.readBoolean();w.progress.chapter=in.readInt();w.progress.iron=in.readInt();w.progress.placed=in.readInt();w.progress.glassPlaced=in.readInt();if(w.progress.chapter<0||w.progress.chapter>7||!validCount(w.progress.iron)||!validCount(w.progress.placed)||!validCount(w.progress.glassPlaced))throw new IOException("Ungültiger Story-Fortschritt");int count=in.readInt();if(count!=(format==2?15:w.progress.inventory.length))throw new IOException("Ungültiges Inventar");for(int i=0;i<count;i++){w.progress.inventory[i]=in.readInt();if(!validCount(w.progress.inventory[i]))throw new IOException("Ungültiger Vorrat");}if(w.story)w.flying=false;}
            if(format>=3){w.progress.buckets=in.readInt();w.progress.sword=in.readBoolean();if(!validCount(w.progress.buckets))throw new IOException("Ungültige Eimer");w.survival.read(in);}else{w.survival.spawnX=w.x;w.survival.spawnZ=w.z;}
            if(format>=4)w.nature.read(in,w);
            if(in.read()!=-1)throw new IOException("Zusätzliche Daten");
        }return w;
    }
}

