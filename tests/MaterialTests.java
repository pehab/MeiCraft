package de.meiapps.meicraft;

import java.util.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;

public final class MaterialTests {
    static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static final class Space implements MaterialReactions.Space,TreeGrowth.Space {
        final World w=NatureTests.flat(false);final Map<String,String> tags=new HashMap<>(),tokens=new HashMap<>();int serial;
        public int get(int x,int y,int z){return w.get(x,y,z);}public boolean inside(int x,int y,int z){return w.contains(x,y,z)&&y>0;}public boolean occupied(int x,int y,int z){return w.overlapsPlayer(x,y,z);}
        public String tag(int x,int y,int z){return tags.getOrDefault(x+"_"+y+"_"+z,"");}public String token(int x,int y,int z){return tokens.getOrDefault(x+"_"+y+"_"+z,"");}
        void put(int x,int y,int z,int block){w.set(x,y,z,block);tags.put(x+"_"+y+"_"+z,"user");tokens.put(x+"_"+y+"_"+z,Integer.toString(++serial));}
        List<MaterialReactions.Change> tick(boolean legacy){List<MaterialReactions.Change> changes=MaterialReactions.plan(this,legacy,true);check(changes.size()<=16,"bounded atomic reaction group");for(MaterialReactions.Change c:changes){w.set(c.x,c.y,c.z,c.type);tags.put(c.key(),c.tag);tokens.put(c.key(),Integer.toString(++serial));}return changes;}
    }
    static int count(World w,int b){int n=0;for(byte v:w.blocks)if((v&255)==b)n++;return n;}
    static byte[] legacy(World w,int format)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(bytes))){
            out.writeInt(0x4d454943);out.writeInt(format);out.writeLong(w.seed);out.writeFloat(w.x);out.writeFloat(w.y);out.writeFloat(w.z);out.writeFloat(w.yaw);out.writeFloat(w.pitch);out.writeInt(w.selected);out.writeBoolean(w.flying);out.writeInt(w.blocks.length);out.write(w.blocks);
            out.writeBoolean(w.story);out.writeInt(w.progress.chapter);out.writeInt(w.progress.iron);out.writeInt(w.progress.placed);out.writeInt(w.progress.glassPlaced);out.writeInt(17);for(int i=0;i<17;i++)out.writeInt(w.progress.inventory[i]);out.writeInt(w.progress.buckets);out.writeBoolean(w.progress.sword);w.survival.write(out);w.nature.write(out);w.forge.write(out);if(format==6)w.nature.writeTrees(out,w);
        }return bytes.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        for(long seed:new long[]{31,1234,7654321}){
            World old=new World(seed,true),fresh=World.fresh(seed,true);check(old.terrainVersion==1&&fresh.terrainVersion==2,"explicit stable generation versions");check(count(old,17)==0&&count(old,20)==0,"legacy generator has no new ores or caves");check(count(fresh,17)>0&&count(fresh,19)>0,"new worlds contain coal ore and deep basalt");check(Arrays.equals(fresh.blocks,World.fresh(seed,true).blocks),"new generation deterministic");
            byte[] saved=fresh.encode();World restored=World.decode(saved);check(restored.terrainVersion==2&&Arrays.equals(restored.blocks,fresh.blocks),"terrain version and complete landscape round-trip");
            restored.set(20,4,20,7);restored.set(21,4,20,0);check(Arrays.equals(restored.blocks,World.decode(restored.encode()).blocks),"edits equal to baseline and structures are never regenerated");
            World a=OnlineTerrain.region(seed,0,2,1),b=OnlineTerrain.region(seed,0,2);check(Arrays.equals(a.blocks,b.blocks),"online legacy baseline exact");World c=OnlineTerrain.region(seed,0,2,2);check(count(c,17)>0&&c.terrainVersion==2,"online version 2 material generation");
        }
        for(int format:new int[]{5,6}){World old=new World(31,true);old.progress.inventory[16]=7;World loaded=World.decode(legacy(old,format));check(Arrays.equals(old.blocks,loaded.blocks)&&loaded.terrainVersion==1,"real legacy format preserves complete world without regeneration");check(loaded.progress.inventory[16]==7&&loaded.progress.inventory[23]==0,"legacy 17-slot inventory expands without gifting materials");check(Arrays.equals(loaded.blocks,World.decode(loaded.encode()).blocks),"legacy save upgrades and reloads exactly");}
        World seam=OnlineTerrain.area(1234,80,0,2),tile=OnlineTerrain.region(1234,1,2,2);for(int y=0;y<48;y++)for(int z=0;z<96;z++)for(int gx=96;gx<176;gx++)check(seam.get(gx-80,y,z)==tile.get(gx-96,y,z),"version-2 ores and caves agree in overlapping windows");
        int caves=0;for(int seed=0;seed<12;seed++)caves+=count(World.fresh(seed,true),Materials.CRYSTAL);check(caves>0,"crystal caves exist in fresh worlds");
        Space s=new Space();s.put(20,5,20,15);s.put(20,6,20,18);s.tick(false);check(s.get(20,6,20)==18&&s.tag(20,6,20).startsWith("coal_1_"),"coal heats without spreading");s.tick(false);s.tick(false);check(s.get(20,6,20)==21&&s.get(20,5,20)==15,"hidden ember-stone recipe retains lava");
        s=new Space();s.put(20,5,20,18);s.put(21,5,20,15);for(int i=0;i<3;i++)s.tick(false);check(s.get(20,5,20)==0,"side-heated coal burns out locally");
        s=new Space();s.put(20,5,20,15);s.put(20,6,20,18);s.tick(false);s.put(21,6,20,10);s.tick(false);check(s.get(20,6,20)==18&&!MaterialReactions.active(s.tag(20,6,20)),"water extinguishes coal without burning neighbors");
        s=new Space();s.put(20,5,20,15);s.put(21,5,20,10);s.tick(false);check(s.get(20,5,20)==19,"water plus lava creates basalt");
        s=new Space();s.put(20,5,20,8);s.put(21,5,20,20);s.tick(false);s.tick(false);s.tick(false);check(s.get(20,5,20)==22&&s.get(21,5,20)==0,"crystal glass atomically consumes the crystal");
        s=new Space();s.put(20,5,20,19);s.put(20,4,20,14);s.tick(false);check(s.get(20,5,20)==23&&s.get(20,4,20)==14,"iron catalyst polishes basalt");
        s=new Space();s.put(20,5,20,15);s.put(20,6,20,13);for(int i=0;i<4;i++)s.tick(true);s.put(20,5,20,0);s.put(20,5,20,15);s.tick(true);check(s.get(20,6,20)==13&&s.tag(20,6,20).startsWith("ore_1_"),"source nonce changes reset smelting even when original block type is restored");for(int i=0;i<4;i++)s.tick(true);check(s.get(20,6,20)==16,"online-capable ore smelting finishes exactly once");
        s=new Space();int[] pattern=Forge.pattern(30,6,30,1,true);for(int i=0;i<6;i++){int cell=pattern[i],x=cell%96,y=cell/(96*96),z=cell/96%96;s.put(x,y-1,z,15);s.put(x,y,z,Forge.PATTERN[i][2]);}for(int i=0;i<3;i++)s.tick(true);check(count(s.w,11)==1&&count(s.w,14)==0&&count(s.w,15)==6,"rotated legacy secret ritual commits six inputs as one group and preserves lava");
        s=new Space();s.w.set(20,5,20,15);s.w.set(20,6,20,18);check(s.tick(true).isEmpty(),"unmarked pre-existing constructions never react just because a client joins");
        s=new Space();s.put(20,5,20,15);s.put(21,5,20,10);s.w.x=20.5f;s.w.y=6.5f;s.w.z=20.5f;s.tick(false);check(s.get(20,5,20)==15,"basalt cannot enclose a player");
        World w=NatureTests.flat(false);w.progress.chapter=7;w.set(20,5,20,17);w.story=true;check(w.mine(20,5,20)&&w.progress.inventory[18]==1&&w.progress.inventory[17]==0,"coal ore yields one coal block");w.progress.inventory[5]=4;check(w.progress.craft(9).contains("Kohleblock")&&w.progress.inventory[18]==2,"coal recipe works in existing worlds");w.progress.inventory[11]=1;w.progress.inventory[8]=1;check(w.progress.craft(10).contains("Kristall")&&w.progress.inventory[20]==1,"existing worlds can obtain crystals without modifying their terrain");
        w=NatureTests.flat(false);w.progress.inventory[18]=1;w.set(20,5,20,15);check(w.place(20,6,20,18),"coal placement");NatureTests.advance(w,3.1f);check(MaterialReactions.active(w.materials.states.getOrDefault(World.index(20,6,20),"")),"offline timer starts while playing");World resumed=World.decode(w.encode());NatureTests.advance(resumed,6.2f);check(resumed.get(20,6,20)==21,"offline material timer survives save/load");
        check(resumed.materials.states.isEmpty()&&World.decode(resumed.encode()).get(20,6,20)==21,"completed offline reactions remove their persisted timer");
        Story story=new Story();story.chapter=7;story.inventory[23]=123;byte[] key=new byte[32];OnlineInventoryCipher cipher=new OnlineInventoryCipher(key);String encrypted=cipher.encrypt(story,"world","uid",9);check(encrypted.startsWith("2.")&&OnlineInventory.same(story,cipher.decrypt(encrypted,"world","uid",9)),"version-2 private inventories preserve every new material");
        VoxelMesher mesh=new VoxelMesher();w=NatureTests.flat(false);w.set(20,5,20,22);mesh.build(w,1,1,true);check(mesh.size>0&&mesh.values[3]>=37f/64&&mesh.values[3]<38f/64,"crystal glass has its own atlas slot in the transparent pass");
        System.out.println("PASS: "+checks+" material, generation, secret-reaction and compatibility assertions");
    }
}
