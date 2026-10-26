package de.meiapps.meicraft;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.CRC32;

/** Versioned, bounded snapshots transferred as independently checked QR fragments. */
final class WorldTransfer {
    static final int CHUNK=480, MAX_WORLD=512*1024, MAX_PACKET=MAX_WORLD+256;
    static final int MAX_FRAMES=(MAX_PACKET+CHUNK-1)/CHUNK;
    static final String PREFIX="MEICRAFT1";
    final String name;
    final World world;
    final byte[] worldBytes;
    private WorldTransfer(String name,World world,byte[] bytes){this.name=name;this.world=world;worldBytes=bytes;}

    static byte[] pack(String name,World world)throws IOException{
        new SavedWorld("creative",name,0); // Same display-name rules in either mode.
        byte[] data=world.encode();if(data.length>MAX_WORLD)throw new IOException("Diese Welt ist zu groß für die QR-Übertragung.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(0x4d435152);out.writeInt(1);out.writeUTF(name);out.writeBoolean(world.story);out.writeInt(data.length);out.write(data);
        }
        return bytes.toByteArray();
    }
    static WorldTransfer unpack(byte[] packet)throws IOException{
        if(packet==null||packet.length<16||packet.length>MAX_PACKET)throw new IOException("Ungültige Weltgröße");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(packet))){
            if(in.readInt()!=0x4d435152||in.readInt()!=1)throw new IOException("Unbekannte Übertragungs-Version");
            String name=in.readUTF();new SavedWorld("creative",name,0);boolean story=in.readBoolean();int length=in.readInt();
            if(length<=0||length>MAX_WORLD||length!=in.available())throw new IOException("Unvollständige Welt");
            byte[] data=new byte[length];in.readFully(data);World world=World.decode(data);
            if(world.story!=story)throw new IOException("Weltmodus stimmt nicht überein");
            return new WorldTransfer(name,world,data);
        }catch(IllegalArgumentException e){throw new IOException("Ungültiger Weltname",e);}
    }
    static String digest(byte[] bytes){
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder result=new StringBuilder(64);
            for(byte b:hash)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static List<String> frames(byte[] packet)throws IOException{
        if(packet.length<16||packet.length>MAX_PACKET)throw new IOException("Ungültige Weltgröße");
        int total=(packet.length+CHUNK-1)/CHUNK;String id=digest(packet);List<String> result=new ArrayList<>(total);
        for(int index=0;index<total;index++){
            byte[] part=Arrays.copyOfRange(packet,index*CHUNK,Math.min(packet.length,(index+1)*CHUNK));CRC32 crc=new CRC32();crc.update(part);
            result.add(PREFIX+":"+id+":"+index+":"+total+":"+packet.length+":"+Long.toHexString(crc.getValue())+":"+Base64.getEncoder().encodeToString(part));
        }
        return Collections.unmodifiableList(result);
    }
    static final class Collector {
        private String id;
        private byte[][] parts;
        private int received,length;
        int received(){return received;}
        int total(){return parts==null?0:parts.length;}
        boolean complete(){return parts!=null&&received==parts.length;}
        /** Valid fragments from another snapshot never discard the active transfer. */
        boolean add(String text)throws IOException{
            if(text==null||text.length()>1100)throw new IOException("Kein meiCraft-Weltcode");
            String[] fields=text.split(":",-1);
            if(fields.length!=7||!PREFIX.equals(fields[0])||!fields[1].matches("[0-9a-f]{64}"))throw new IOException("Kein meiCraft-Weltcode");
            try{
                int index=Integer.parseInt(fields[2]),total=Integer.parseInt(fields[3]),size=Integer.parseInt(fields[4]);
                if(size<16||size>MAX_PACKET||total!=(size+CHUNK-1)/CHUNK||total>MAX_FRAMES||index<0||index>=total||!fields[5].matches("[0-9a-f]{1,8}"))throw new IOException("Ungültiger Weltcode");
                byte[] data=Base64.getDecoder().decode(fields[6]);int expected=Math.min(CHUNK,size-index*CHUNK);
                CRC32 crc=new CRC32();crc.update(data);
                if(data.length!=expected||crc.getValue()!=Long.parseLong(fields[5],16))throw new IOException("Bild beschädigt · wird erneut gescannt");
                if(id!=null&&(!id.equals(fields[1])||length!=size||parts.length!=total))throw new IOException("Andere Welt erkannt · zuerst Empfang zurücksetzen");
                if(id==null){id=fields[1];length=size;parts=new byte[total][];}
                if(parts[index]!=null){if(!Arrays.equals(parts[index],data))throw new IOException("Widersprüchlicher Weltcode");return false;}
                parts[index]=data;received++;return true;
            }catch(IllegalArgumentException e){throw new IOException("Ungültiger Weltcode",e);}
        }
        byte[] packet()throws IOException{
            if(!complete())throw new IOException("Es fehlen noch Bilder");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(length);for(byte[] part:parts)bytes.write(part);
            byte[] result=bytes.toByteArray();if(!digest(result).equals(id))throw new IOException("Prüfsumme stimmt nicht · Empfang zurücksetzen");return result;
        }
    }
}
