package de.meiapps.meicraft;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

final class UpdateController {
 static final String MANIFEST="https://github.com/pehab/MeiCraft/releases/latest/download/meiCraft-update.json";
 final MainActivity activity;final SharedPreferences prefs;final ExecutorService network=Executors.newSingleThreadExecutor();
 boolean resumed,destroyed,busy;Runnable pending;
 UpdateController(MainActivity activity){this.activity=activity;prefs=activity.getSharedPreferences("updates",0);}
 void onResume(){resumed=true;if(pending!=null){Runnable task=pending;pending=null;task.run();}
  if(prefs.getBoolean("waitingPermission",false)){prefs.edit().putBoolean("waitingPermission",false).apply();if(activity.getPackageManager().canRequestPackageInstalls()){try{install(parse(prefs.getString("candidate","")));}catch(Exception e){activity.toast("Bitte das Update erneut herunterladen");}}else activity.toast("Installation wurde nicht freigegeben");}
  if(prefs.getBoolean("automatic",true)&&System.currentTimeMillis()-prefs.getLong("lastCheck",0)>86400000L)check(false);
 }
 void onPause(){resumed=false;}
 void onDestroy(){destroyed=true;pending=null;network.shutdownNow();}
 void ui(Runnable task){activity.runOnUiThread(()->{if(destroyed)return;if(resumed)task.run();else pending=task;});}
 void settings(){activity.paused=true;boolean automatic=prefs.getBoolean("automatic",true);new AlertDialog.Builder(activity).setTitle("Updates · meiCraft "+versionName()).setItems(new String[]{"Jetzt nach Updates suchen","Automatische Prüfung: "+(automatic?"an":"aus")},(d,item)->{if(item==0)check(true);else{prefs.edit().putBoolean("automatic",!automatic).apply();activity.toast(!automatic?"Tägliche Update-Prüfung eingeschaltet":"Automatische Prüfung ausgeschaltet");}}).setOnDismissListener(d->activity.paused=false).show();}
 String versionName(){try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}catch(Exception e){return "?";}}
 long installedCode(){try{PackageInfo p=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);return versionCode(p);}catch(Exception e){return Long.MAX_VALUE;}}
 static long versionCode(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():legacyVersionCode(p);}
 // Android 8 uses the original version-code field; newer devices use the long API.
 @SuppressWarnings("deprecation") private static long legacyVersionCode(PackageInfo p){return p.versionCode;}
 static UpdateInfo parse(String data)throws JSONException{JSONObject j=new JSONObject(data);if(j.getInt("schema")!=1)throw new JSONException("Unknown schema");return new UpdateInfo(j.getInt("versionCode"),j.getString("versionName"),j.getString("url"),j.getString("sha256"),j.getLong("size"));}
 static String encode(UpdateInfo info){try{JSONObject j=new JSONObject();j.put("schema",1);j.put("versionCode",info.code);j.put("versionName",info.name);j.put("url",info.url);j.put("sha256",info.sha256);j.put("size",info.size);return j.toString();}catch(JSONException e){throw new AssertionError(e);}}
 void check(boolean manual){if(busy){if(manual)activity.toast("Eine Update-Prüfung oder ein Download läuft bereits");return;}busy=true;prefs.edit().putLong("lastCheck",System.currentTimeMillis()).apply();if(manual)activity.toast("Suche nach Updates …");
  network.execute(()->{try{HttpURLConnection connection=open(new URL(MANIFEST));String text;try(InputStream in=connection.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>65536)throw new IOException("Metadata too large");bytes.write(buffer,0,n);}text=bytes.toString("UTF-8");}finally{connection.disconnect();}UpdateInfo info=parse(text);ui(()->{busy=false;if(info.newerThan(installedCode()))offer(info);else if(manual)activity.toast("Du hast die aktuelle Version");});}
   catch(Exception e){ui(()->{busy=false;if(manual)activity.toast("Updates gerade nicht erreichbar. Offline weiterspielen ist möglich.");});}
  });
 }
 void offer(UpdateInfo info){activity.paused=true;new AlertDialog.Builder(activity).setTitle("Update "+info.name+" verfügbar").setMessage("Die neue APK kann jetzt heruntergeladen werden. Dein Spielstand bleibt bei der Aktualisierung erhalten. Android bestätigt anschließend die Installation.").setPositiveButton("Herunterladen",(d,w)->download(info)).setNegativeButton("Später",null).setOnDismissListener(d->activity.paused=false).show();}
 static HttpURLConnection open(URL url)throws IOException{
  for(int redirects=0;redirects<7;redirects++){
   String host=url.getHost();if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||url.getPort()!=-1||!("github.com".equals(host)||host.endsWith(".githubusercontent.com")))throw new IOException("Untrusted redirect");
   HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","meiCraft-Android");int status=c.getResponseCode();
   if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Missing redirect");url=new URL(url,location);continue;}
   if(status!=200){c.disconnect();throw new IOException("HTTP "+status);}return c;
  }throw new IOException("Too many redirects");
 }
 void download(UpdateInfo info){if(busy)return;busy=true;activity.toast("Update wird heruntergeladen …");network.execute(()->{
  File part=new File(activity.getCacheDir(),"update.part"),apk=new File(activity.getCacheDir(),"update.apk");
  try{HttpURLConnection connection=open(new URL(info.url));try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[16384];int n;long size=0;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Interrupted");size+=n;if(size>info.size)throw new IOException("APK too large");out.write(buffer,0,n);}out.getFD().sync();}finally{connection.disconnect();}
   verify(info,part);if(!part.renameTo(apk))throw new IOException("Cannot store APK");prefs.edit().putString("candidate",encode(info)).commit();ui(()->{busy=false;ready(info);});
  }catch(Exception e){part.delete();ui(()->{busy=false;activity.toast("Das Update konnte nicht sicher geladen werden. Bitte später erneut versuchen.");});}
 });}
 @SuppressWarnings("deprecation") void verify(UpdateInfo info,File apk)throws Exception{
  if(!info.matches(apk))throw new IOException("Checksum mismatch");PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
  PackageInfo downloaded=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(activity.getPackageName(),flags);
  if(downloaded==null||!activity.getPackageName().equals(downloaded.packageName)||versionCode(downloaded)!=info.code||!info.name.equals(downloaded.versionName)||!info.newerThan(versionCode(current)))throw new IOException("Package identity mismatch");
  if(!signatures(downloaded).equals(signatures(current))||signatures(current).isEmpty())throw new IOException("Signing key mismatch");
 }
 @SuppressWarnings("deprecation") static Set<String> signatures(PackageInfo info){Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;Set<String> set=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)set.add(signature.toCharsString());return set;}
 void ready(UpdateInfo info){activity.paused=true;new AlertDialog.Builder(activity).setTitle("Update bereit").setMessage("Die APK wurde geprüft. Version "+info.name+" jetzt installieren?").setPositiveButton("Installieren",(d,w)->install(info)).setNegativeButton("Später",null).setOnDismissListener(d->activity.paused=false).show();}
 void install(UpdateInfo info){if(busy)return;busy=true;network.execute(()->{try{verify(info,new File(activity.getCacheDir(),"update.apk"));ui(()->{busy=false;try{
   if(!activity.getPackageManager().canRequestPackageInstalls()){prefs.edit().putBoolean("waitingPermission",true).commit();activity.toast("Bitte Updates aus meiCraft einmalig erlauben");activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;}
   Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);
  }catch(Exception e){prefs.edit().putBoolean("waitingPermission",false).apply();activity.toast("Android konnte die Installation nicht öffnen");}});}catch(Exception e){ui(()->{busy=false;activity.toast("Bitte das Update erneut herunterladen");});}});}
}
