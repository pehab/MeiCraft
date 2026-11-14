package de.meiapps.meicraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Shared synchronization must not expose another member's private inventory.
 * AES-GCM authenticates the world, anonymous identity and inventory revision. */
final class OnlineInventoryCipher {
    private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
    OnlineInventoryCipher(byte[] key){if(key.length!=32)throw new IllegalArgumentException("Inventory key");this.key=new SecretKeySpec(key.clone(),"AES");}
    String encrypt(Story story,String world,String uid,long revision)throws IOException{
        if(!OnlineInventory.valid(story))throw new IOException("Ungültiges Online-Inventar.");
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4d434931);
            for(int item:story.inventory)out.writeInt(item);out.writeInt(story.chapter);out.writeInt(story.iron);out.writeInt(story.placed);out.writeInt(story.glassPlaced);out.writeInt(story.buckets);out.writeBoolean(story.sword);out.flush();
            byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));cipher.updateAAD((world+"|"+uid+"|"+revision).getBytes(StandardCharsets.UTF_8));
            Base64.Encoder base64=Base64.getUrlEncoder().withoutPadding();return "1."+base64.encodeToString(iv)+"."+base64.encodeToString(cipher.doFinal(bytes.toByteArray()));
        }catch(Exception e){throw new IOException("Online-Inventar konnte nicht geschützt werden.",e);}
    }
    Story decrypt(String payload,String world,String uid,long revision)throws IOException{
        try{
            if(!payload.matches("1[.][A-Za-z0-9_-]{16}[.][A-Za-z0-9_-]{146}"))throw new IOException();
            String[] parts=payload.split("[.]");Base64.Decoder base64=Base64.getUrlDecoder();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,base64.decode(parts[1])));cipher.updateAAD((world+"|"+uid+"|"+revision).getBytes(StandardCharsets.UTF_8));
            DataInputStream in=new DataInputStream(new ByteArrayInputStream(cipher.doFinal(base64.decode(parts[2]))));if(in.readInt()!=0x4d434931)throw new IOException();Story story=new Story();for(int i=0;i<story.inventory.length;i++)story.inventory[i]=in.readInt();story.chapter=in.readInt();story.iron=in.readInt();story.placed=in.readInt();story.glassPlaced=in.readInt();story.buckets=in.readInt();story.sword=in.readBoolean();if(in.read()!=-1||!OnlineInventory.valid(story))throw new IOException();return story;
        }catch(Exception e){throw new IOException("Online-Inventar konnte nicht entschlüsselt werden. Geräteanmeldung und App-Daten bitte erhalten.",e);}
    }
}
