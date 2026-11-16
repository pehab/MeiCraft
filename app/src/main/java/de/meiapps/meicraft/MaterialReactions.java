package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Bounded, pure plans. Online callers journal the entire plan; offline callers apply under the world lock. */
final class MaterialReactions {
    interface Space {
        int get(int x,int y,int z);String tag(int x,int y,int z);String token(int x,int y,int z);
        boolean inside(int x,int y,int z);boolean occupied(int x,int y,int z);
    }
    static final int[][] NEIGHBORS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final class Change {final int x,y,z,type;final String tag;Change(int x,int y,int z,int type,String tag){this.x=x;this.y=y;this.z=z;this.type=type;this.tag=tag;}String key(){return x+"_"+y+"_"+z;}}
    static boolean active(String tag){return tag.startsWith("wood_")||tag.startsWith("coal_")||tag.startsWith("glass_")||tag.startsWith("ore_")||tag.startsWith("ritual_");}
    static boolean authored(Space s,int x,int y,int z){String tag=s.tag(x,y,z);if(tag.equals("user")||active(tag))return true;for(int[] d:NEIGHBORS){tag=s.tag(x+d[0],y+d[1],z+d[2]);if(tag.equals("user")||active(tag))return true;}return false;}
    static int[] adjacent(Space s,int x,int y,int z,int type){for(int[] d:NEIGHBORS)if(s.get(x+d[0],y+d[1],z+d[2])==type)return new int[]{x+d[0],y+d[1],z+d[2]};return null;}
    static boolean potential(TreeGrowth.Space s,int x,int y,int z){int b=s.get(x,y,z);if(b==Materials.COAL||b==Materials.BASALT||b==8)return near(s,x,y,z,b==Materials.COAL?15:b==Materials.BASALT?14:Materials.CRYSTAL);return (b==5||b==13||b==14)&&s.get(x,y-1,z)==15||b==15&&near(s,x,y,z,10);}
    static boolean near(TreeGrowth.Space s,int x,int y,int z,int type){for(int[] d:NEIGHBORS)if(s.get(x+d[0],y+d[1],z+d[2])==type)return true;return false;}
    static String signature(Space s,int rx,int ry,int rz,int[][] cells){long h=0xcbf29ce484222325L;for(int[] p:cells)if(p[0]!=rx||p[1]!=ry||p[2]!=rz){h=(h^s.get(p[0],p[1],p[2]))*0x100000001b3L;h=(h^s.token(p[0],p[1],p[2]).hashCode())*0x100000001b3L;}return Long.toHexString(h);}
    static String phase(Space s,int x,int y,int z,String name,int limit,int[][] dependencies){String sig=signature(s,x,y,z,dependencies),tag=s.tag(x,y,z);int age=0;String[] p=tag.split("_");if(p.length==3&&p[0].equals(name)&&p[2].equals(sig))try{age=Integer.parseInt(p[1]);}catch(NumberFormatException ignored){}return age+1>=limit?"":name+"_"+(age+1)+"_"+sig;}
    static void add(List<Change> result,Set<String> used,List<Change> group,Space s){if(result.size()+group.size()>16)return;for(Change c:group)if(!s.inside(c.x,c.y,c.z)||used.contains(c.key())||c.type!=s.get(c.x,c.y,c.z)&&c.type!=0&&s.occupied(c.x,c.y,c.z))return;for(Change c:group){used.add(c.key());result.add(c);}}
    static List<Change> plan(Space s,boolean legacyForge,boolean requireAuthored){
        List<Change> result=new ArrayList<>();Set<String> used=new HashSet<>();
        for(int y=1;y<World.H&&result.size()<16;y++)for(int z=0;z<World.D&&result.size()<16;z++)for(int x=0;x<World.W&&result.size()<16;x++){
            int b=s.get(x,y,z);if(b!=5&&b!=15&&b!=Materials.COAL&&b!=Materials.BASALT&&b!=8&&(!legacyForge||b!=13&&b!=14))continue;
            if(used.contains(x+"_"+y+"_"+z))continue;String old=s.tag(x,y,z);if(b!=5&&requireAuthored&&!authored(s,x,y,z))continue;
            if(b==15&&adjacent(s,x,y,z,10)!=null){add(result,used,List.of(new Change(x,y,z,Materials.BASALT,"")),s);continue;}
            if(b==5){
                // Only deliberately placed wood may char; old buildings and grown trunks stay protected.
                if(!old.equals("user")&&!old.startsWith("wood_"))continue;
                if(s.get(x,y-1,z)!=15||adjacent(s,x,y,z,10)!=null){if(old.startsWith("wood_"))add(result,used,List.of(new Change(x,y,z,5,"user")),s);continue;}
                String tag=phase(s,x,y,z,"wood",3,new int[][]{{x,y-1,z}});add(result,used,List.of(new Change(x,y,z,tag.isEmpty()?Materials.COAL:5,tag.isEmpty()?"charcoal":tag)),s);continue;
            }
            if(b==Materials.COAL){if(old.equals("charcoal"))continue;int[] lava=adjacent(s,x,y,z,15);if(lava==null||adjacent(s,x,y,z,10)!=null){if(active(old))add(result,used,List.of(new Change(x,y,z,b,"user")),s);continue;}
                String tag=phase(s,x,y,z,"coal",3,new int[][]{lava});int out=tag.isEmpty()?(s.get(x,y-1,z)==15?Materials.EMBER:0):b;add(result,used,List.of(new Change(x,y,z,out,tag)),s);continue;}
            if(b==Materials.BASALT&&adjacent(s,x,y,z,14)!=null){add(result,used,List.of(new Change(x,y,z,Materials.POLISHED,"")),s);continue;}
            if(b==8){int[] crystal=adjacent(s,x,y,z,Materials.CRYSTAL);if(crystal!=null){String tag=phase(s,x,y,z,"glass",3,new int[][]{crystal});List<Change> group=new ArrayList<>();group.add(new Change(x,y,z,tag.isEmpty()?Materials.CRYSTAL_GLASS:8,tag));if(tag.isEmpty())group.add(new Change(crystal[0],crystal[1],crystal[2],0,""));add(result,used,group,s);}else if(old.startsWith("glass_"))add(result,used,List.of(new Change(x,y,z,b,"user")),s);continue;}
            if(legacyForge&&b==13){if(s.get(x,y-1,z)==15){String tag=phase(s,x,y,z,"ore",5,new int[][]{{x,y-1,z}});add(result,used,List.of(new Change(x,y,z,tag.isEmpty()?16:13,tag)),s);}else if(old.startsWith("ore_"))add(result,used,List.of(new Change(x,y,z,b,"user")),s);continue;}
            if(legacyForge&&b==14){boolean found=false;
                for(int rotation=0;rotation<4&&!found;rotation++)for(boolean mirror:new boolean[]{false,true}){
                    int dx=0,dz=1;for(int n=0;n<rotation;n++){int v=dx;dx=-dz;dz=v;}int ox=x-dx,oz=z-dz;int[][] cells=new int[6][3];boolean valid=true;
                    for(int n=0;n<6;n++){int[] p=Forge.PATTERN[n];dx=mirror?-p[0]:p[0];dz=p[1];for(int k=0;k<rotation;k++){int v=dx;dx=-dz;dz=v;}cells[n]=new int[]{ox+dx,y,oz+dz};if(!s.inside(ox+dx,y,oz+dz)||s.get(ox+dx,y,oz+dz)!=p[2]||s.get(ox+dx,y-1,oz+dz)!=15)valid=false;}
                    if(!valid)continue;int[] anchor=cells[2][0]<cells[3][0]||cells[2][0]==cells[3][0]&&cells[2][2]<cells[3][2]?cells[2]:cells[3];if(anchor[0]!=x||anchor[2]!=z)continue;
                    int[][] deps=new int[12][3];for(int n=0;n<6;n++){deps[n]=cells[n];deps[n+6]=new int[]{cells[n][0],y-1,cells[n][2]};}String tag=phase(s,x,y,z,"ritual",3,deps);
                    List<Change> group=new ArrayList<>();if(!tag.isEmpty())group.add(new Change(x,y,z,b,tag));else for(int[] c:cells)group.add(new Change(c[0],c[1],c[2],c[0]==x&&c[2]==z?11:0,""));add(result,used,group,s);found=true;break;
                }if(!found&&old.startsWith("ritual_"))add(result,used,List.of(new Change(x,y,z,b,"user")),s);
            }
        }return result;
    }
    boolean applying;final Map<Integer,String> states=new TreeMap<>();final BitSet touched=new BitSet(World.W*World.H*World.D);
    void touch(int index){touched.set(index);}
    void changed(int index){if(applying)return;states.remove(index);for(int[] d:NEIGHBORS){int i=index+d[0]+d[1]*World.W*World.D+d[2]*World.W;if(!"charcoal".equals(states.get(i)))states.remove(i);}}
    void step(World w){
        Space space=new Space(){public int get(int x,int y,int z){return w.get(x,y,z);}public boolean inside(int x,int y,int z){return w.contains(x,y,z)&&y>0;}public boolean occupied(int x,int y,int z){return w.overlapsPlayer(x,y,z);}public String token(int x,int y,int z){return "";}public String tag(int x,int y,int z){return w.contains(x,y,z)?states.getOrDefault(w.idx(x,y,z),touched.get(w.idx(x,y,z))?"user":""):"";}};
        applying=true;try{for(Change c:plan(space,false,true)){w.set(c.x,c.y,c.z,c.type);if(active(c.tag)||c.tag.equals("charcoal"))states.put(w.idx(c.x,c.y,c.z),c.tag);else states.remove(w.idx(c.x,c.y,c.z));}}finally{applying=false;}
    }
    void write(DataOutputStream out)throws IOException{out.writeInt(states.size());for(Map.Entry<Integer,String> e:states.entrySet()){out.writeInt(e.getKey());out.writeUTF(e.getValue());}byte[] data=touched.toByteArray();out.writeInt(data.length);out.write(data);}
    static boolean validState(String tag,int block){if(tag.equals("charcoal"))return block==Materials.COAL;if(!tag.matches("(wood|coal|glass)_[12]_[a-f0-9]{1,16}"))return false;return block==(tag.startsWith("wood_")?5:tag.startsWith("coal_")?Materials.COAL:8);}
    void read(DataInputStream in,World w)throws IOException{int count=in.readInt();if(count<0||count>w.blocks.length)throw new IOException("Zu viele Materialreaktionen");for(int n=0;n<count;n++){int index=in.readInt();String tag=in.readUTF();if(index<World.W*World.D||index>=w.blocks.length||!validState(tag,w.blocks[index]&255)||states.put(index,tag)!=null)throw new IOException("Ungültige Materialreaktion");}int length=in.readInt();if(length<0||length>(w.blocks.length+7)/8)throw new IOException("Ungültige Bearbeitungshistorie");byte[] data=new byte[length];in.readFully(data);touched.or(BitSet.valueOf(data));}
}
