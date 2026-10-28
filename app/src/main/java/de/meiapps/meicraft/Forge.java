package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Hidden, in-world lava reactions. Half-second steps are supplied by Nature, only while playing. */
public final class Forge {
    static final int LAYER=World.W*World.D, SMELT_STEPS=30,RITUAL_STEPS=16,MAX_SMELTS=256,MAX_RITUALS=64;
    static final int[][] PATTERN={{0,0,3},{1,0,8},{0,1,14},{1,1,14},{0,2,8},{1,2,3}};
    final BitSet metals=new BitSet(World.W*World.H*World.D);
    final TreeMap<Integer,Integer> ores=new TreeMap<>();
    final TreeMap<Integer,Ritual> rituals=new TreeMap<>();
    final Map<Integer,Float> glow=new HashMap<>();
    public float animation;
    public String notice="";
    static final class Ritual {final int[] cells;int age;Ritual(int[] cells,int age){this.cells=cells;this.age=age;}}
    void initialize(World w){metals.clear();for(int i=0;i<w.blocks.length;i++)if(w.blocks[i]==13||w.blocks[i]==14)metals.set(i);}
    void changed(World w,int index){
        if(w.blocks[index]==13||w.blocks[index]==14)metals.set(index);else metals.clear(index);
        if(ores.isEmpty()&&rituals.isEmpty())return;
        boolean reset=ores.remove(index)!=null;reset|=ores.remove(index+LAYER)!=null;
        for(Iterator<Ritual> it=rituals.values().iterator();it.hasNext();){Ritual r=it.next();boolean affected=false;
            for(int cell:r.cells)if(cell==index||cell-LAYER==index){affected=true;break;}
            if(affected){for(int cell:r.cells)w.nature.dirty(cell%World.W,cell/World.W%World.D);it.remove();reset=true;}
        }
        if(reset)refreshGlow();
    }
    private static boolean lava(World w,int cell){return cell>=LAYER&&w.blocks[cell-LAYER]==15&&!w.nature.stream(cell%World.W,cell/LAYER-1,cell/World.W%World.D);}
    public float heat(int x,int y,int z){return !glow.isEmpty()&&World.inside(x,y,z)?glow.getOrDefault(World.index(x,y,z),0f):0;}
    private void refreshGlow(){glow.clear();for(Map.Entry<Integer,Integer> e:ores.entrySet())glow.put(e.getKey(),(float)e.getValue()/SMELT_STEPS);for(Ritual r:rituals.values())for(int i:r.cells)glow.put(i,(float)r.age/RITUAL_STEPS);}
    static int[] pattern(int x,int y,int z,int rotation,boolean mirror){
        int[] cells=new int[6];int n=0;
        for(int[] p:PATTERN){int dx=mirror?-p[0]:p[0],dz=p[1];for(int turn=0;turn<rotation;turn++){int old=dx;dx=-dz;dz=old;}
            if(!World.inside(x+dx,y,z+dz)||y<2)return null;cells[n++]=World.index(x+dx,y,z+dz);}
        return cells;
    }
    static boolean matches(World w,int[] cells){if(cells==null||cells.length!=6)return false;for(int n=0;n<6;n++)if((w.blocks[cells[n]]&255)!=PATTERN[n][2]||!lava(w,cells[n]))return false;return true;}
    void step(World w){
        TreeMap<Integer,Integer> nextOres=new TreeMap<>();TreeMap<Integer,Ritual> nextRituals=new TreeMap<>();List<Integer> smelted=new ArrayList<>();List<Ritual> completed=new ArrayList<>();
        for(int i=metals.nextSetBit(0);i>=0;i=metals.nextSetBit(i+1)){
            int block=w.blocks[i]&255;if(block==13){if(lava(w,i)&&nextOres.size()<MAX_SMELTS){int age=ores.getOrDefault(i,0)+1;if(age>=SMELT_STEPS)smelted.add(i);else nextOres.put(i,age);w.nature.dirty(i%World.W,i/World.W%World.D);}continue;}
            if(block!=14)continue;
            int x=i%World.W,y=i/LAYER,z=i/World.W%World.D;
            for(int rotation=0;rotation<4;rotation++)for(boolean mirror:new boolean[]{false,true}){
                // PATTERN[2] is the first iron block at local (0,1).
                int dx=0,dz=1;for(int turn=0;turn<rotation;turn++){int old=dx;dx=-dz;dz=old;}
                int[] cells=pattern(x-dx,y,z-dz,rotation,mirror);if(!matches(w,cells))continue;
                int key=Arrays.stream(cells).min().getAsInt();if(nextRituals.containsKey(key)||nextRituals.size()>=MAX_RITUALS)continue;
                Ritual old=rituals.get(key);int age=old!=null&&Arrays.equals(cells,old.cells)?old.age+1:1;
                Ritual r=new Ritual(cells,age);nextRituals.put(key,r);if(age>=RITUAL_STEPS)completed.add(r);
                for(int cell:cells)w.nature.dirty(cell%World.W,cell/World.W%World.D);
            }
        }
        ores.clear();ores.putAll(nextOres);rituals.clear();rituals.putAll(nextRituals);
        for(int i:smelted)if(w.blocks[i]==13&&lava(w,i)){w.set(i%World.W,i/LAYER,i/World.W%World.D,16);notice="Das Erz ist zu Roheisen erstarrt.";}
        for(Ritual r:completed)if(matches(w,r.cells)){
            int output=Math.min(r.cells[2],r.cells[3]);
            for(int cell:r.cells)w.set(cell%World.W,cell/LAYER,cell/World.W%World.D,0);
            w.set(output%World.W,output/LAYER,output/World.W%World.D,11);notice="Ein Leuchtstein entsteht aus der Glut.";
        }
        refreshGlow();
    }
    public void write(DataOutputStream out)throws IOException{
        out.writeInt(ores.size());for(Map.Entry<Integer,Integer> e:ores.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue());}
        out.writeInt(rituals.size());for(Ritual r:rituals.values()){out.writeInt(r.age);for(int cell:r.cells)out.writeInt(cell);}
    }
    public void read(DataInputStream in,World w)throws IOException{
        int count=in.readInt();if(count<0||count>MAX_SMELTS)throw new IOException("Ungültige Schmelzvorgänge");
        for(int n=0;n<count;n++){int cell=in.readInt(),age=in.readInt();if(cell<LAYER||cell>=w.blocks.length||w.blocks[cell]!=13||!lava(w,cell)||age<1||age>=SMELT_STEPS||ores.put(cell,age)!=null)throw new IOException("Ungültiger Schmelzstand");}
        count=in.readInt();if(count<0||count>MAX_RITUALS)throw new IOException("Ungültige Glutvorgänge");
        for(int n=0;n<count;n++){int age=in.readInt();int[] cells=new int[6];for(int k=0;k<6;k++){cells[k]=in.readInt();if(cells[k]<LAYER||cells[k]>=w.blocks.length)throw new IOException("Ungültige Glutposition");}
            boolean shape=false;int origin=cells[0];for(int turn=0;turn<4;turn++)for(boolean mirror:new boolean[]{false,true})if(Arrays.equals(cells,pattern(origin%World.W,origin/LAYER,origin/World.W%World.D,turn,mirror)))shape=true;
            int key=Arrays.stream(cells).min().getAsInt();if(age<1||age>=RITUAL_STEPS||!shape||!matches(w,cells)||rituals.put(key,new Ritual(cells,age))!=null)throw new IOException("Ungültiger Glutstand");
        }
        refreshGlow();
    }
    /** Only newly generated Story worlds receive a small, incomplete clue. Saved worlds are never rebuilt. */
    static boolean ruin(World w){
        Random random=new Random(w.seed^0x474c5554L);
        for(int attempt=0;attempt<600;attempt++){
            int x=8+random.nextInt(World.W-16),z=8+random.nextInt(World.D-16);if(Math.abs(x-w.x)+Math.abs(z-w.z)<24)continue;
            // Deep chamber with an untouched stone shell and at least three blocks of roof.
            int floor=3+random.nextInt(3);
            boolean safe=true;
            for(int dx=-4;dx<=5;dx++)for(int dz=-4;dz<=6;dz++)for(int y=floor-1;y<=floor+8;y++){
                int b=w.get(x+dx,y,z+dz);
                if(b!=3&&b!=13&&b!=11||w.overlapsPlayer(x+dx,y,z+dz))safe=false;
            }
            if(!safe)continue;
            for(int dx=-3;dx<=4;dx++)for(int dz=-3;dz<=5;dz++){w.set(x+dx,floor-1,z+dz,3);w.set(x+dx,floor,z+dz,3);for(int y=floor+1;y<=floor+5;y++)w.set(x+dx,y,z+dz,0);}
            for(int dx=-2;dx<=3;dx++)for(int dz=-2;dz<=4;dz++)if((Math.abs(dx)==2||dz==-2||dz==4)&&(dx+dz)%3!=0)w.set(x+dx,floor+1,z+dz,7);
            int[] cells=pattern(x,floor+2,z,0,false);for(int n=0;n<6;n++){int i=cells[n];w.set(i%World.W,floor+1,i/World.W%World.D,15);if(n!=1&&n!=5)w.set(i%World.W,floor+2,i/World.W%World.D,PATTERN[n][2]);}
            w.set(x-2,floor+1,z+1,3);w.set(x-2,floor+2,z+1,3);w.set(x-2,floor+3,z+1,11);
            w.set(x+3,floor+1,z,15);w.set(x+3,floor+2,z,16);w.set(x+3,floor+1,z+2,13);
            return true;
        }
        return false;
    }
}
