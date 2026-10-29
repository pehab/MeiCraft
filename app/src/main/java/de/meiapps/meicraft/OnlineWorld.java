package de.meiapps.meicraft;

import java.security.SecureRandom;

/** Versioned online protocol; sector coordinates never alter the offline save format. */
public final class OnlineWorld {
    public static final int PROTOCOL=2;
    private static final String PREFIX="MEICRAFT-ONLINE:1:";
    public final String id, invitation;
    public OnlineWorld(String id,String invitation){
        if(id==null||!id.matches("[a-f0-9]{32}")||invitation==null||!invitation.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Ungültige Online-Einladung");
        this.id=id;this.invitation=invitation;
    }
    public static String randomId(){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder result=new StringBuilder(32);for(byte b:bytes){result.append("0123456789abcdef".charAt((b&255)>>>4));result.append("0123456789abcdef".charAt(b&15));}return result.toString();}
    public String encode(){return PREFIX+id+":"+invitation;}
    public static OnlineWorld decode(String text){
        if(text==null||!text.startsWith(PREFIX)||text.length()!=PREFIX.length()+65)throw new IllegalArgumentException("Das ist keine meiCraft-Online-Einladung.");
        String[] fields=text.substring(PREFIX.length()).split(":",-1);if(fields.length!=2)throw new IllegalArgumentException("Ungültige Einladung");return new OnlineWorld(fields[0],fields[1]);
    }
    public static String playerName(String value){String name=value==null?"":value.trim();if(name.isEmpty()||name.length()>24||name.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("Spielername: 1–24 Zeichen, ohne Steuerzeichen.");return name;}
    public static long sectorSeed(long seed,int sector){return seed+sector*104729L;}
    public static int neighbor(int sector,int side,int dx,int dz){
        if((side!=2&&side!=3)||sector<0||sector>=side*side||Math.abs(dx)+Math.abs(dz)!=1)return -1;
        int x=sector%side+dx,z=sector/side+dz;return x>=0&&z>=0&&x<side&&z<side?z*side+x:-1;
    }
    public static int sectorAt(float x,float z,int side){if(!Float.isFinite(x)||!Float.isFinite(z)||x<0||z<0||x>=side*World.W||z>=side*World.D)return -1;return (int)(z/World.D)*side+(int)(x/World.W);}
    public static int[] visibleSectors(float x,float z,int side,float radius){
        java.util.List<Integer> result=new java.util.ArrayList<>();
        for(int id=0;id<side*side;id++){float dx=Math.max(Math.max(id%side*World.W-x,0),x-(id%side+1)*World.W),dz=Math.max(Math.max(id/side*World.D-z,0),z-(id/side+1)*World.D);if(dx*dx+dz*dz<=radius*radius)result.add(id);}
        result.sort(java.util.Comparator.comparingDouble(id->{float dx=id%side*World.W+World.W/2f-x,dz=id/side*World.D+World.D/2f-z;return dx*dx+dz*dz;}));
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
    private static final int[] COLORS={0xff3986e8,0xff45b86a,0xffed9d32,0xffa466dc,0xffdf5c64};
    public static int colorIndex(String uid){return Math.floorMod(uid.hashCode(),5);}
    public static int color(String uid){return COLORS[colorIndex(uid)];}
    public static boolean validBlock(int x,int y,int z,int type){return World.inside(x,y,z)&&y>0&&type>=0&&type<World.NAMES.length;}
}
