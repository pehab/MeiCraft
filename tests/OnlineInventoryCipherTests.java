package de.meiapps.meicraft;

import java.util.Arrays;

public final class OnlineInventoryCipherTests {
    static int checks;
    static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
    static void denied(OnlineInventoryCipher cipher,String payload,String world,String uid,long revision)throws Exception{boolean failed=false;try{cipher.decrypt(payload,world,uid,revision);}catch(java.io.IOException expected){failed=true;}check(failed,"Authenticated ciphertext rejects wrong key, identity, world, revision or tampering");}
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32];Arrays.fill(key,(byte)17);OnlineInventoryCipher cipher=new OnlineInventoryCipher(key);Story story=new Story();story.chapter=7;story.iron=123;story.placed=456;story.glassPlaced=9;story.buckets=10;story.sword=true;for(int i=1;i<17;i++)story.inventory[i]=i*999;
        String payload=cipher.encrypt(story,"world-a","player-a",19);check(payload.length()==165,"Fixed small encrypted payload");check(OnlineInventory.same(story,cipher.decrypt(payload,"world-a","player-a",19)),"Every resource, tool and progress field round-trips");
        check(!cipher.encrypt(story,"world-a","player-a",19).equals(payload),"Each encryption has a fresh GCM nonce");check(OnlineInventory.same(story,new OnlineInventoryCipher(key).decrypt(payload,"world-a","player-a",19)),"Persisted key can reopen an inventory after restart");
        denied(cipher,payload,"world-b","player-a",19);denied(cipher,payload,"world-a","player-b",19);denied(cipher,payload,"world-a","player-a",20);denied(new OnlineInventoryCipher(new byte[32]),payload,"world-a","player-a",19);
        for(int position:new int[]{2,20,80,160}){char[] changed=payload.toCharArray();changed[position]=changed[position]=='A'?'B':'A';denied(cipher,new String(changed),"world-a","player-a",19);}
        denied(cipher,payload.substring(1),"world-a","player-a",19);denied(cipher,payload+"A","world-a","player-a",19);
        Story invalid=new Story();invalid.inventory[2]=-1;boolean failed=false;try{cipher.encrypt(invalid,"world-a","player-a",0);}catch(java.io.IOException expected){failed=true;}check(failed,"Malformed inventories never enter the opaque shared field");
        System.out.println("PASS: "+checks+" private inventory encryption and authentication contracts");
    }
}
