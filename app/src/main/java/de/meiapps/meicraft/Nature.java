package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Slow, bounded world changes. Called only while playing, under the world lock. */
public final class Nature {
    static final int SOURCE=8, MAX_FLOW=4096;
    static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    final byte[] levels=new byte[World.W*World.H*World.D];
    final BitSet springs=new BitSet(levels.length);
    final BitSet active=new BitSet(levels.length);
    public boolean cavesAdded,flowEnabled=true;
    private float clock;
    private int step,grassColumn;
    private long dirtyChunks;

    public static boolean liquid(int block){return block==10||block==15;}
    void changed(int x,int y,int z){
        int i=World.index(x,y,z);levels[i]=0;active.clear(i);springs.clear(i);
        dirty(x,z);
    }
    void dirty(int x,int z){
        for(int cz=Math.max(0,(z-1)/16);cz<=Math.min(5,(z+1)/16);cz++)
            for(int cx=Math.max(0,(x-1)/16);cx<=Math.min(5,(x+1)/16);cx++)dirtyChunks|=1L<<(cz*6+cx);
    }
    public long takeDirtyChunks(){long result=dirtyChunks;dirtyChunks=0;return result;}
    void source(int x,int y,int z){int i=World.index(x,y,z);levels[i]=SOURCE;active.set(i);}
    public boolean spring(int x,int y,int z){return World.inside(x,y,z)&&springs.get(World.index(x,y,z));}
    public boolean stream(int x,int y,int z){return World.inside(x,y,z)&&levels[World.index(x,y,z)]>0&&levels[World.index(x,y,z)]<SOURCE;}

