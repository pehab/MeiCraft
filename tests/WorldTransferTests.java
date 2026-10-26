package de.meiapps.meicraft;

import com.google.zxing.common.BitMatrix;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

public final class WorldTransferTests {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    interface Action {void run()throws Exception;}
    static void rejects(Action action,String message)throws Exception{boolean rejected=false;try{action.run();}catch(IOException e){rejected=true;}check(rejected,message);}
    static byte[] raster(BitMatrix matrix,int scale,int rotation,boolean inverted){
        int n=matrix.getWidth(),size=n*scale;byte[] pixels=new byte[size*size];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            int mx=x/scale,my=y/scale;for(int turn=0;turn<rotation;turn++){int old=mx;mx=my;my=n-1-old;}
            int value=matrix.get(mx,my)?22:231;if(inverted)value=255-value;
            pixels[y*size+x]=(byte)value;
        }
        return pixels;
    }
    public static void main(String[] args)throws Exception{
        World world=new World(20261026,true);world.progress.chapter=7;world.progress.iron=28;world.progress.sword=true;world.progress.buckets=3;
        world.survival.time=520;world.survival.enemiesEnabled=false;world.set(15,30,15,7);world.progress.inventory[15]=4;
        byte[] packet=WorldTransfer.pack("Burg der Kinder 🌍",world);List<String> frames=WorldTransfer.frames(packet);
        check(frames.size()>1,"real world needs several codes");check(frames.size()<100,"normal world fits a practical QR sequence");
        WorldTransfer.Collector collector=new WorldTransfer.Collector();List<String> shuffled=new ArrayList<>(frames);Collections.shuffle(shuffled,new Random(42));
        for(String frame:shuffled){check(collector.add(frame),"out-of-order fragment accepted");check(!collector.add(frame),"duplicate fragment does not advance progress");}
        check(collector.complete()&&collector.received()==frames.size(),"all fragments assembled exactly once");
        check(Arrays.equals(packet,collector.packet()),"packet bytes remain exact");
        WorldTransfer transferred=WorldTransfer.unpack(collector.packet());
        check(transferred.name.equals("Burg der Kinder 🌍"),"Unicode name survives transfer");
        check(Arrays.equals(transferred.world.blocks,world.blocks)&&transferred.world.story,"all blocks and mode survive transfer");
        check(transferred.world.progress.iron==28&&transferred.world.progress.sword&&transferred.world.progress.buckets==3,"resources and equipment survive transfer");
        check(transferred.world.survival.time==520&&!transferred.world.survival.enemiesEnabled,"survival settings and time survive transfer");
        check(transferred.world.nature.springs.equals(world.nature.springs),"natural springs survive transfer without regeneration");
        WorldTransfer.Collector partial=new WorldTransfer.Collector();partial.add(frames.get(0));rejects(partial::packet,"incomplete packet cannot be imported");
        List<String> other=WorldTransfer.frames(WorldTransfer.pack("Andere Welt",new World(17)));
        rejects(()->partial.add(other.get(0)),"other transfer does not overwrite progress");check(partial.received()==1,"existing progress retained");
        String[] fields=frames.get(1).split(":");fields[5]=fields[5].equals("0")?"1":"0";
        rejects(()->partial.add(String.join(":",fields)),"damaged frame rejected by CRC");check(partial.received()==1,"bad fragment cannot advance progress");
        rejects(()->partial.add("https://example.invalid/"),"unrelated QR ignored");
        rejects(()->partial.add("X".repeat(1200)),"oversized frame rejected");
        for(String value:new String[]{"-1","2147483647","99999999999999999999999"}){
            String[] bad=frames.get(0).split(":");bad[2]=value;rejects(()->new WorldTransfer.Collector().add(String.join(":",bad)),"invalid index rejected");
        }
        String[] huge=frames.get(0).split(":");huge[4]="2147483647";huge[3]="2147483647";rejects(()->new WorldTransfer.Collector().add(String.join(":",huge)),"huge declared transfer rejected before allocation");
        rejects(()->WorldTransfer.unpack(Arrays.copyOf(packet,packet.length-1)),"truncated envelope rejected");
        byte[] badVersion=packet.clone();badVersion[7]=2;rejects(()->WorldTransfer.unpack(badVersion),"future protocol version rejected");
        rejects(()->WorldTransfer.unpack(new byte[WorldTransfer.MAX_PACKET+1]),"oversized packet rejected");
        byte[] corrupt=packet.clone();corrupt[corrupt.length-10]^=1;List<String> rechecksummed=WorldTransfer.frames(corrupt);
        WorldTransfer.Collector mixed=new WorldTransfer.Collector();String originalId=WorldTransfer.digest(packet);
        for(String frame:rechecksummed){String[] bad=frame.split(":");bad[1]=originalId;mixed.add(String.join(":",bad));}
        rejects(mixed::packet,"final SHA-256 detects corrupt packet even when frame CRCs match");
        byte[] creative=WorldTransfer.pack("Kreativ",new World(31));check(!WorldTransfer.unpack(creative).world.story,"creative snapshot supported");
        // Read the rendered optical QR, rather than handing the protocol its own strings.
        for(int index=0;index<frames.size();index++){
            BitMatrix matrix=QrCodec.encode(frames.get(index));int scale=3,size=matrix.getWidth()*scale;
            String decoded=QrCodec.decode(raster(matrix,scale,index%4,false),size,size);
            check(frames.get(index).equals(decoded),"every real-world fragment decodes from scaled/rotated image");
        }
        BitMatrix matrix=QrCodec.encode(frames.get(0));int size=matrix.getWidth()*4;
        check(frames.get(0).equals(QrCodec.decode(raster(matrix,4,2,true),size,size)),"inverted optical image supported");
        byte[] blank=new byte[320*240];Arrays.fill(blank,(byte)255);check(QrCodec.decode(blank,320,240)==null,"non-QR camera image ignored");
        check(QrCodec.decode(new byte[2],320,240)==null,"short camera buffer rejected");
        check(QrCodec.decode(new byte[1],Integer.MAX_VALUE,2)==null,"oversized image rejected before allocation");
        // Worst-case incompressible packets remain bounded and can finish after missed frames.
        byte[] maximum=new byte[WorldTransfer.MAX_PACKET];new Random(13).nextBytes(maximum);List<String> many=WorldTransfer.frames(maximum);
        WorldTransfer.Collector large=new WorldTransfer.Collector();for(int i=many.size()-1;i>=0;i--)if(i%2==0)large.add(many.get(i));
        check(!large.complete(),"missed fragments wait for another cycle");for(String frame:many)large.add(frame);
        check(Arrays.equals(maximum,large.packet()),"maximum supported packet assembles with repeats");
        rejects(()->WorldTransfer.frames(new byte[WorldTransfer.MAX_PACKET+1]),"sender enforces packet cap");
        System.out.println("PASS: "+checks+" QR transfer assertions · "+frames.size()+" optical frames");
    }
}
