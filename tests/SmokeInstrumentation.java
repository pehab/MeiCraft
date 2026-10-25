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

 void stickEvent(GameView game,int action,int[] ids,float[][] positions){
  runOnMainSync(()->{
   game.hud.layoutHud();android.view.MotionEvent.PointerProperties[] properties=new android.view.MotionEvent.PointerProperties[ids.length];android.view.MotionEvent.PointerCoords[] coordinates=new android.view.MotionEvent.PointerCoords[ids.length];
   for(int i=0;i<ids.length;i++){properties[i]=new android.view.MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=android.view.MotionEvent.TOOL_TYPE_FINGER;coordinates[i]=new android.view.MotionEvent.PointerCoords();coordinates[i].x=game.hud.offsetX+positions[i][0]*game.hud.scale;coordinates[i].y=game.hud.offsetY+positions[i][1]*game.hud.scale;coordinates[i].pressure=1;coordinates[i].size=1;}
   long now=SystemClock.uptimeMillis();android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,action,ids.length,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);game.hud.dispatchTouchEvent(event);event.recycle();
  });
 }
 void testSwipeControl(MainActivity activity,GameView game)throws Exception{
  float lx=game.hud.joyX,ly=game.hud.joyY,rx=game.hud.designW*.65f,ry=game.hud.designH*.4f;
  runOnMainSync(()->{activity.paused=false;synchronized(game.world){game.world.pitch=0;}});
  stickEvent(game,0,new int[]{31},new float[][]{{lx,ly-40}});
  stickEvent(game,5|(1<<8),new int[]{31,17},new float[][]{{lx,ly-40},{rx,ry}});
  stickEvent(game,2,new int[]{31,17},new float[][]{{lx,ly-40},{rx+30,ry+120}});
  Thread.sleep(250);check(game.forward>.5f,"movement joystick stays active while swiping");synchronized(game.world){check(game.world.pitch<-.3f,"original swipe control looks down");}
  stickEvent(game,6|(1<<8),new int[]{31,17},new float[][]{{lx,ly-40},{rx+30,ry+120}});check(game.lookId==-1&&game.forward>.5f,"releasing swipe leaves movement active");
  stickEvent(game,3,new int[]{31},new float[][]{{lx,ly-40}});check(game.forward==0&&game.strafe==0&&!game.mineHeld,"cancel clears original controls");
 }
 void clickText(String text)throws Exception{
  for(int attempt=0;attempt<40;attempt++){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(!node.isVisibleToUser()||bounds.isEmpty())continue;long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,bounds.centerX(),bounds.centerY(),0);sendPointerSync(down);down.recycle();android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+30,1,bounds.centerX(),bounds.centerY(),0);sendPointerSync(up);up.recycle();waitForIdleSync();return;}Thread.sleep(100);}
  throw new AssertionError("UI text not found: "+text);
 }
 void awaitSlot(MainActivity activity,int slot)throws Exception{for(int n=0;n<100&&(activity.slot!=slot||activity.world.story!=(slot==1)||activity.paused);n++)Thread.sleep(50);check(activity.slot==slot&&activity.world.story==(slot==1)&&!activity.paused,"selected world slot is active "+slot);}
 void testStory(MainActivity activity,GameView game)throws Exception{
  runOnMainSync(()->{activity.getPreferences(0).edit().putBoolean("storyIntroduced",true).apply();activity.switchMode(1);});awaitSlot(activity,1);
  check(!game.world.flying&&game.world.progress.tool()==0,"new story starts walking without tools");check(game.world.get(30,30,30)!=11,"story does not reuse creative blocks");
  float[] spawn=new float[3];CountDownLatch setup=new CountDownLatch(1);game.gl.queueEvent(()->{synchronized(game.world){World w=game.world;spawn[0]=w.x;spawn[1]=w.y;spawn[2]=w.z;for(int x=44;x<=52;x++)for(int z=40;z<=52;z++){w.set(x,32,z,3);for(int y=33;y<=38;y++)w.set(x,y,z,0);}w.x=48.5f;w.y=34.6f;w.z=48.5f;w.yaw=0;w.pitch=0;w.set(48,34,45,2);game.renderer.dirty(48,45);}setup.countDown();});check(setup.await(30,TimeUnit.SECONDS),"story mining fixture ready");
  tap(game,game.hud.mineX,game.hud.actionY);Thread.sleep(120);check(game.world.get(48,34,45)==2,"brief tap does not instantly mine story blocks");
  stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});boolean collected=false;for(int i=0;i<60&&!collected;i++){Thread.sleep(50);synchronized(game.world){collected=game.world.progress.inventory[2]==1;}}stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});check(collected&&game.world.get(48,34,45)==0,"holding mine gathers a finite resource");
  synchronized(game.world){game.world.set(48,34,45,3);}stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});Thread.sleep(600);stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});check(game.world.get(48,34,45)==3&&game.world.progress.inventory[3]==0,"hand cannot mine locked stone");
  tap(game,game.hud.placeX,game.hud.actionY);CountDownLatch placed=new CountDownLatch(1);game.gl.queueEvent(placed::countDown);check(placed.await(30,TimeUnit.SECONDS),"story placement processed");check(game.world.get(48,34,46)==2&&game.world.progress.inventory[2]==0,"placing consumes collected dirt");tap(game,game.hud.placeX,game.hud.actionY);Thread.sleep(150);check(game.world.get(48,34,47)==0&&game.world.progress.inventory[2]==0,"empty inventory cannot create blocks");
  runOnMainSync(()->{synchronized(game.world){game.world.progress.inventory[2]=8;}activity.chapter();});clickText("Auftrag abschließen");for(int i=0;i<30&&game.world.progress.chapter<1;i++)Thread.sleep(100);check(game.world.progress.tool()==1&&game.world.progress.inventory[2]==0,"story dialog trades dirt for first tool");clickText("Weiter sammeln");
  runOnMainSync(()->{synchronized(game.world){game.world.progress.chapter=2;game.world.progress.inventory[5]=1;}activity.storyMenu();});clickText("4 Bretter");for(int i=0;i<30&&game.world.progress.inventory[9]!=4;i++)Thread.sleep(100);check(game.world.progress.inventory[9]==4&&game.world.progress.inventory[5]==0,"workbench UI crafts finite planks");clickText("Weiterbauen");
  runOnMainSync(()->activity.switchMode(0));awaitSlot(activity,0);check(game.world.get(30,30,30)==11&&!game.world.story,"creative world preserved after story switch");World stored=World.decode(activity.slotFile(1).readFully());check(stored.story&&stored.progress.chapter==2&&stored.progress.inventory[9]==4,"story progress stored separately");
  runOnMainSync(()->activity.switchMode(1));awaitSlot(activity,1);check(game.world.progress.inventory[9]==4&&game.world.progress.tool()==2,"story restores inventory and tools");
  CountDownLatch clean=new CountDownLatch(1);game.gl.queueEvent(()->{synchronized(game.world){World w=game.world;for(int x=44;x<=52;x++)for(int z=40;z<=52;z++)for(int y=32;y<=38;y++)w.set(x,y,z,0);w.x=spawn[0];w.y=spawn[1];w.z=spawn[2];w.yaw=.55f;w.pitch=-.38f;w.selected=9;for(VoxelRenderer.Mesh mesh:game.renderer.meshes)mesh.dirty=true;}clean.countDown();});check(clean.await(30,TimeUnit.SECONDS),"story showcase ready");
 }
 void systemBack()throws Exception{
  try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK"))){byte[] data=new byte[512];while(in.read(data)!=-1){}}
  waitForIdleSync();
 }
 boolean focused(MainActivity activity){boolean[] value={false};runOnMainSync(()->value[0]=activity.hasWindowFocus());return value[0];}
 void orient(MainActivity activity,GameView game,int orientation,float width)throws Exception{
  runOnMainSync(()->activity.setRequestedOrientation(orientation));boolean[] ready={false};for(int attempt=0;attempt<40&&!ready[0];attempt++){Thread.sleep(100);runOnMainSync(()->{game.hud.layoutHud();ready[0]=game.hud.designW==width;});}check(ready[0],"responsive controls match requested orientation "+orientation);
 }
 void emitScreenshot(android.graphics.Bitmap screenshot,String name){
  ByteArrayOutputStream image=new ByteArrayOutputStream();screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);String encoded=android.util.Base64.encodeToString(image.toByteArray(),android.util.Base64.NO_WRAP);
  for(int start=0;start<encoded.length();start+=16000){Bundle chunk=new Bundle();chunk.putString(name+"Chunk",encoded.substring(start,Math.min(encoded.length(),start+16000)));sendStatus(1,chunk);}
 }
 android.graphics.Bitmap smallScreenshot(){
  android.graphics.Bitmap source=getUiAutomation().takeScreenshot();if(source==null)return null;int edge=Math.max(source.getWidth(),source.getHeight());if(edge<=640)return source;android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(source,Math.max(1,source.getWidth()*640/edge),Math.max(1,source.getHeight()*640/edge),true);source.recycle();return scaled;
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
   android.content.pm.PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(candidate.getAbsolutePath(),0);
   UpdateInfo info=new UpdateInfo((int)UpdateController.versionCode(archive),archive.versionName,"https://github.com/pehab/MeiCraft/releases/download/v"+archive.versionName+"/meiCraft-"+archive.versionName+".apk",UpdateInfo.digest(candidate),candidate.length());
   activity.updates.verify(info,candidate);check(true,"valid newer APK identity, hash and signing certificate verified");
   boolean rejected=false;try{activity.updates.verify(new UpdateInfo(info.code+1,info.name,info.url,info.sha256,info.size),candidate);}catch(Exception e){rejected=true;}check(rejected,"wrong APK version rejected");
   android.net.Uri uri=android.net.Uri.parse("content://de.meiapps.meicraft.updates/update.apk");try(android.os.ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(uri,"r")){check(fd.getStatSize()==info.size,"installer URI grants exact APK bytes");}
   rejected=false;try{context.getContentResolver().openFileDescriptor(uri,"w");}catch(Exception e){rejected=true;}check(rejected,"installer URI is read only");
   synchronized(game.world){game.world.set(30,30,30,11);game.world.selected=12;}
   activity.updates.prefs.edit().putBoolean("automatic",false).commit();runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
   result.putString("summary","PASS: "+checks+" update seed assertions");finish(Activity.RESULT_OK,result);return;
  }
  if("verify".equals(phase)){check(game.world.get(30,30,30)==11&&game.world.selected==12,"install -r preserves world and selected material");check(!activity.updates.prefs.getBoolean("automatic",true),"update preference persists across APK replacement");}
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,960);
  testSwipeControl(activity,game);
  runOnMainSync(()->activity.paused=false);for(int attempt=0;attempt<30&&!focused(activity);attempt++)Thread.sleep(100);check(focused(activity),"game window has input focus before system back");systemBack();
  for(int attempt=0;attempt<30&&!activity.paused;attempt++)Thread.sleep(100);check(activity.paused,"system back opens game menu");for(int attempt=0;attempt<30&&focused(activity);attempt++)Thread.sleep(100);check(!focused(activity),"menu window receives focus before dismissal");
  systemBack();for(int attempt=0;attempt<30&&(activity.paused||!focused(activity));attempt++)Thread.sleep(100);check(!activity.paused&&focused(activity),"back dismisses menu without leaving game");
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
  runOnMainSync(()->activity.paused=false);tap(game,game.hud.mineX,game.hud.actionY);runOnMainSync(()->activity.paused=true);
  CountDownLatch editDone=new CountDownLatch(1);game.gl.queueEvent(()->{synchronized(game.world){try{check(game.world.get(48,34,45)==0,"break removes targeted block");game.world.set(48,34,44,3);}catch(Throwable t){failure[0]=t;}}editDone.countDown();});check(editDone.await(30,TimeUnit.SECONDS),"break queue completed");if(failure[0]!=null)throw new AssertionError(failure[0]);
  runOnMainSync(()->activity.paused=false);tap(game,game.hud.placeX,game.hud.actionY);runOnMainSync(()->activity.paused=true);CountDownLatch placeDone=new CountDownLatch(1);game.gl.queueEvent(()->{try{synchronized(game.world){check(game.world.get(48,34,45)==9,"place adds selected block adjacent to target");game.renderer.dirty(48,45);}}catch(Throwable t){failure[0]=t;}finally{placeDone.countDown();}});check(placeDone.await(30,TimeUnit.SECONDS),"place queue completed");if(failure[0]!=null)throw new AssertionError(failure[0]);
  runOnMainSync(()->activity.paused=false);tap(game,game.hud.barX+248,game.hud.barY+25);runOnMainSync(()->activity.paused=true);check(game.world.selected==9,"hotbar touch selects material");
  runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);World saved=World.decode(activity.file.readFully());check(saved.get(48,34,45)==9,"edited block written to disk");check(saved.selected==9,"selected material written to disk");
  check(java.util.Arrays.asList(context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions).contains("android.permission.INTERNET"),"update permission present");
  runOnMainSync(()->{synchronized(game.world){World w=game.world;w.yaw=.55f;w.pitch=-.38f;w.y=26;w.x=48.5f;w.z=48.5f;}activity.paused=false;});
  testStory(activity,game);
  Thread.sleep(1500);
  android.graphics.Bitmap screenshot=smallScreenshot();
  if(screenshot!=null){try(FileOutputStream out=context.openFileOutput("smoke.png",0)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}emitScreenshot(screenshot,"screenshot");screenshot.recycle();}
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,540);testSwipeControl(activity,game);
  android.graphics.Bitmap portrait=smallScreenshot();if(portrait!=null){emitScreenshot(portrait,"portraitScreenshot");portrait.recycle();}
  result.putString("summary","PASS: "+checks+" Android assertions");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));try{android.graphics.Bitmap failure=smallScreenshot();if(failure!=null){emitScreenshot(failure,"failureScreenshot");failure.recycle();}android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)result.putString("activeWindowPackage",String.valueOf(root.getPackageName()));}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}}
}
