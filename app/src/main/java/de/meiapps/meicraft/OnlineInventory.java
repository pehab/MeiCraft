package de.meiapps.meicraft;

import java.util.Arrays;

/** Per-player resource transactions. Copies are committed before touching the visible game. */
public final class OnlineInventory {
    private OnlineInventory(){}
    public static Story copy(Story source){Story next=new Story();apply(next,source);return next;}
    public static void apply(Story target,Story source){System.arraycopy(source.inventory,0,target.inventory,0,target.inventory.length);target.chapter=source.chapter;target.iron=source.iron;target.placed=source.placed;target.glassPlaced=source.glassPlaced;target.buckets=source.buckets;target.sword=source.sword;}
    public static boolean same(Story a,Story b){return Arrays.equals(a.inventory,b.inventory)&&a.chapter==b.chapter&&a.iron==b.iron&&a.placed==b.placed&&a.glassPlaced==b.glassPlaced&&a.buckets==b.buckets&&a.sword==b.sword;}
    public static boolean valid(Story p){if(p.chapter<0||p.chapter>7||!count(p.iron)||!count(p.placed)||!count(p.glassPlaced)||!count(p.buckets))return false;for(int n:p.inventory)if(!count(n))return false;return p.inventory[0]==0;}
    private static boolean count(int n){return n>=0&&n<=1000000;}
    /** Returns an error, or an empty string after modifying the isolated candidate. */
    public static String harvest(Story next,int block){
        if(block<=0||block>=World.NAMES.length)return "Hier ist kein Block.";
        if(Nature.liquid(block)){
            if(next.buckets<=0)return "Stelle zuerst einen leeren Eiseneimer her.";
            if(!next.add(block,1))return "Dein Vorrat ist voll.";
            next.buckets--;return "";
        }
        if(!next.canMine(block))return "Dafür brauchst du: "+Story.TOOLS[Story.needed(block)];
        if(block==16){if(next.iron>=1000000)return "Dein Vorrat ist voll.";next.iron++;}
        else if(!next.add(block==1?2:block,1))return "Dein Vorrat ist voll.";
        return "";
    }
    public static String place(Story next,int block){
        if(block<=0||block>=World.NAMES.length||next.inventory[block]<=0)return "Erst Rohstoffe sammeln oder herstellen.";
        if(Nature.liquid(block)&&next.buckets>=1000000)return "Dein Vorrat ist voll.";
        next.inventory[block]--;if(Nature.liquid(block))next.buckets++;
        next.placed=Math.min(1000000,next.placed+1);if(block==8)next.glassPlaced=Math.min(1000000,next.glassPlaced+1);
        return "";
    }
    public static void install(World target,World tile,int sector,int side){
        int ox=sector%side*World.W,oz=sector/side*World.D;
        for(int y=0;y<World.H;y++)for(int z=0;z<World.D;z++)System.arraycopy(tile.blocks,World.index(0,y,z),target.blocks,target.idx(ox,y,oz+z),World.W);
        target.revision++;
    }
}
