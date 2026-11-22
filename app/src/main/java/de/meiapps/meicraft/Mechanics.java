package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Small reversible translations. Plans validate the entire swept space before changing a cell. */
final class Mechanics {
    static final int MAX_BLOCKS=9,MAX_DISTANCE=4,MAX_BUTTONS=3,MAX_MARKER=140;
    static final int[][] DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final String[] LABELS={"→ Rechts (+X)","← Links (−X)","↑ Hoch","↓ Runter","↘ Vor (+Z)","↖ Zurück (−Z)"};
    final Map<Integer,Link> links=new LinkedHashMap<>();
    final PressureTimer pressureTimer=new PressureTimer();
    static final class Block {
        final int x,y,z,type;
        Block(int x,int y,int z,int type){this.x=x;this.y=y;this.z=z;this.type=type;}
        String key(){return x+"_"+y+"_"+z;}
    }
    static final class Link {
        final Block button;final List<Block> buttons,blocks;final int direction,distance;final boolean moved,pressure;final int closeDelay;final String name;
        Link(Block button,List<Block> blocks,int direction,int distance,boolean moved){this(button,blocks,direction,distance,moved,"");}
        Link(Block button,List<Block> blocks,int direction,int distance,boolean moved,String name){this(button,blocks,direction,distance,moved,name,false,List.of(button));}
        Link(Block button,List<Block> blocks,int direction,int distance,boolean moved,String name,boolean pressure,List<Block> buttons){this(button,blocks,direction,distance,moved,name,pressure,buttons,0);}
        Link(Block button,List<Block> blocks,int direction,int distance,boolean moved,String name,boolean pressure,List<Block> buttons,int closeDelay){
            if(closeDelay!=0&&closeDelay!=2&&closeDelay!=5)throw new IllegalArgumentException("Schließverzögerung: 0, 2 oder 5 Sekunden");this.closeDelay=closeDelay;
            this.name=validName(name);
            if(button==null||blocks==null||blocks.isEmpty()||blocks.size()>MAX_BLOCKS||direction<0||direction>=6||distance<1||distance>MAX_DISTANCE||buttons==null||buttons.isEmpty()||buttons.size()>MAX_BUTTONS||!buttons.get(0).key().equals(button.key())||buttons.get(0).type!=button.type)throw new IllegalArgumentException("Ungültige Mechanik");
            this.button=button;this.blocks=Collections.unmodifiableList(new ArrayList<>(blocks));this.buttons=Collections.unmodifiableList(new ArrayList<>(buttons));this.direction=direction;this.distance=distance;this.moved=moved;this.pressure=pressure;
            Set<String> unique=new HashSet<>();
            for(Block trigger:buttons){valid(trigger);if(!unique.add(trigger.key()))throw new IllegalArgumentException("Schalter doppelt");if(trigger.x/World.W!=button.x/World.W||trigger.z/World.D!=button.z/World.D||Math.abs(trigger.x-button.x)>16||Math.abs(trigger.y-button.y)>16||Math.abs(trigger.z-button.z)>16)throw new IllegalArgumentException("Weitere Schalter: gleicher Weltbereich, höchstens 16 Blöcke entfernt");}
            for(Block b:blocks){valid(b);if(!unique.add(b.key()))throw new IllegalArgumentException("Block doppelt oder Schalter in der Gruppe");int[] d=DIRECTIONS[direction];int xx=b.x+d[0]*distance,yy=b.y+d[1]*distance,zz=b.z+d[2]*distance;if(xx<0||xx>=288||yy<1||yy>=World.H||zz<0||zz>=288)throw new IllegalArgumentException("Bewegung außerhalb der Welt");for(int n=0;n<=distance;n++)for(Block trigger:buttons)if(b.x+d[0]*n==trigger.x&&b.y+d[1]*n==trigger.y&&b.z+d[2]*n==trigger.z)throw new IllegalArgumentException("Ein Schalter steht im Weg");}
        }
        Link state(boolean value){return new Link(button,blocks,direction,distance,value,name,pressure,buttons,closeDelay);}
        Link toggle(){return state(!moved);}
        Link named(String value){return new Link(button,blocks,direction,distance,moved,value,pressure,buttons,closeDelay);}
        Link mode(boolean value){return new Link(button,blocks,direction,distance,moved,name,value,buttons,closeDelay);}
        Link withButtons(List<Block> value){return new Link(button,blocks,direction,distance,moved,name,pressure,value,closeDelay);}
        Link delayed(int seconds){return new Link(button,blocks,direction,distance,moved,name,pressure,buttons,seconds);}
        String encode(){try{int version=closeDelay>0?4:pressure||buttons.size()>1?3:name.isEmpty()?1:2;ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.writeByte(version);out.writeByte(direction);out.writeByte(distance);out.writeBoolean(moved);writeBlock(out,button);out.writeByte(blocks.size());for(Block b:blocks)writeBlock(out,b);if(version>=2)out.writeUTF(name);if(version>=3){out.writeBoolean(pressure);out.writeByte(buttons.size()-1);for(int i=1;i<buttons.size();i++)writeBlock(out,buttons.get(i));if(version==4)out.writeByte(closeDelay);}}return "mech_"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
    }
    /** Runtime-only monotonic absence timer. Reloads conservatively start the full wait again. */
    static final class PressureTimer {
        static final class Wait {final String configuration;final long since;Wait(String configuration,long since){this.configuration=configuration;this.since=since;}}
        final Map<String,Wait> waiting=new LinkedHashMap<>();
        synchronized boolean desired(Link link,boolean pressed,long now){
            String key=link.button.key();if(pressed){waiting.remove(key);return true;}
            if(!link.moved||link.closeDelay==0){waiting.remove(key);return false;}
            String config=link.state(false).encode();Wait wait=waiting.get(key);if(wait==null||!wait.configuration.equals(config)||now<wait.since){wait=new Wait(config,now);waiting.put(key,wait);if(waiting.size()>64)waiting.remove(waiting.keySet().iterator().next());}
            return now-wait.since<link.closeDelay*1000L;
        }
    }
    static String validName(String value){String name=value==null?"":value.trim();if(name.length()>16||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>20||name.chars().anyMatch(c->Character.isISOControl(c)||Character.isSurrogate((char)c)))throw new IllegalArgumentException("Name: höchstens 16 Zeichen (Umlaute zählen mehr)");return name;}
    static void validateConfiguration(Link link,Space space){
        for(Block button:link.buttons)if(!space.inside(button.x,button.y,button.z)||space.get(button.x,button.y,button.z)!=button.type)throw new IllegalArgumentException("Ein Schalter wurde verändert oder ist nicht geladen");
        int[] d=DIRECTIONS[link.direction];for(Block b:link.blocks){if(space.get(b.x,b.y,b.z)!=b.type)throw new IllegalArgumentException("Ein ausgewählter Block wurde verändert");for(int n=0;n<=link.distance;n++)if(!space.inside(b.x+d[0]*n,b.y+d[1]*n,b.z+d[2]*n))throw new IllegalArgumentException("Bewegung außerhalb der Welt");}
    }
    static void valid(Block b){if(b.x<0||b.x>=288||b.y<1||b.y>=World.H||b.z<0||b.z>=288||b.type<1||b.type>=World.NAMES.length||Nature.liquid(b.type)||b.type==6)throw new IllegalArgumentException("Nur feste Blöcke ohne Laub bewegen");}
    static void writeBlock(DataOutputStream out,Block b)throws IOException{out.writeShort(b.x);out.writeByte(b.y);out.writeShort(b.z);out.writeByte(b.type);}
    static Block readBlock(DataInputStream in)throws IOException{return new Block(in.readUnsignedShort(),in.readUnsignedByte(),in.readUnsignedShort(),in.readUnsignedByte());}
    static Link decode(String text)throws IOException{
        try{if(text==null||!text.matches("mech_[A-Za-z0-9_-]{20,140}"))throw new IllegalArgumentException();try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(text.substring(5))))){int version=in.readUnsignedByte();if(version!=1&&version!=2&&version!=3&&version!=4)throw new IllegalArgumentException();int direction=in.readUnsignedByte(),distance=in.readUnsignedByte();boolean moved=in.readBoolean();Block button=readBlock(in);int count=in.readUnsignedByte();if(count<1||count>MAX_BLOCKS)throw new IllegalArgumentException();List<Block> blocks=new ArrayList<>();for(int i=0;i<count;i++)blocks.add(readBlock(in));String name=version>=2?in.readUTF():"";boolean pressure=version>=3&&in.readBoolean();List<Block> buttons=new ArrayList<>();buttons.add(button);if(version>=3){int extra=in.readUnsignedByte();if(extra>MAX_BUTTONS-1)throw new IllegalArgumentException();for(int i=0;i<extra;i++)buttons.add(readBlock(in));}int closeDelay=version==4?in.readUnsignedByte():0;Link link=new Link(button,blocks,direction,distance,moved,name,pressure,buttons,closeDelay);if(in.read()!=-1||!text.equals(link.encode()))throw new IllegalArgumentException();return link;}}catch(RuntimeException e){throw new IOException("Ungültige Mechanik",e);}
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
    Link at(World w,int x,int y,int z){for(Link link:links.values())for(Block b:link.buttons)if(b.x==x&&b.y==y&&b.z==z&&w.get(x,y,z)==b.type)return link;return null;}
    static boolean onPlate(Block b,float x,float y,float z){return x>=b.x&&x<b.x+1&&z>=b.z&&z<b.z+1&&Math.abs((y-1.6f)-(b.y+1))<=.12f;}
    static boolean pressed(Link link,Space space,float x,float y,float z){for(Block b:link.buttons)if(space.get(b.x,b.y,b.z)==b.type&&onPlate(b,x,y,z))return true;return false;}
    void install(World w,Link link){if(links.size()>=64&&!links.containsKey(w.idx(link.button.x,link.button.y,link.button.z)))throw new IllegalArgumentException("Höchstens 64 Mechaniken pro Welt");for(Block b:link.blocks)if(!w.contains(b.x,b.y,b.z))throw new IllegalArgumentException("Außerhalb der Welt");validateConfiguration(link,space(w));links.put(w.idx(link.button.x,link.button.y,link.button.z),link);w.revision++;}
    void apply(World w,Link link){List<Block> changes=plan(link,space(w));for(Block b:changes){w.set(b.x,b.y,b.z,b.type);w.materials.touch(w.idx(b.x,b.y,b.z));}links.put(w.idx(link.button.x,link.button.y,link.button.z),link.toggle());}
    void update(World w,int x,int y,int z,int type,String tag)throws IOException{int key=w.idx(x,y,z);if(tag.startsWith("mech_")){Link link=decode(tag);if(link.button.x!=x||link.button.y!=y||link.button.z!=z||link.button.type!=type)throw new IOException("Ungültiger Schalter");links.put(key,link);}else links.remove(key);}
    void write(DataOutputStream out)throws IOException{out.writeInt(links.size());for(Link link:links.values())out.writeUTF(link.encode());}
    void read(DataInputStream in,World w)throws IOException{int count=in.readInt();if(count<0||count>64)throw new IOException("Zu viele Mechaniken");for(int i=0;i<count;i++){Link link=decode(in.readUTF());Block b=link.button;if(!w.contains(b.x,b.y,b.z)||links.put(w.idx(b.x,b.y,b.z),link)!=null)throw new IOException("Ungültige Mechanik");}}
}
