package de.meiapps.meicraft;
import java.io.*;
import java.net.URI;
import java.security.*;

/** Strict metadata boundary between a release manifest and an installable APK. */
public final class UpdateInfo {
 public final int code; public final String name,url,sha256;public final long size;
 public UpdateInfo(int code,String name,String url,String sha256,long size){
  if(code<=0||name==null||!name.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?")||name.length()>40||sha256==null||!sha256.matches("[0-9a-f]{64}")||size<=0||size>50*1024*1024)throw new IllegalArgumentException("Invalid update metadata");
  URI uri;try{uri=URI.create(url);}catch(Exception e){throw new IllegalArgumentException("Invalid APK URL",e);}
  if(!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!("/pehab/MeiCraft/releases/download/v"+name+"/meiCraft-"+name+".apk").equals(uri.getRawPath()))throw new IllegalArgumentException("Untrusted APK URL");
  this.code=code;this.name=name;this.url=url;this.sha256=sha256;this.size=size;
 }
 public boolean newerThan(long installed){return code>installed;}
 public boolean matches(File file)throws IOException{return file.length()==size&&sha256.equals(digest(file));}
 public static String digest(File file)throws IOException{
  MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
  try(InputStream in=new FileInputStream(file)){byte[] data=new byte[16384];int n;while((n=in.read(data))!=-1)digest.update(data,0,n);}
  StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
 }
}
