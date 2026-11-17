package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Small reversible translations. Plans validate the entire swept space before changing a cell. */
final class Mechanics {
    static final int MAX_BLOCKS=7,MAX_DISTANCE=4;
    static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final String[] LABELS={"→ Rechts (+X)","← Links (−X)","↑ Hoch","↓ Runter","↘ Vor (+Z)","↖ Zurück (−Z)"};
    final Map<Integer,Link> links=new LinkedHashMap<>();
    static final class Block {
        final int x,y,z,type;
        Block(int x,int y,int z,int type){this.x=x;this.y=y;this.z=z;this.type=type;}
        String key(){return x+"_"+y+"_"+z;}
    }
    static final class Link {
        final Block button;final List<Block> blocks;final int direction,distance;final boolean moved;
        Link(Block button,List<Block> blocks,int direction,int distance,boolean moved){
            if(button==null||blocks==null||blocks.isEmpty()||blocks.size()>MAX_BLOCKS||direction<0||direction>=6||distance<1||distance>MAX_DISTANCE)throw new IllegalArgumentException("Ungültige Mechanik");
            this.button=button;this.blocks=Collections.unmodifiableList(new ArrayList<>(blocks));this.direction=direction;this.distance=distance;this.moved=moved;
            Set<String> unique=new HashSet<>();unique.add(button.key());valid(button);
            for(Block b:blocks){valid(b);if(!unique.add(b.key()))throw new IllegalArgumentException("Block doppelt oder Schalter in der Gruppe");int[] d=DIRECTIONS[direction];int xx=b.x+d[0]*distance,yy=b.y+d[1]*distance,zz=b.z+d[2]*distance;if(xx<0||xx>=288||yy<1||yy>=World.H||zz<0||zz>=288)throw new IllegalArgumentException("Bewegung außerhalb der Welt");for(int n=1;n<=distance;n++)if(b.x+d[0]*n==button.x&&b.y+d[1]*n==button.y&&b.z+d[2]*n==button.z)throw new IllegalArgumentException("Der Schalter steht im Weg");}
        }
        Link toggle(){return new Link(button,blocks,direction,distance,!moved);}
        String encode(){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeByte(1);out.writeByte(direction);out.writeByte(distance);out.writeBoolean(moved);writeBlock(out,button);out.writeByte(blocks.size());for(Block b:blocks)writeBlock(out,b);}return "mech_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
    }
    static void valid(Block b){if(b.x<0||b.x>=288||b.y<1||b.y>=World.H||b.z<0||b.z>=288||b.type<1||b.type>=World.NAMES.length||Nature.liquid(b.type)||b.type==6)throw new IllegalArgumentException("Nur feste Blöcke ohne Laub bewegen");}
    static void writeBlock(DataOutputStream out,Block b)throws IOException{out.writeShort(b.x);out.writeByte(b.y);out.writeShort(b.z);out.writeByte(b.type);}
    static Block readBlock(DataInputStream in)throws IOException{return new Block(in.readUnsignedShort(),in.readUnsignedByte(),in.readUnsignedShort(),in.readUnsignedByte());}
    static Link decode(String text)throws IOException{
        try{if(text==null||!text.matches("mech_[A-Za-z0-9_-]{20,100}"))throw new IllegalArgumentException();try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(text.substring(5))))){if(in.readUnsignedByte()!=1)throw new IllegalArgumentException();int direction=in.readUnsignedByte(),distance=in.readUnsignedByte();boolean moved=in.readBoolean();Block button=readBlock(in);int count=in.readUnsignedByte();if(count<1||count>MAX_BLOCKS)throw new IllegalArgumentException();List<Block> blocks=new ArrayList<>();for(int i=0;i<count;i++)blocks.add(readBlock(in));Link link=new Link(button,blocks,direction,distance,moved);if(in.read()!=-1||!text.equals(link.encode()))throw new IllegalArgumentException();return link;}}catch(RuntimeException e){throw new IOException("Ungültige Mechanik",e);}
    }
    interface Space {int get(int x,int y,int z);boolean inside(int x,int y,int z);boolean occupied(int x,int y,int z);}
    static List<Block> plan(Link link,Space space){
        if(space.get(link.button.x,link.button.y,link.button.z)!=link.button.type)throw new IllegalArgumentException("Der Schalter wurde verändert");
        int[] d=DIRECTIONS[link.direction];int sign=link.moved?-1:1;Map<String,Block> current=new LinkedHashMap<>(),result=new LinkedHashMap<>();
        for(Block b:link.blocks){Block source=new Block(b.x+(link.moved?d[0]*link.distance:0),b.y+(link.moved?d[1]*link.distance:0),b.z+(link.moved?d[2]*link.distance:0),b.type);if(!space.inside(source.x,source.y,source.z)||space.get(source.x,source.y,source.z)!=b.type)throw new IllegalArgumentException("Ein beweglicher Block wurde verändert");current.put(source.key(),source);result.put(source.key(),new Block(source.x,source.y,source.z,0));}
        for(Block source:current.values())for(int n=1;n<=link.distance;n++){int x=source.x+d[0]*n*sign,y=source.y+d[1]*n*sign,z=source.z+d[2]*n*sign;String key=x+"_"+y+"_"+z;if(!space.inside(x,y,z)||space.occupied(x,y,z)||space.get(x,y,z)!=0&&!current.containsKey(key))throw new IllegalArgumentException("Der Weg ist versperrt");if(n==link.distance)result.put(key,new Block(x,y,z,source.type));}
        return new ArrayList<>(result.values());
    }
    static Space space(World w){return new Space(){public int get(int x,int y,int z){return w.get(x,y,z);}public boolean inside(int x,int y,int z){return y>0&&w.contains(x,y,z);}public boolean occupied(int x,int y,int z){return w.overlapsPlayer(x,y,z);}};}
    void install(World w,Link link){if(links.size()>=64&&!links.containsKey(w.idx(link.button.x,link.button.y,link.button.z)))throw new IllegalArgumentException("Höchstens 64 Mechaniken pro Welt");for(Block b:link.blocks)if(!w.contains(b.x,b.y,b.z))throw new IllegalArgumentException("Außerhalb der Welt");plan(new Link(link.button,link.blocks,link.direction,link.distance,false),space(w));links.put(w.idx(link.button.x,link.button.y,link.button.z),link);w.revision++;}
    void apply(World w,Link link){List<Block> changes=plan(link,space(w));for(Block b:changes){w.set(b.x,b.y,b.z,b.type);w.materials.touch(w.idx(b.x,b.y,b.z));}links.put(w.idx(link.button.x,link.button.y,link.button.z),link.toggle());}
    void update(World w,int x,int y,int z,int type,String tag)throws IOException{int key=w.idx(x,y,z);if(tag.startsWith("mech_")){Link link=decode(tag);if(link.button.x!=x||link.button.y!=y||link.button.z!=z||link.button.type!=type)throw new IOException("Ungültiger Schalter");links.put(key,link);}else links.remove(key);}
    void write(DataOutputStream out)throws IOException{out.writeInt(links.size());for(Link link:links.values())out.writeUTF(link.encode());}
    void read(DataInputStream in,World w)throws IOException{int count=in.readInt();if(count<0||count>64)throw new IOException("Zu viele Mechaniken");for(int i=0;i<count;i++){Link link=decode(in.readUTF());Block b=link.button;if(!w.contains(b.x,b.y,b.z)||links.put(w.idx(b.x,b.y,b.z),link)!=null)throw new IOException("Ungültige Mechanik");}}
}
