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
  for(int attempt=0;attempt<40;attempt++){
   android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   java.util.List<android.view.accessibility.AccessibilityNodeInfo> matches=root==null?java.util.Collections.emptyList():root.findAccessibilityNodeInfosByText(text);
   matches.sort(java.util.Comparator.comparingInt(node->text.contentEquals(node.getText()==null?"":node.getText())?0:1));
   for(android.view.accessibility.AccessibilityNodeInfo node:matches){
    android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(!node.isVisibleToUser()||bounds.isEmpty())continue;
    // Native dialog rows expose a semantic click, independent of window animations.
    if(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
    long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,bounds.centerX(),bounds.centerY(),0);sendPointerSync(down);down.recycle();android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+30,1,bounds.centerX(),bounds.centerY(),0);sendPointerSync(up);up.recycle();waitForIdleSync();return;
   }
   if(attempt%4==3)scrollForward(root);Thread.sleep(100);
  }
  throw new AssertionError("UI text not found: "+text);
 }
 boolean scrollForward(android.view.accessibility.AccessibilityNodeInfo node){
  if(node==null)return false;
  if(node.isScrollable()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
  for(int i=0;i<node.getChildCount();i++)if(scrollForward(node.getChild(i)))return true;return false;
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
 void onGl(GameView game,Runnable task)throws Exception{
  CountDownLatch done=new CountDownLatch(1);Throwable[] error=new Throwable[1];
  game.gl.queueEvent(()->{try{synchronized(game.world){task.run();}}catch(Throwable t){error[0]=t;}finally{done.countDown();}});
  check(done.await(30,TimeUnit.SECONDS),"survival GL operation completed");if(error[0]!=null)throw new AssertionError(error[0]);
 }
 void testSurvival(MainActivity activity,GameView game)throws Exception{
  World original=game.world;
  runOnMainSync(()->{activity.paused=true;activity.handler.removeCallbacks(activity.autosave);});
  try{
   onGl(game,()->{
    World w=new World(5150,true);java.util.Arrays.fill(w.blocks,(byte)0);w.nature.springs.clear();w.nature.active.clear();java.util.Arrays.fill(w.nature.levels,(byte)0);
    for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)w.blocks[World.index(x,32,z)]=3;
    w.x=48.5f;w.y=34.62f;w.z=48.5f;w.yaw=0;w.pitch=-.32f;w.survival.time=510;w.progress.chapter=5;w.progress.sword=true;
    w.survival.mobs.add(new Survival.Mob(48.5f,34.62f,46.5f));game.replace(w);activity.world=w;
   });
   runOnMainSync(()->activity.paused=false);
   stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});for(int i=0;i<120;i++){Thread.sleep(50);boolean gone;synchronized(game.world){gone=game.world.survival.mobs.isEmpty();}if(gone)break;}stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});
   runOnMainSync(()->activity.paused=true);
   onGl(game,()->check(game.world.survival.mobs.isEmpty(),"held attack button defeats an aimed enemy using the sword"));
   onGl(game,()->{World w=game.world;w.set(48,33,46,15);w.progress.buckets=1;w.yaw=0;w.pitch=-.55f;game.renderer.dirty(48,46);});
   runOnMainSync(()->activity.paused=false);stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});Thread.sleep(200);stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});
   runOnMainSync(()->activity.paused=true);
   onGl(game,()->{check(game.world.get(48,33,46)==0&&game.world.progress.inventory[15]==1&&game.world.progress.buckets==0,"touch mining scoops lava into a finite bucket");game.world.selected=15;game.world.set(48,33,45,3);game.renderer.dirty(48,45);});
   runOnMainSync(()->activity.paused=false);tap(game,game.hud.placeX,game.hud.actionY);runOnMainSync(()->activity.paused=true);
   onGl(game,()->check(game.world.get(48,33,46)==15&&game.world.progress.inventory[15]==0&&game.world.progress.buckets==1,"touch placement pours lava and returns an empty bucket"));
   runOnMainSync(activity::storyMenu);clickText("Nachtgegner:");check(!game.world.survival.enemiesEnabled,"Story settings can disable night enemies");clickText("Weiterbauen");
   runOnMainSync(()->activity.paused=true);
   onGl(game,()->{
    World w=game.world;w.survival.enemiesEnabled=true;w.survival.time=520;w.yaw=0;w.pitch=-.08f;
    w.survival.mobs.add(new Survival.Mob(48.5f,34.62f,43.5f));w.set(46,33,46,11);game.renderer.dirty(46,46);
   });
   Thread.sleep(800);
   onGl(game,()->check(GLES20.glGetError()==GLES20.GL_NO_ERROR,"night lighting, lava and enemy meshes render without OpenGL errors"));
   android.graphics.Bitmap night=smallScreenshot();if(night!=null){emitScreenshot(night,"nightScreenshot");night.recycle();}
   float frozen=game.world.survival.time;Thread.sleep(200);check(game.world.survival.time==frozen,"paused game freezes night and enemies");
   World loaded=World.decode(game.world.encode());check(loaded.survival.night()&&loaded.survival.mobs.size()==1&&loaded.get(48,33,46)==15&&loaded.progress.sword,"night, enemies, lava and equipment survive a save/reload");
  }finally{
   onGl(game,()->{game.replace(original);activity.world=original;});
   runOnMainSync(()->{activity.paused=false;activity.save();activity.handler.postDelayed(activity.autosave,30000);});
   activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
  }
 }
 void testNature(MainActivity activity,GameView game)throws Exception{
  World original=game.world;
  runOnMainSync(()->{activity.paused=true;activity.handler.removeCallbacks(activity.autosave);});
  try{
   onGl(game,()->{
    World w=new World(5150,true,false);java.util.Arrays.fill(w.blocks,(byte)0);
    for(int x=0;x<World.W;x++)for(int z=0;z<World.D;z++)w.set(x,32,z,3);
    w.x=48.5f;w.y=34.62f;w.z=48.5f;w.yaw=0;w.pitch=-.55f;
    w.progress.chapter=5;w.progress.buckets=1;w.set(48,33,46,10);
    game.replace(w);activity.world=w;
   });
   runOnMainSync(()->activity.paused=false);
   stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});
   for(int attempt=0;attempt<100;attempt++){boolean filled;synchronized(game.world){filled=game.world.progress.inventory[10]==1;}if(filled)break;Thread.sleep(50);}
   stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});
   runOnMainSync(()->activity.paused=true);
   onGl(game,()->{
    check(game.world.get(48,33,46)==10&&game.world.progress.inventory[10]==1&&game.world.progress.buckets==0,"touch fills water bucket and natural water refills");
    game.world.set(48,33,46,0);game.world.set(48,33,45,3);game.world.selected=10;
   });
   runOnMainSync(()->activity.paused=false);tap(game,game.hud.placeX,game.hud.actionY);runOnMainSync(()->activity.paused=true);
   onGl(game,()->{
    World w=game.world;check(w.get(48,33,46)==10&&w.progress.buckets==1&&w.progress.inventory[10]==0,"touch pours water and returns empty bucket");
    for(int i=0;i<80;i++)w.nature.tick(w,.05f);
    check(w.get(49,33,46)==10,"poured water flows on the device");
    w.set(52,33,46,15);w.set(53,33,46,4);
    for(int i=0;i<80;i++)w.nature.tick(w,.05f);
    check(w.get(52,33,46)==3,"water-lava contact produces stone on the device");
    w.set(44,33,43,15);w.set(43,33,43,4);
    for(int i=0;i<80;i++)w.nature.tick(w,.05f);
    check(w.get(43,33,43)==8,"lava heat makes glass on the device");
    w.set(40,33,43,1);w.set(41,33,43,2);
    for(int i=0;i<800;i++)w.nature.tick(w,.05f);
    check(w.get(41,33,43)==1,"exposed dirt regrows grass on the device");
    try{World restored=World.decode(w.encode());
    check(java.util.Arrays.equals(w.blocks,restored.blocks)&&java.util.Arrays.equals(w.nature.levels,restored.nature.levels),"nature and flowing liquids survive save/reload on the device");}catch(java.io.IOException e){throw new AssertionError(e);}
   });
   runOnMainSync(activity::storyMenu);clickText("Flüssigkeiten fließen:");check(!game.world.nature.flowEnabled,"Story menu can pause fluid flow");clickText("Weiterbauen");
   runOnMainSync(()->activity.paused=true);
   float frozen=game.world.survival.time;byte[] blocks=game.world.blocks.clone();Thread.sleep(600);
   check(game.world.survival.time==frozen&&java.util.Arrays.equals(blocks,game.world.blocks),"menus freeze nature and survival together");
  }finally{
   onGl(game,()->{game.replace(original);activity.world=original;});
   runOnMainSync(()->{activity.paused=false;activity.save();activity.handler.postDelayed(activity.autosave,30000);});
   activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
  }
 }
 void testQrTransfer(MainActivity activity,GameView game)throws Exception{
  int initial=activity.library.list().size();
  runOnMainSync(()->{synchronized(game.world){game.world.set(29,30,29,14);}});
  ActivityMonitor senderMonitor=addMonitor(QrTransferActivity.class.getName(),null,false);
  runOnMainSync(activity::menu);clickText("Welt per QR teilen");
  QrTransferActivity sender=(QrTransferActivity)waitForMonitorWithTimeout(senderMonitor,15000);removeMonitor(senderMonitor);
  check(sender!=null,"sharing menu opens the QR sender");
  for(int i=0;i<100;i++){boolean[] ready={false};runOnMainSync(()->ready[0]=sender.image.getDrawable()!=null);if(ready[0])break;Thread.sleep(100);}
  waitForIdleSync();clickText("Pause");check(sender.hold&&sender.frames.size()>1,"QR sequence can be paused");
  check(activity.paused,"world simulation stays paused during QR export");
  check(sender.getWindow().getAttributes().screenBrightness>.5f,"sender keeps the QR display bright");
  Thread.sleep(300);android.graphics.Bitmap optical=getUiAutomation().takeScreenshot();
  check(optical!=null,"sender screen can be captured for optical verification");
  int width=optical.getWidth(),height=optical.getHeight();int[] rgb=new int[width*height];optical.getPixels(rgb,0,width,0,0,width,height);byte[] luma=new byte[rgb.length];
  for(int i=0;i<rgb.length;i++){int pixel=rgb[i];luma[i]=(byte)((((pixel>>16)&255)*299+((pixel>>8)&255)*587+(pixel&255)*114)/1000);}
  String visible=QrCodec.decode(luma,width,height);check(visible!=null&&visible.startsWith(WorldTransfer.PREFIX+":"),"actual sender screenshot decodes as a world fragment");
  emitScreenshot(optical,"qrScreenshot");optical.recycle();
  java.util.List<String> frames=new java.util.ArrayList<>(sender.frames);
  clickText("Langsamer");check(sender.delay==1200,"sender offers slower scanning speed");
  clickText("Zurück");for(int i=0;i<60&&activity.paused;i++)Thread.sleep(100);
  check(!activity.paused,"canceling QR export resumes the original world");
  ActivityMonitor receiverMonitor=addMonitor(QrTransferActivity.class.getName(),null,false);
  runOnMainSync(activity::menu);clickText("Welt per QR empfangen");
  QrTransferActivity receiver=(QrTransferActivity)waitForMonitorWithTimeout(receiverMonitor,15000);removeMonitor(receiverMonitor);
  check(receiver!=null,"receiving menu opens the QR camera");
  for(int i=0;i<100;i++){boolean[] ready={false};runOnMainSync(()->ready[0]=receiver.camera!=null&&receiver.camera.imageCount>0);if(ready[0])break;Thread.sleep(100);}
  QrCamera[] opened={null};runOnMainSync(()->opened[0]=receiver.camera);
  check(opened[0]!=null&&opened[0].imageCount>0,"real Camera2 preview delivers images in airplane mode");
  receiver.acceptFrame(visible);receiver.worker.submit(()->{}).get(30,TimeUnit.SECONDS);waitForIdleSync();
  check(receiver.collector.received()==1,"optically decoded screenshot enters receiver progress");
  receiver.acceptFrame(visible);receiver.acceptFrame("not a world code");receiver.worker.submit(()->{}).get(30,TimeUnit.SECONDS);
  check(receiver.collector.received()==1&&activity.library.list().size()==initial,"duplicates and unrelated QR codes cannot import a partial world");
  try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME"))){byte[] bytes=new byte[512];while(in.read(bytes)!=-1){}}
  for(int i=0;i<60&&!opened[0].closed();i++)Thread.sleep(100);
  check(opened[0].closed(),"camera closes when the receiving Activity enters the background");
  Activity resumed=startActivitySync(new Intent(activity,QrTransferActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));waitForIdleSync();
  check(resumed==receiver&&receiver.collector.received()==1,"returning to receiver preserves partial QR progress");
  java.util.Collections.reverse(frames);
  for(String text:frames){
   com.google.zxing.common.BitMatrix matrix=QrCodec.encode(text);int side=matrix.getWidth()*3;byte[] pixels=new byte[side*side];
   for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=(byte)(matrix.get(x/3,y/3)?0:255);
   String decoded=QrCodec.decode(pixels,side,side);check(text.equals(decoded),"QR bitmap fragment decodes on Android");receiver.acceptFrame(decoded);
  }
  receiver.worker.submit(()->{}).get(30,TimeUnit.SECONDS);waitForIdleSync();
  check(receiver.finishedScan&&receiver.incoming!=null,"all optical fragments produce a verified world preview");
  check(receiver.camera==null,"camera closes automatically after receiving the complete world");
  check(activity.library.list().size()==initial,"complete scan waits for explicit import before writing a world");
  field("Name der Weltkopie","QR Burg");clickText("Als neue Welt importieren");awaitWorld(activity,"QR Burg");
  check(game.world.get(29,30,29)==14,"imported copy contains the sender's edited block");
  check(activity.library.list().size()==initial+1,"QR import creates an extra world without replacing old entries");
  String first=activity.active.id;SavedWorld second=activity.library.importCopy("QR Burg",receiver.incoming.worldBytes);
  check(!second.id.equals(first)&&second.name.equals("QR Burg (2)"),"duplicate world names get distinct copies and safe names");
  check(activity.library.read(second).get(29,30,29)==14&&activity.library.read(activity.library.entry(first)).get(29,30,29)==14,"both imported copies remain readable");
  check(World.decode(activity.slotFile(0).readFully()).get(30,30,30)==11,"QR import preserves original creative world");
 }
 void field(String label,String value)throws Exception{
  for(int attempt=0;attempt<40;attempt++){
   android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
   android.view.accessibility.AccessibilityNodeInfo input=findField(root,label);
   if(input!=null){Bundle args=new Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args),"text entered into "+label);waitForIdleSync();return;}
   Thread.sleep(100);
  }
  throw new AssertionError("Field not found: "+label);
 }
 android.view.accessibility.AccessibilityNodeInfo findField(android.view.accessibility.AccessibilityNodeInfo node,String label){
  if(node==null)return null;if(label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
  for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo child=findField(node.getChild(i),label);if(child!=null)return child;}return null;
 }
 void awaitWorld(MainActivity activity,String name)throws Exception{
  for(int i=0;i<160&&(activity.busy||activity.paused||!activity.active.name.equals(name));i++)Thread.sleep(50);
  check(!activity.busy&&!activity.paused&&activity.active.name.equals(name),"world loaded: "+name);
 }
 void chooseWorld(MainActivity activity,String name)throws Exception{
  runOnMainSync(activity::menu);clickText("Welten laden");clickText(name);awaitWorld(activity,name);
 }
 void testWorldLibrary(MainActivity activity,GameView game)throws Exception{
  runOnMainSync(()->activity.switchMode(0));awaitSlot(activity,0);
  check(game.world.get(30,30,30)==11,"legacy creative world is preserved before creating worlds");
  runOnMainSync(activity::menu);clickText("Neue Welt");field("Weltname","Burg");field("Seed","445566");clickText("Welt erstellen");awaitWorld(activity,"Burg");
  String castle=activity.active.id;check(!castle.equals("creative")&&activity.slot==0&&game.world.seed==445566,"new named creative world uses a separate ID and seed");
  runOnMainSync(()->{activity.paused=true;synchronized(game.world){game.world.set(30,35,30,7);game.world.selected=7;}activity.paused=false;activity.renameWorld();});field("Weltname","Burg am See");clickText("Umbenennen");awaitWorld(activity,"Burg am See");
  check(activity.active.id.equals(castle),"renaming preserves the world file identity");
  runOnMainSync(activity::newWorld);field("Weltname","Testfeld");field("Seed","998877");clickText("Welt erstellen");awaitWorld(activity,"Testfeld");
  check(!activity.active.id.equals(castle)&&game.world.seed==998877,"second creative world does not replace the first");
  runOnMainSync(()->{synchronized(game.world){game.world.set(30,35,30,9);}});
  chooseWorld(activity,"Meine Kreativwelt");check(game.world.get(30,30,30)==11,"legacy world loads from the world list");
  chooseWorld(activity,"Burg am See");check(game.world.get(30,35,30)==7&&game.world.selected==7,"first creative world restores its blocks and selected material");
  chooseWorld(activity,"Testfeld");check(game.world.get(30,35,30)==9,"second creative world has independent block edits");
  chooseWorld(activity,"Meine Storywelt");check(game.world.story&&game.world.progress.inventory[9]==4,"creative world management preserves existing Story inventory");
  chooseWorld(activity,"Burg am See");check(activity.library.list().size()==6,"legacy worlds, two QR copies and two new worlds are listed");
  runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
  check(new WorldLibrary(activity.getFilesDir()).entry(castle).name.equals("Burg am See"),"renamed metadata persists independently of Activity memory");
  // Failure to load must keep the current world and must not create an empty replacement.
  SavedWorld missing=SavedWorld.create("Nicht vorhanden",0);runOnMainSync(()->activity.openWorld(missing,null));
  for(int i=0;i<100&&activity.busy;i++)Thread.sleep(50);
  check(!activity.busy&&activity.active.id.equals(castle)&&game.world.get(30,35,30)==7,"failed loading keeps the open world intact");
  check(!activity.library.file(missing).getBaseFile().exists(),"failed loading does not create an empty save");
 }
 void testReopened(MainActivity activity,GameView game,Bundle result)throws Exception{
   check(activity.active.name.equals("Burg am See")&&game.world.seed==445566,"process restart reopens the last selected named world");
   check(game.world.get(30,35,30)==7&&game.world.selected==7,"named world block edits survive a full process restart");
   check(activity.library.list().size()==6,"all world entries including QR imports survive process restart");
   for(SavedWorld entry:activity.library.list())if(entry.name.startsWith("QR Burg"))check(activity.library.read(entry).get(29,30,29)==14,"QR-imported blocks survive a full process restart");
   check(activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"restart stays in landscape with portrait device lock");
   runOnMainSync(activity::showWorlds);Thread.sleep(800);waitForIdleSync();android.graphics.Bitmap worlds=smallScreenshot();if(worlds!=null){emitScreenshot(worlds,"worldsScreenshot");worlds.recycle();}clickText("Zurück");
   result.putString("summary","PASS: "+checks+" world restart assertions");finish(Activity.RESULT_OK,result);
 }
 void systemBack()throws Exception{
  try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input keyevent KEYCODE_BACK"))){byte[] data=new byte[512];while(in.read(data)!=-1){}}
  waitForIdleSync();
 }
 boolean focused(MainActivity activity){boolean[] value={false};runOnMainSync(()->value[0]=activity.hasWindowFocus());return value[0];}
 void orient(MainActivity activity,GameView game,int orientation,float width)throws Exception{
  runOnMainSync(()->activity.setRequestedOrientation(orientation));boolean[] ready={false};for(int attempt=0;attempt<40&&!ready[0];attempt++){Thread.sleep(100);runOnMainSync(()->{game.hud.layoutHud();ready[0]=game.hud.designW==width;});}check(ready[0],"responsive controls match requested orientation "+orientation);
  // Window layout precedes the compositor finishing its rotation animation.
  Thread.sleep(1200);waitForIdleSync();
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
  if("worlds-reopen".equals(phase)){testReopened(activity,game,result);return;}
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
   World legacyStory=new World(7654321,true);legacyStory.set(31,30,30,7);
   try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"story.mcworld"))){out.write(legacyStory.encode());}
   activity.updates.prefs.edit().putBoolean("automatic",false).commit();runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);
   result.putString("summary","PASS: "+checks+" update seed assertions");finish(Activity.RESULT_OK,result);return;
  }
  if("verify".equals(phase)){check(game.world.get(30,30,30)==11&&game.world.selected==12,"install -r preserves world and selected material");check(!activity.updates.prefs.getBoolean("automatic",true),"update preference persists across APK replacement");}
  check(activity.getRequestedOrientation()==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,"manifest requests only landscape orientations");
  check(activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE,"launch is landscape even with portrait device lock");
  check(context.getApplicationInfo().category==android.content.pm.ApplicationInfo.CATEGORY_GAME,"game category preserves orientation support on large screens");
  check(World.decode(activity.slotFile(1).readFully()).get(31,30,30)==7,"existing 0.2.0 Story world is retained during upgrade");
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,960);
  testSwipeControl(activity,game);
  runOnMainSync(()->activity.paused=false);for(int attempt=0;attempt<30&&!focused(activity);attempt++)Thread.sleep(100);check(focused(activity),"game window has input focus before system back");systemBack();
  for(int attempt=0;attempt<30&&!activity.paused;attempt++)Thread.sleep(100);check(activity.paused,"system back opens game menu");for(int attempt=0;attempt<30&&focused(activity);attempt++)Thread.sleep(100);check(!focused(activity),"menu window receives focus before dismissal");
  systemBack();for(int attempt=0;attempt<30&&(activity.paused||!focused(activity));attempt++)Thread.sleep(100);check(!activity.paused&&focused(activity),"back dismisses menu without leaving game (paused="+activity.paused+", focused="+focused(activity)+")");
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
  testSurvival(activity,game);
   testNature(activity,game);
   testQrTransfer(activity,game);
  Thread.sleep(1500);
  android.graphics.Bitmap screenshot=smallScreenshot();
  if(screenshot!=null){try(FileOutputStream out=context.openFileOutput("smoke.png",0)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}emitScreenshot(screenshot,"screenshot");screenshot.recycle();}
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,960);testSwipeControl(activity,game);
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,960);
  testWorldLibrary(activity,game);
  android.graphics.Bitmap portrait=smallScreenshot();if(portrait!=null){emitScreenshot(portrait,"landscapeScreenshot");portrait.recycle();}
  result.putString("summary","PASS: "+checks+" Android assertions");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));try{android.graphics.Bitmap failure=smallScreenshot();if(failure!=null){emitScreenshot(failure,"failureScreenshot");failure.recycle();}android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)result.putString("activeWindowPackage",String.valueOf(root.getPackageName()));}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}}
}

