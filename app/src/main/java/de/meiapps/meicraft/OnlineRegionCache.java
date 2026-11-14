package de.meiapps.meicraft;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Disposable, authenticated-online-only acceleration. Never an inventory or world backup. */
final class OnlineRegionCache implements AutoCloseable {
    static final long MAX_FILE=16L*1024*1024,MAX_TOTAL=64L*1024*1024;
    private static final int MAGIC=0x4d435243,FORMAT=3;
    private static final Object FILE_LOCK=new Object();
    final File directory;final String worldId;final long seed;final int side;
    final ExecutorService writer=Executors.newSingleThreadExecutor();
    private final Map<Integer,Snapshot> pending=new LinkedHashMap<>();
    private boolean scheduled,closed;
    static final class Snapshot {
        final long revision;final String watermark;final Map<String,Long> heads;
        final Map<String,String> versions;final Map<String,Integer> types;final Map<String,Boolean> renewable;
        Snapshot(long revision,String watermark,Map<String,String> versions,Map<String,Integer> types,Map<String,Boolean> renewable){
            this(revision,watermark,versions,types,renewable,Collections.emptyMap());
        }
        Snapshot(long revision,String watermark,Map<String,String> versions,Map<String,Integer> types,Map<String,Boolean> renewable,Map<String,Long> heads){
            this.heads=new HashMap<>(heads);this.revision=revision;this.watermark=watermark;this.versions=new HashMap<>(versions);this.types=new HashMap<>(types);this.renewable=new HashMap<>(renewable);
        }
    }
    OnlineRegionCache(File directory,String worldId,long seed,int side){
        if(!worldId.matches("[a-f0-9]{32}")||(side!=2&&side!=3))throw new IllegalArgumentException("Cache identity");
        this.directory=directory;this.worldId=worldId;this.seed=seed;this.side=side;
    }
    File file(int region){if(region<0||region>=side*side)throw new IllegalArgumentException("Cache region");return new File(directory,worldId+"-"+region+".bin");}
    static void timestamp(String value)throws IOException{try{if(value.length()>64)throw new IllegalArgumentException();Instant.parse(value);}catch(RuntimeException e){throw new IOException("Cache timestamp",e);}}
    Snapshot load(int region){
        synchronized(FILE_LOCK){File file=file(region);if(!file.isFile()||file.length()>MAX_FILE)return null;
            try(CheckedInputStream checked=new CheckedInputStream(new BufferedInputStream(new FileInputStream(file)),new CRC32());DataInputStream in=new DataInputStream(checked)){
                if(in.readInt()!=MAGIC||in.readInt()!=FORMAT||in.readInt()!=OnlineWorld.PROTOCOL||!in.readUTF().equals(worldId)||in.readLong()!=seed||in.readInt()!=side||in.readInt()!=region)return null;
                long revision=in.readLong();String watermark=in.readUTF();timestamp(watermark);int actors=in.readInt();if(actors<0||actors>256)throw new IOException("Cache actors");Map<String,Long> heads=new HashMap<>();
                for(int i=0;i<actors;i++){String actor=in.readUTF();long sequence=in.readLong();if(!actor.matches("[A-Za-z0-9_-]{1,128}")||sequence<0||heads.put(actor,sequence)!=null)throw new IOException("Cache actor");}
                int count=in.readInt();
                if(revision<0||count<0||count>World.W*(World.H-1)*World.D)throw new IOException("Cache bounds");
                Map<String,String> versions=new HashMap<>();Map<String,Integer> types=new HashMap<>();Map<String,Boolean> renewable=new HashMap<>();
                for(int i=0;i<count;i++){
                    int x=in.readUnsignedByte(),y=in.readUnsignedByte(),z=in.readUnsignedByte(),type=in.readUnsignedByte();boolean water=in.readBoolean();String version=in.readUTF();if(!version.matches("[a-f0-9]{32}"))timestamp(version);
                    if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Cache block");String key=x+"_"+y+"_"+z;
                    if(versions.put(key,version)!=null)throw new IOException("Cache duplicate");types.put(key,type);renewable.put(key,water);
                }
                long checksum=checked.getChecksum().getValue();if(in.readLong()!=checksum||in.read()!=-1)throw new IOException("Cache checksum");
                return new Snapshot(revision,watermark,versions,types,renewable,heads);
            }catch(IOException|RuntimeException ignored){return null;}
        }
    }
    /** At most one queued snapshot per region; disk I/O never runs on the network/render worker. */
    synchronized void offer(int region,Snapshot snapshot){
        if(closed)return;pending.put(region,snapshot);if(scheduled)return;scheduled=true;
        writer.execute(()->{while(true){int id;Snapshot next;synchronized(this){if(pending.isEmpty()){scheduled=false;return;}id=pending.keySet().iterator().next();next=pending.remove(id);}save(id,next);}});
    }
    void save(int region,Snapshot snapshot){
        synchronized(FILE_LOCK){File temporary=null;
            try{
                timestamp(snapshot.watermark);if(snapshot.revision<0||snapshot.types.size()>World.W*(World.H-1)*World.D)return;
                if(!directory.isDirectory()&&!directory.mkdirs())return;
                temporary=File.createTempFile("region-",".tmp",directory);
                try(FileOutputStream raw=new FileOutputStream(temporary);CheckedOutputStream checked=new CheckedOutputStream(new BufferedOutputStream(raw),new CRC32());DataOutputStream out=new DataOutputStream(checked)){
                    out.writeInt(MAGIC);out.writeInt(FORMAT);out.writeInt(OnlineWorld.PROTOCOL);out.writeUTF(worldId);out.writeLong(seed);out.writeInt(side);out.writeInt(region);
                    out.writeLong(snapshot.revision);out.writeUTF(snapshot.watermark);out.writeInt(snapshot.heads.size());
                    for(Map.Entry<String,Long> head:snapshot.heads.entrySet()){out.writeUTF(head.getKey());out.writeLong(head.getValue());}
                    out.writeInt(snapshot.types.size());
                    for(Map.Entry<String,Integer> entry:snapshot.types.entrySet()){
                        String[] xyz=entry.getKey().split("_");if(xyz.length!=3)throw new IOException("Cache coordinate");int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]),type=entry.getValue();
                        if(!OnlineWorld.validBlock(x,y,z,type))throw new IOException("Cache block");String version=snapshot.versions.get(entry.getKey());if(!version.matches("[a-f0-9]{32}"))timestamp(version);
                        out.writeByte(x);out.writeByte(y);out.writeByte(z);out.writeByte(type);out.writeBoolean(snapshot.renewable.getOrDefault(entry.getKey(),false));out.writeUTF(version);
                    }
                    out.flush();long checksum=checked.getChecksum().getValue();out.writeLong(checksum);out.flush();raw.getFD().sync();
                }
                if(temporary.length()>MAX_FILE)return;
                Files.move(temporary.toPath(),file(region).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                prune();
            }catch(IOException|RuntimeException ignored){/* A cache miss must never interrupt play or change the cloud state. */}
            finally{if(temporary!=null)temporary.delete();}
        }
    }
    private void prune(){
        File[] files=directory.listFiles((dir,name)->name.matches("[a-f0-9]{32}-[0-8]\\.bin"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File file:files)total+=file.length();
        for(File file:files){if(total<=MAX_TOTAL)break;long size=file.length();if(file.delete())total-=size;}
    }
    @Override public synchronized void close(){closed=true;writer.shutdown();}
}
