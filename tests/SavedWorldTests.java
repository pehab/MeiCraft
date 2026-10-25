package de.meiapps.meicraft;

import java.io.IOException;
import java.util.Arrays;

public final class SavedWorldTests {
    static int count;
    static void check(boolean condition, String message) { count++; if (!condition) throw new AssertionError(message); }
    static void rejects(String id, String name, int mode) {
        boolean rejected=false;
        try { new SavedWorld(id,name,mode); } catch(IllegalArgumentException e) { rejected=true; }
        check(rejected,"invalid world descriptor rejected");
    }
    public static void main(String[] args) throws Exception {
        SavedWorld first=SavedWorld.create("  Große Burg 🌍  ",0),second=SavedWorld.create("Große Burg 🌍",0);
        check(!first.id.equals(second.id),"even identical names have independent save identities");
        check(first.name.equals("Große Burg 🌍"),"display names retain Unicode and trim surrounding spaces");
        check(first.dataPath().startsWith("worlds/")&&!first.dataPath().contains(first.name),"display names never become paths");
        check(SavedWorld.legacy(0).dataPath().equals("world.mcworld"),"old creative save is retained in place");
        check(SavedWorld.legacy(1).dataPath().equals("story.mcworld"),"old Story save is retained in place");
        SavedWorld restored=SavedWorld.decode(first.encode(),first.id);
        check(restored.name.equals(first.name)&&restored.mode==0&&restored.id.equals(first.id),"world identity and name survive metadata reload");
        SavedWorld renamed=new SavedWorld(first.id,"Burg am See",first.mode);
        check(renamed.dataPath().equals(first.dataPath()),"renaming keeps the same save path");
        for(String id:new String[]{"../world","/tmp/world","worlds/test","",null,"UPPERCASE","creative/../story"})rejects(id,"Burg",0);
        rejects(first.id,"",0);rejects(first.id,"   ",0);rejects(first.id,"x".repeat(49),0);rejects(first.id,"A\nB",0);
        rejects(first.id,"Burg",2);rejects("creative","Burg",1);rejects("story","Burg",0);
        boolean rejected=false;try{SavedWorld.decode(first.encode(),second.id);}catch(IOException e){rejected=true;}check(rejected,"metadata cannot impersonate another world ID");
        byte[] truncated=Arrays.copyOf(first.encode(),first.encode().length-1);rejected=false;try{SavedWorld.decode(truncated,first.id);}catch(IOException e){rejected=true;}check(rejected,"incomplete metadata is rejected");
        byte[] unsupported=first.encode();unsupported[3]=2;rejected=false;try{SavedWorld.decode(unsupported,first.id);}catch(IOException e){rejected=true;}check(rejected,"unknown metadata format is rejected");
        System.out.println("PASS: "+count+" saved world assertions");
    }
}