    public void tick(World w,float dt){
        if(w.online||!Float.isFinite(dt)||dt<=0)return;
        w.forge.animation+=Math.min(dt,.1f);
        clock+=Math.min(dt,.1f);if(clock<.5f)return;clock-=.5f;step++;
        react(w);
        if(flowEnabled){flow(w,10);if(step%3==0)flow(w,15);}
        w.forge.step(w);
        // One full pass every 36 seconds. Growth needs open sky and nearby grass.
        grow(w,128);
    }
    private static boolean adjacent(World w,int x,int y,int z,int type){
        if(w.get(x,y+1,z)==type||w.get(x,y-1,z)==type)return true;
        for(int[] d:SIDES)if(w.get(x+d[0],y,z+d[1])==type)return true;
        return false;
    }
    private void react(World w){
        // Melt all snow touching the original lava before cooling it. The meltwater then
        // participates in the normal water/lava phase in this same simulation step.
        BitSet melted=new BitSet(levels.length);
        for(int i=World.W*World.D;i<w.blocks.length;i++)if(w.blocks[i]==12){
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;
            if(adjacent(w,x,y,z,15))melted.set(i);
        }
        for(int i=melted.nextSetBit(0);i>=0;i=melted.nextSetBit(i+1)){
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;
            w.set(x,y,z,10);
            if(active.cardinality()<MAX_FLOW)source(x,y,z);
        }
        // Cooling and glass candidates share a snapshot; cooled lava cannot also heat sand.
        BitSet stone=new BitSet(levels.length),glass=new BitSet(levels.length);
        for(int i=0;i<w.blocks.length;i++){
            int b=w.blocks[i]&255;if(b!=15&&b!=4)continue;
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;if(y==0)continue;
            if(b==15&&adjacent(w,x,y,z,10))stone.set(i);
            if(b==4&&step%6==0&&adjacent(w,x,y,z,15))glass.set(i);
        }
        for(int i=stone.nextSetBit(0);i>=0;i=stone.nextSetBit(i+1))w.set(i%World.W,i/(World.W*World.D),i/World.W%World.D,3);
        for(int i=glass.nextSetBit(0);i>=0;i=glass.nextSetBit(i+1)){
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;
            if(adjacent(w,x,y,z,15))w.set(x,y,z,8);
        }
    }
    private void grow(World w,int columns){
        BitSet grass=new BitSet(levels.length);
        for(int n=0;n<columns;n++){
            int column=grassColumn++%(World.W*World.D),x=column%World.W,z=column/World.W;
            for(int y=World.H-1;y>0;y--){
                int b=w.get(x,y,z);if(b==0)continue;
                if(b==2&&w.get(x,y+1,z)==0){
                    for(int[] d:SIDES)for(int dy=-1;dy<=1;dy++)
                        if(w.get(x+d[0],y+dy,z+d[1])==1&&w.get(x+d[0],y+dy+1,z+d[1])==0)grass.set(World.index(x,y,z));
                }
                break;
            }
        }
        grassColumn%=World.W*World.D;
        for(int i=grass.nextSetBit(0);i>=0;i=grass.nextSetBit(i+1))w.set(i%World.W,i/(World.W*World.D),i/World.W%World.D,1);
    }
    private void flow(World w,int type){
        BitSet candidates=new BitSet(levels.length);
        for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1)){
            if((w.blocks[i]&255)!=type)continue;
            candidates.set(i);int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;
            if(y>1)candidates.set(World.index(x,y-1,z));
            for(int[] d:SIDES)if(World.inside(x+d[0],y,z+d[1]))candidates.set(World.index(x+d[0],y,z+d[1]));
        }
        int[] indices=new int[candidates.cardinality()];byte[] desired=new byte[indices.length];int n=0;
        for(int i=candidates.nextSetBit(0);i>=0;i=candidates.nextSetBit(i+1)){
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D,b=w.get(x,y,z),level=0;
            if(y==0||b!=0&&(b!=type||levels[i]==0))continue;
            if(b==type&&levels[i]==SOURCE)level=SOURCE;
            else{
                if(y<World.H-1&&w.get(x,y+1,z)==type)level=spreadLevel(World.index(x,y+1,z),type);
                for(int[] d:SIDES){int xx=x+d[0],zz=z+d[1];
                    if(!World.inside(xx,y,zz)||w.get(xx,y,zz)!=type||!supported(w,xx,y,zz))continue;
                    level=Math.max(level,spreadLevel(World.index(xx,y,zz),type)-1);
                }
            }
            indices[n]=i;desired[n++]=(byte)Math.max(0,level);
        }
        int population=active.cardinality();
        for(int k=0;k<n;k++){
            int i=indices[k],level=desired[k],old=levels[i];if(level==old)continue;
            if(old==0&&level>0&&population>=MAX_FLOW)continue;
            int x=i%World.W,y=i/(World.W*World.D),z=i/World.W%World.D;
            w.set(x,y,z,level>0?type:0);levels[i]=(byte)level;
            if(level>0)active.set(i);
            if(old==0&&level>0)population++;else if(old>0&&level==0)population--;
        }
    }
    private int spreadLevel(int i,int type){int level=levels[i];return level==SOURCE?(type==10?5:3):level;}
    private boolean supported(World w,int x,int y,int z){return y==1||w.get(x,y-1,z)!=0;}

    /** Add only wholly untouched stone volumes; never regenerate a saved landscape. */
    public int addCaves(World w){
        if(cavesAdded||!w.story)return 0;
        World original=new World(w.seed,true,false);Random random=new Random(w.seed^0x4c415641L);int added=0;
        for(int attempt=0;attempt<80&&added<6;attempt++){
            int cx=7+random.nextInt(World.W-14),cz=7+random.nextInt(World.D-14),floor=3+random.nextInt(3);
            boolean safe=true;
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int y=floor-1;y<=floor+6;y++){
                int x=cx+dx,z=cz+dz,i=World.index(x,y,z),b=w.get(x,y,z);
                if(b!=original.get(x,y,z)||b!=3&&b!=13&&b!=11||w.overlapsPlayer(x,y,z))safe=false;
            }
            if(!safe)continue;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
                if(dx*dx+dz*dz>10)continue;
                w.set(cx+dx,floor-1,cz+dz,3);
                w.set(cx+dx,floor,cz+dz,Math.abs(dx)<=1&&Math.abs(dz)<=1?15:3);
                for(int y=floor+1;y<=floor+3;y++)w.set(cx+dx,y,cz+dz,0);
            }
            springs.set(World.index(cx,floor,cz));added++;
        }
        cavesAdded=true;return added;
    }
    public void write(DataOutputStream out)throws IOException{
        out.writeBoolean(cavesAdded);out.writeBoolean(flowEnabled);out.writeInt(step%6);out.writeInt(grassColumn);
        out.writeInt(active.cardinality());
        for(int i=active.nextSetBit(0);i>=0;i=active.nextSetBit(i+1)){out.writeInt(i);out.writeByte(levels[i]);}
        out.writeInt(springs.cardinality());for(int i=springs.nextSetBit(0);i>=0;i=springs.nextSetBit(i+1))out.writeInt(i);
    }
    public void read(DataInputStream in,World w)throws IOException{
        cavesAdded=in.readBoolean();flowEnabled=in.readBoolean();step=in.readInt();grassColumn=in.readInt();
        if(step<0||step>=6||grassColumn<0||grassColumn>=World.W*World.D)throw new IOException("Ungültige Natur-Zeit");
        int count=in.readInt();if(count<0||count>MAX_FLOW)throw new IOException("Zu viele Flüssigkeitsblöcke");
        for(int n=0;n<count;n++){int i=in.readInt(),level=in.readUnsignedByte();
            if(i<World.W*World.D||i>=levels.length||active.get(i)||!liquid(w.blocks[i]&255)||level<1||level>SOURCE)throw new IOException("Ungültiger Flüssigkeitsstand");
            levels[i]=(byte)level;active.set(i);
        }
        count=in.readInt();if(count<0||count>6)throw new IOException("Ungültige Lavaquellen");
        for(int n=0;n<count;n++){int i=in.readInt();if(i<World.W*World.D||i>=levels.length||w.blocks[i]!=15||springs.get(i)||active.get(i))throw new IOException("Ungültige Lavaquelle");springs.set(i);}
    }
}
