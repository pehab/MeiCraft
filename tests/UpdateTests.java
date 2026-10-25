package de.meiapps.meicraft;
import java.io.*;
import java.nio.file.*;
public final class UpdateTests {
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void rejected(Runnable fn){boolean bad=false;try{fn.run();}catch(IllegalArgumentException e){bad=true;}check(bad,"invalid metadata rejected");}
 public static void main(String[]args)throws Exception{
  File file=File.createTempFile("meicraft",".apk");try{Files.write(file.toPath(),new byte[]{1,2,3});String hash=UpdateInfo.digest(file);String url="https://github.com/pehab/MeiCraft/releases/download/v0.2.0/meiCraft-0.2.0.apk";
   UpdateInfo good=new UpdateInfo(2,"0.2.0",url,hash,3);check(good.newerThan(1)&&!good.newerThan(2),"only newer versions offered");check(good.matches(file),"size and sha verified");Files.write(file.toPath(),new byte[]{1,2,4});check(!good.matches(file),"tampered APK rejected");Files.write(file.toPath(),new byte[]{1,2});check(!good.matches(file),"truncated APK rejected");
   for(String bad:new String[]{url.replace("https:","http:"),url.replace("github.com","example.com"),url.replace("MeiCraft","MeiHotel"),url+"?download=1",url+"#x",url.replace("github.com","github.com:443"),url.replace("github.com","user@github.com"),url.replace("v0.2.0/","v0.3.0/"),url.replace("/MeiCraft/","/%4deiCraft/")})rejected(()->new UpdateInfo(2,"0.2.0",bad,hash,3));
   rejected(()->new UpdateInfo(0,"0.2.0",url,hash,3));rejected(()->new UpdateInfo(2,"0.2.0",url,"broken",3));rejected(()->new UpdateInfo(2,"0.2.0",url,hash,0));rejected(()->new UpdateInfo(2,"0.2.0",url,hash,51*1024*1024));rejected(()->new UpdateInfo(2,"../../0.2.0",url,hash,3));
  }finally{file.delete();}System.out.println("PASS: "+checks+" update assertions");
 }
}
