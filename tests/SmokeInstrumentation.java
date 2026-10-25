package de.meiapps.meicraft;

import android.app.*;
import android.content.*;
import android.os.*;
import android.opengl.GLES20;
import java.io.*;
import java.util.concurrent.*;

public final class SmokeInstrumentation extends Instrumentation {
 int checks;String phase;
 void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 void tap(GameView game,float x,float y){
  float[] point=new float[2];runOnMainSync(()->{game.hud.layoutHud();int[] location=new int[2];game.hud.getLocationOnScreen(location);point[0]=location[0]+game.hud.offsetX+x*game.hud.scale;point[1]=location[1]+game.hud.offsetY+y*game.hud.scale;});
  long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,point[0],point[1],0);sendPointerSync(down);down.recycle();android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+30,1,point[0],point[1],0);sendPointerSync(up);up.recycle();waitForIdleSync();
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);phase=args==null?"full":args.getString("phase","full");start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context context=getTargetContext();context.getSharedPreferences("MainActivity",0).edit().putBoolean("introduced",true).commit();
  MainActivity activity=(MainActivity)startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
  Thread.sleep(1800);
  GameView game=activity.game;
  if("seed".equals(phase)){
   runOnMainSync(()->activity.paused=true);
   File candidate=new File(context.getCacheDir(),"update.apk");try(InputStream in=getContext().getAssets().open("current.apk");FileOutputStream out=new FileOutputStream(candidate)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
   UpdateInfo info=new UpdateInfo(1,"0.1.0","https://github.com/pehab/MeiCraft/releases/download/v0.1.0/meiCraft-0.1.0.apk",UpdateInfo.digest(candidate),candidate.length());
   activity.updates.verify(info,candidate);check(true,"valid newer APK identity, hash and signing certificate verified");
   boolean rejected=false;try{activity.updates.verify(new UpdateInfo(2,"0.1.0",info.url,info.sha256,info.size),candidate);}catch(Exception e){rejected=true;}check(rejected,"wrong APK version rejected");
   android.net.Uri uri=android.net.Uri.parse("content://de.meiapps.meicraft.updates/update.apk");try(android.os.ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r")){check(fd.getStatSize()==info.size,"installer URI grants exact APK bytes");}
   rejected=false;try{context.getContentResolver().openFileDescriptor(uri,"w");}catch(Exception e){rejected=true;}check(rejected,"installer URI is read only");
   synchronized(game.world){game.world.set(30,30,30,11);game.world.selected=12;}
   activity.updates.prefs.edit().putBoolean("automatic",false).commit();runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
   result.putString("summary","PASS: "+checks+" update seed assertions");finish(Activity.RESULT_OK,result);return;
  }
  if("verify".equals(phase)){check(game.world.get(30,30,30)==11&&game.world.selected==12,"install -r preserves world and selected material");check(!activity.updates.prefs.getBoolean("automatic",true),"update preference persists across APK replacement");}
  runOnMainSync(()->activity.paused=true);
  CountDownLatch done=new CountDownLatch(1);Throwable[] failure=new Throwable[1];
  game.gl.queueEvent(()->{try{
   World w=game.world;
   check(GLES20.glGetString(GLES20.GL_VERSION)!=null,"OpenGL context exists");check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"rendering has no GL errors");
   synchronized(w){
    w.x=48.5f;w.y=34.5f;w.z=48.5f;w.yaw=0;w.pitch=0;w.flying=true;
    for(int x=44;x<=52;x++)for(int z=40;z<=52;z++)for(int y=31;y<=38;y++)w.set(x,y,z,0);
    w.set(48,34,45,7);game.renderer.dirty(48,45);
    check(w.ray(6)!=null,"crosshair reaches prepared block");

    game.forward=1;game.tick(.25f);game.forward=0;
    check(w.z<48.5f,"forward movement advances camera");
    w.z=48.5f;w.selected=9;
   }
  }catch(Throwable t){failure[0]=t;}finally{done.countDown();}});
  check(done.await(60,TimeUnit.SECONDS),"GL thread completed");if(failure[0]!=null)throw new AssertionError("GL smoke",failure[0]);
  runOnMainSync(()->activity.paused=false);tap(game,826,300);runOnMainSync(()->activity.paused=true);
  CountDownLatch editDone=new CountDownLatch(1);game.gl.queueEvent(()->{synchronized(game.world){try{check(game.world.get(48,34,45)==0,"break removes targeted block");game.world.set(48,34,44,3);}catch(Throwable t){failure[0]=t;}}editDone.countDown();});check(editDone.await(30,TimeUnit.SECONDS),"break queue completed");if(failure[0]!=null)throw new AssertionError(failure[0]);
  runOnMainSync(()->activity.paused=false);tap(game,897,300);runOnMainSync(()->activity.paused=true);CountDownLatch placeDone=new CountDownLatch(1);game.gl.queueEvent(()->{try{synchronized(game.world){check(game.world.get(48,34,45)==9,"place adds selected block adjacent to target");game.renderer.dirty(48,45);}}catch(Throwable t){failure[0]=t;}finally{placeDone.countDown();}});check(placeDone.await(30,TimeUnit.SECONDS),"place queue completed");if(failure[0]!=null)throw new AssertionError(failure[0]);
  runOnMainSync(()->activity.paused=false);tap(game,508,497);runOnMainSync(()->activity.paused=true);check(game.world.selected==9,"hotbar touch selects material");
  runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);World saved=World.decode(activity.file.readFully());check(saved.get(48,34,45)==9,"edited block written to disk");check(saved.selected==9,"selected material written to disk");
  check(java.util.Arrays.asList(context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions).contains("android.permission.INTERNET"),"update permission present");
  runOnMainSync(()->{synchronized(game.world){World w=game.world;w.yaw=.55f;w.pitch=-.38f;w.y=26;w.x=48.5f;w.z=48.5f;}activity.paused=false;});
  Thread.sleep(1500);
  android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
  if(screenshot!=null){try(FileOutputStream out=context.openFileOutput("smoke.png",0)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}ByteArrayOutputStream image=new ByteArrayOutputStream();screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);result.putString("screenshot",android.util.Base64.encodeToString(image.toByteArray(),android.util.Base64.NO_WRAP));screenshot.recycle();}
  result.putString("summary","PASS: "+checks+" Android assertions");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
