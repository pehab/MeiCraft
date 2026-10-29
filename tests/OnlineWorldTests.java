package de.meiapps.meicraft;

public final class OnlineWorldTests {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        OnlineWorld invite=new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId());
        check(OnlineWorld.decode(invite.encode()).encode().equals(invite.encode()),"Invitation round trip");
        com.google.zxing.common.BitMatrix qr=QrCodec.encode(invite.encode());int edge=qr.getWidth()*6;byte[] luma=new byte[edge*edge];
        for(int y=0;y<edge;y++)for(int x=0;x<edge;x++)luma[y*edge+x]=(byte)(qr.get(x/6,y/6)?0:255);
        check(invite.encode().equals(QrCodec.decode(luma,edge,edge)),"Online invitation is readable through the actual camera QR codec");
        for(String invalid:new String[]{"",invite.encode()+":extra",invite.encode().replace(":1:",":2:"),"https://example.com",invite.encode().substring(0,50)}){
            try{OnlineWorld.decode(invalid);throw new AssertionError("Accepted invalid invitation");}catch(IllegalArgumentException expected){}
        }
        for(int side:new int[]{2,3})for(int sector=0;sector<side*side;sector++)for(int[] delta:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int neighbor=OnlineWorld.neighbor(sector,side,delta[0],delta[1]);if(neighbor>=0)check(OnlineWorld.neighbor(neighbor,side,-delta[0],-delta[1])==sector,"Reversible region crossing");}
        check(OnlineWorld.neighbor(0,2,-1,0)==-1,"No wrapping at west edge");
        check(OnlineWorld.neighbor(3,2,1,0)==-1,"No wrapping at east edge");
        check(OnlineWorld.validBlock(0,1,95,16),"Valid boundary block");
        check(!OnlineWorld.validBlock(0,0,0,1)&&!OnlineWorld.validBlock(96,3,2,1)&&!OnlineWorld.validBlock(1,3,2,17),"Reject foundation, bounds, unknown materials");
        for(String invalid:new String[]{"","  ","a\nb","0123456789012345678901234"})try{OnlineWorld.playerName(invalid);throw new AssertionError("Accepted invalid name");}catch(IllegalArgumentException expected){}
        World a=new World(OnlineWorld.sectorSeed(123,2),false,false),b=new World(OnlineWorld.sectorSeed(123,2),false,false);
        check(java.util.Arrays.equals(a.blocks,b.blocks),"Same seed on both devices");
        StringBuilder hash=new StringBuilder();for(byte value:java.security.MessageDigest.getInstance("SHA-256").digest(a.blocks))hash.append(String.format("%02x",value&255));
        check(hash.toString().equals("797e4e5dd633c184cdd2482f08cab5891f471ce73c5b03a4b95740c5c65fd1f4"),"Protocol 1 terrain must remain compatible with existing online worlds");
        a.online=true;
        a.set(10,30,10,15);a.set(11,30,10,12);a.nature.source(10,30,10);
        byte[] staticBlocks=a.blocks.clone();float staticTime=a.survival.time;
        for(int i=0;i<1200;i++){a.nature.tick(a,.1f);a.survival.tick(a,.1f);}
        check(java.util.Arrays.equals(staticBlocks,a.blocks),"Online terrain stays static: no flow, growth or reactions");
        check(a.survival.time==staticTime&&a.survival.mobs.isEmpty(),"No online day/night cycle or enemies");
        check(!World.decode(a.encode()).online,"Online session never leaks into offline save format");
        System.out.println("Online protocol, region boundaries and offline format checks passed");
    }
}
