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
   matches.sort(java.util.Comparator.comparingInt(node->text.equalsIgnoreCase(node.getText()==null?"":node.getText().toString())?0:1));
   for(android.view.accessibility.AccessibilityNodeInfo node:matches){
    android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(!node.isVisibleToUser()||bounds.isEmpty())continue;
    // Native dialog rows expose a semantic click, independent of window animations.
    if(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}
    boolean actionable=node.isClickable();
    for(android.view.accessibility.AccessibilityNodeInfo parent=node.getParent();parent!=null&&!actionable;parent=parent.getParent()){
     String kind=String.valueOf(parent.getClassName());actionable=parent.isClickable()||kind.contains("ListView");
    }
    if(!actionable)continue; // Explanatory home labels are not substitutes for a pending dialog row.
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
 void testForge(MainActivity activity,GameView game)throws Exception{
  World original=game.world;World fixture=new World(20261027,false);java.util.Arrays.fill(fixture.blocks,(byte)0);fixture.forge.initialize(fixture);
  fixture.story=true;fixture.progress.chapter=7;fixture.nature.flowEnabled=false;fixture.survival.enemiesEnabled=false;fixture.survival.time=180;
  for(int x=24;x<=38;x++)for(int z=24;z<=38;z++)fixture.set(x,4,z,3);
  fixture.set(30,5,35,3);fixture.set(30,6,35,3);fixture.x=30.5f;fixture.y=8.62f;fixture.z=35.5f;fixture.yaw=.1f;fixture.pitch=-.5f;
  int[] pattern=Forge.pattern(28,6,29,0,false);
  for(int n=0;n<6;n++){int cell=pattern[n];fixture.set(cell%World.W,5,cell/World.W%World.D,15);fixture.set(cell%World.W,6,cell/World.W%World.D,Forge.PATTERN[n][2]);}
  fixture.set(32,5,30,15);fixture.set(32,6,30,13);
  runOnMainSync(()->{activity.paused=true;activity.handler.removeCallbacks(activity.autosave);});
  try{
   onGl(game,()->{game.replace(fixture);activity.world=fixture;});runOnMainSync(()->activity.paused=false);
   boolean heated=false;for(int n=0;n<300;n++){synchronized(fixture){heated=fixture.forge.heat(32,6,30)>=.3f;}if(heated)break;Thread.sleep(50);}
   check(heated,"live game clock heats ore and hidden ritual on Android");
   runOnMainSync(()->activity.paused=true);onGl(game,()->{});
   byte[] before;int age;synchronized(fixture){before=fixture.blocks.clone();age=fixture.forge.ores.get(World.index(32,6,30));World saved=World.decode(fixture.encode());check(saved.forge.ores.size()==1&&saved.forge.rituals.size()==1,"Android save/reload preserves both forge timers");}
   Thread.sleep(700);synchronized(fixture){check(java.util.Arrays.equals(before,fixture.blocks)&&fixture.forge.ores.get(World.index(32,6,30))==age,"menus freeze active smelting and ritual progress");}
   android.graphics.Bitmap heatedImage=smallScreenshot();if(heatedImage!=null){emitScreenshot(heatedImage,"forgeHeatScreenshot");heatedImage.recycle();}
   runOnMainSync(()->activity.paused=false);
   boolean finished=false;for(int n=0;n<600;n++){synchronized(fixture){finished=fixture.get(32,6,30)==16&&fixture.get(Math.min(pattern[2],pattern[3])%World.W,6,Math.min(pattern[2],pattern[3])/World.W%World.D)==11;}if(finished)break;Thread.sleep(50);}
   check(finished,"real-time Android reactions produce raw iron and a glowstone");
   int lava=0;synchronized(fixture){for(int cell:pattern)if(fixture.get(cell%World.W,5,cell/World.W%World.D)==15)lava++;}check(lava==6,"Android ritual leaves six reusable lava sources");
   runOnMainSync(()->activity.paused=true);onGl(game,()->{float dx=32.5f-fixture.x,dz=30.5f-fixture.z;fixture.yaw=(float)Math.atan2(-dx,-dz);fixture.pitch=(float)Math.atan2(6.5f-fixture.y,Math.sqrt(dx*dx+dz*dz));});
   game.gl.requestRender();Thread.sleep(200);android.graphics.Bitmap readyImage=smallScreenshot();if(readyImage!=null){emitScreenshot(readyImage,"forgeReadyScreenshot");readyImage.recycle();}
   runOnMainSync(()->activity.paused=false);stickEvent(game,0,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});
   boolean collected=false;for(int n=0;n<100;n++){synchronized(fixture){collected=fixture.progress.iron==1;}if(collected)break;Thread.sleep(50);}
   stickEvent(game,1,new int[]{5},new float[][]{{game.hud.mineX,game.hud.actionY}});check(collected&&fixture.get(32,6,30)==0,"touch mining raw iron yields exactly one iron bar");
  }finally{runOnMainSync(()->activity.paused=true);onGl(game,()->{game.replace(original);activity.world=original;});runOnMainSync(()->{activity.paused=false;activity.save();activity.handler.postDelayed(activity.autosave,30000);});activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);}
 }
 boolean worldExists(WorldLibrary library,String id)throws Exception{for(SavedWorld entry:library.list())if(entry.id.equals(id))return true;return false;}
 void deleteViaUi(MainActivity activity,String name,boolean confirm)throws Exception{
  runOnMainSync(activity::showHome);clickText("Welten verwalten");clickText(name);clickText("Welt löschen");clickText(confirm?"Dauerhaft löschen":"Abbrechen");
  for(int n=0;n<100&&activity.busy;n++)Thread.sleep(50);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);waitForIdleSync();
 }
 void testRecentOnlineRemoval(MainActivity activity)throws Exception{
  android.content.SharedPreferences prefs=activity.getSharedPreferences("online-worlds",0);
  String previous=prefs.getString("worlds","{}");String stale=OnlineWorld.randomId(),keep=OnlineWorld.randomId();
  try{
   prefs.edit().putString("worlds",new org.json.JSONObject().put(stale,"Gelöschte Testwelt").put(keep,"Familienwelt behalten").toString()).commit();
   runOnMainSync(activity::onlineMenu);clickText("Beitreten / Öffnen");clickText("Gelöschte Testwelt");clickText("Abbrechen");
   check(new org.json.JSONObject(prefs.getString("worlds","{}")).has(stale),"cancel preserves recent world entry");
   runOnMainSync(activity.online::joinMenu);clickText("Gelöschte Testwelt");clickText("Aus Liste entfernen");
   org.json.JSONObject remaining=new org.json.JSONObject(prefs.getString("worlds","{}"));
   check(!remaining.has(stale)&&remaining.has(keep),"removing deleted remote world only forgets selected local entry without Firebase access");
   check(activity.online.session==null&&!activity.busy,"list cleanup does not start a network session");
  }finally{prefs.edit().putString("worlds",previous).commit();}
 }
 static final class SyncBackend implements FirebaseRest.ConnectionFactory {
  int attempts,stateReads,blockQueries,inventoryReads,writes,unexpected,status=200;long revision;
  String inventory="";
  @Override public java.net.HttpURLConnection open(java.net.URL url){
   attempts++;
   return new java.net.HttpURLConnection(url){
    @Override public void connect(){}
    @Override public void disconnect(){}
    @Override public boolean usingProxy(){return false;}
    @Override public int getResponseCode(){return status;}
    @Override public java.io.OutputStream getOutputStream(){writes++;return new java.io.ByteArrayOutputStream();}
    @Override public java.io.InputStream getErrorStream(){return new java.io.ByteArrayInputStream("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Read quota exceeded\"}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Override public java.io.InputStream getInputStream()throws IOException{
     String path=url.getPath(),response;
     if(path.endsWith("/sync/state")){stateReads++;response="{\"fields\":{\"regions\":{\"mapValue\":{\"fields\":{\"s0\":{\"mapValue\":{\"fields\":{\"revision\":{\"integerValue\":\""+revision+"\"}}}}}}},\"players\":{\"mapValue\":{\"fields\":{}}}}}";}
     else if(path.endsWith(":runQuery")){blockQueries++;response="[{\"document\":{\"name\":\"fixture/blocks/10_30_10\",\"updateTime\":\"2026-10-31T12:00:00Z\",\"fields\":{\"x\":{\"integerValue\":\"10\"},\"y\":{\"integerValue\":\"30\"},\"z\":{\"integerValue\":\"10\"},\"type\":{\"integerValue\":\"7\"},\"updatedAt\":{\"timestampValue\":\"2026-10-31T12:00:00Z\"}}}}]";}
     else if(path.endsWith("/inventory/main")){inventoryReads++;response=inventory;}
     else{unexpected++;throw new IOException("Unexpected fixture request");}
     return new java.io.ByteArrayInputStream(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
   };
  }
 }
 void testSyncRequests(MainActivity activity,GameView game)throws Exception{
  SyncBackend backend=new SyncBackend();FirebaseRest rest=new FirebaseRest(activity,backend);rest.uid="fixture-parent";rest.token="fixture-token";rest.expires=System.currentTimeMillis()+3600000;
  OnlineSession session=new OnlineSession(activity,rest,new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId()),"Test",new OnlineSession.Listener(){public void loaded(World w,String n){}public void problem(String text,boolean fatal){}});
  try{
   session.world=World.onlineSpace(123,2);session.side=2;session.px=session.world.x;session.py=session.world.y;session.pz=session.world.z;session.lastX=session.px;session.lastY=session.py;session.lastZ=session.pz;session.lastHeartbeat=System.currentTimeMillis();session.loading=false;session.healthy=true;
   for(int id=0;id<4;id++){OnlineSession.Region region=new OnlineSession.Region(id);region.generated=true;region.revision=0;session.regions.put(id,region);}
   for(int i=0;i<8;i++)session.poll();
   check(backend.stateReads==8&&backend.blockQueries==0&&backend.inventoryReads==0&&backend.writes==0&&backend.unexpected==0,"unchanged worlds read exactly one shared document per cycle, no block, member or inventory polls");
   backend.revision=1;session.poll();onGl(game,()->{});
   check(backend.blockQueries==1&&session.world.get(10,30,10)==7,"one changed region fetches and applies the peer block update");
   session.poll();check(backend.blockQueries==1,"cached region revision suppresses duplicate block reads");
   Story updated=new Story();updated.inventory[2]=4;backend.inventory="{\"updateTime\":\"2026-10-31T12:00:01Z\",\"fields\":"+session.inventoryFields(updated,"").toString()+"}";
   session.inventoryUncertain=true;session.poll();session.poll();onGl(game,()->{});
   check(backend.inventoryReads==1&&session.inventory.inventory[2]==4,"an ambiguous write response reconciles inventory once instead of polling it forever");
   backend.status=429;session.poll();long until=session.nextAttempt;int requests=backend.attempts;for(int i=0;i<10;i++)session.poll();
   check(!session.healthy&&until-System.currentTimeMillis()>55000&&backend.attempts==requests,"actual HTTP 429 pauses building and prevents immediate repeated network requests");
   backend.status=200;session.nextAttempt=0;session.poll();check(session.healthy&&session.failures==0,"successful reconnect restores the session");
  }finally{session.close();}
 }
 void testOnlineStreaming(MainActivity activity,GameView game)throws Exception{
  World original=activity.world;android.util.AtomicFile oldFile=activity.file;OnlineController previous=activity.online;
  World wide=World.onlineSpace(7412,2);for(int i=0;i<4;i++)OnlineInventory.install(wide,OnlineTerrain.region(7412,i,2),i,2);
  for(int x=89;x<=105;x++)for(int z=40;z<=57;z++){wide.set(x,32,z,3);for(int y=33;y<=40;y++)wide.set(x,y,z,0);}
  wide.x=94.5f;wide.y=34.6f;wide.z=48.5f;wide.yaw=-(float)Math.PI/2;wide.pitch=0;
  FirebaseRest rest=new FirebaseRest(activity);rest.uid="test-owner";
  OnlineController controller=new OnlineController(activity);OnlineSession session=new OnlineSession(activity,rest,new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId()),"Test",new OnlineSession.Listener(){public void loaded(World w,String n){}public void problem(String text,boolean fatal){}});
  controller.session=session;session.world=wide;session.side=2;session.owner=rest.uid;session.name="Streaming-Test";session.loading=false;session.healthy=true;session.readyRegions=new java.util.HashSet<>(java.util.Arrays.asList(0,1,2,3));session.inventory.chapter=7;session.inventory.inventory[2]=5;OnlineInventory.apply(wide.progress,session.inventory);
  session.players=java.util.Collections.singletonList(new OnlineSession.Player("test-peer",FirebaseRest.fields("name","Mitspieler","sector",1,"x",3.5,"y",34.6,"z",48.5,"yaw",Math.PI/2,"banned",false)));
  try{
   runOnMainSync(()->{activity.handler.removeCallbacks(activity.autosave);activity.file=null;activity.paused=true;activity.online=controller;activity.hideHome();});
   onGl(game,()->{game.replace(wide);activity.world=wide;});runOnMainSync(()->activity.paused=false);Thread.sleep(500);
   check(!game.world.flying&&game.world.story,"online resource mode starts walking");check(game.renderer.meshCols==12&&game.renderer.meshRows==12,"renderer covers the full continuous online world");
   check(!game.renderer.playerLabels.isEmpty(),"player across the former sector seam is rendered with name");
   android.graphics.Bitmap image=smallScreenshot();if(image!=null){emitScreenshot(image,"onlineScreenshot");image.recycle();}
   onGl(game,()->game.forward=1);for(int i=0;i<100&&wide.x<97.5f;i++)Thread.sleep(100);onGl(game,()->game.forward=0);
   check(game.world==wide&&wide.x>96&&wide.x<105,"walking crosses the old boundary without world replacement or teleport");
   check(session.sector==1&&wide.progress.inventory[2]==5,"streaming preserves player inventory and tracks continuous position");
   String oldToken=rest.token;rest.token="eyJtestHeader.testPayload.testSignature";
   FirebaseRest.Failure quota=rest.failure(429,"Firestore","Blockänderungen lesen","{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Read quota exceeded key="+rest.key+" token="+rest.token+"\",\"details\":[{\"reason\":\"RATE_LIMIT_EXCEEDED\",\"metadata\":{\"quota_metric\":\"document/read_count\"}}]}}");
   check(quota.status==429&&quota.serverStatus.equals("RESOURCE_EXHAUSTED")&&quota.detail.contains("document/read_count"),"diagnostics preserve service, status and quota metric");
   check(!quota.detail.contains(rest.key)&&!quota.detail.contains(rest.token),"diagnostics redact API key and authentication token");rest.token=oldToken;
   runOnMainSync(controller::diagnostics);clickText("Bericht kopieren");
   android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
   String report=String.valueOf(clipboard.getPrimaryClip().getItemAt(0).getText());
   check(report.contains("HTTP 429")&&report.contains("RESOURCE_EXHAUSTED")&&!report.contains(rest.key),"diagnostic report copies useful errors without API keys");
   runOnMainSync(controller::workbench);clickText("Weiterbauen");check(!activity.paused,"online workbench returns to the same world");
   runOnMainSync(controller::menu);clickText("Online-Welt löschen");clickText("Abbrechen");check(activity.online==controller&&game.world==wide&&!activity.busy,"canceling owner deletion leaves online world and inventory intact");
   session.owner="another-owner";runOnMainSync(controller::menu);
   android.view.accessibility.AccessibilityNodeInfo menu=getUiAutomation().getRootInActiveWindow();check(menu==null||menu.findAccessibilityNodeInfosByText("Online-Welt löschen").isEmpty(),"non-owner has no online delete action");clickText("Weiterbauen");session.owner=rest.uid;
  }finally{
   session.close();runOnMainSync(()->activity.paused=true);onGl(game,()->{game.replace(original);activity.world=original;});runOnMainSync(()->{activity.online=previous;activity.file=oldFile;activity.paused=false;activity.handler.postDelayed(activity.autosave,30000);});
  }
 }
 void testHomeAndDeletion(MainActivity activity,GameView game)throws Exception{
  String original=activity.active.id;int initial=activity.library.list().size();runOnMainSync(activity::showHome);check(activity.homeVisible&&activity.paused,"main menu pauses the world");waitForIdleSync();Thread.sleep(250);
  android.graphics.Bitmap homeImage=smallScreenshot();if(homeImage!=null){emitScreenshot(homeImage,"homeScreenshot");homeImage.recycle();}
  clickText("Über meiCraft");clickText("Zurück");check(activity.homeVisible&&activity.paused,"closing About returns to the paused home screen");
  clickText("Steuerung");clickText("Verstanden");check(activity.paused,"closing help does not run the world behind the home screen");
  String firebaseUid=activity.getSharedPreferences("firebase-user",0).getString("uid","");
  clickText("Gemeinsam bauen");clickText("Neue Online-Welt");clickText("Abbrechen");
  check(activity.homeVisible&&activity.active.id.equals(original)&&activity.online.session==null,"canceling online creation preserves local world");
  clickText("Gemeinsam bauen");clickText("Beitreten / Öffnen");clickText("Zurück");
  check(firebaseUid.equals(activity.getSharedPreferences("firebase-user",0).getString("uid","")),"browsing online lobby does not create an anonymous account");
  SavedWorld temporary=activity.library.importCopy("Löschtest",game.world.encode());
  deleteViaUi(activity,"Löschtest",false);check(worldExists(activity.library,temporary.id),"canceling delete retains the world");
  deleteViaUi(activity,"Löschtest",true);check(!worldExists(activity.library,temporary.id)&&!activity.library.file(temporary).getBaseFile().exists(),"confirmed inactive world deletion removes metadata and snapshot");
  check(activity.active.id.equals(original)&&activity.library.list().size()==initial,"deleting another world preserves the current world and all neighbors");
  runOnMainSync(activity::newWorld);field("Weltname","Aktive Löschwelt");field("Seed","8754");clickText("Welt erstellen");awaitWorld(activity,"Aktive Löschwelt");SavedWorld activeDeleted=activity.active;
  deleteViaUi(activity,"Aktive Löschwelt",true);check(activity.homeVisible&&activity.paused&&activity.active!=null&&!activity.active.id.equals(activeDeleted.id),"active deletion returns home with a different selected world");
  runOnMainSync(activity::save);activity.disk.submit(()->{}).get(30,TimeUnit.SECONDS);check(!activity.library.file(activeDeleted).getBaseFile().exists()&&!worldExists(new WorldLibrary(activity.getFilesDir()),activeDeleted.id),"later autosaves cannot resurrect a deleted active world");
  chooseWorld(activity,"Burg am See");check(activity.active.id.equals(original)&&activity.library.list().size()==initial,"world selection still works after active and inactive deletion");
  File root=new File(activity.getCacheDir(),"isolated-delete-"+System.nanoTime());WorldLibrary isolated=new WorldLibrary(root);SavedWorld creative=SavedWorld.legacy(0),story=SavedWorld.legacy(1);
  byte[] snapshot=game.world.encode();WorldLibrary.write(isolated.file(creative),snapshot);isolated.describe(creative);WorldLibrary.write(new android.util.AtomicFile(new File(root,"world.mcworld.bak")),snapshot);
  isolated.delete(creative);check(isolated.list().size()==1&&!isolated.file(creative).getBaseFile().exists()&&!new File(root,"world.mcworld.bak").exists(),"legacy deletion removes the default save and its backup");
  WorldLibrary.write(isolated.file(creative),snapshot);check(isolated.list().size()==1,"a stale default-world snapshot cannot recreate a deleted world");
  boolean rejected=false;try{isolated.read(creative);}catch(IOException e){rejected=true;}check(rejected,"deleted default world cannot be silently regenerated");
  isolated.delete(creative);isolated.delete(story);check(new WorldLibrary(root).list().isEmpty(),"deleting the last default world leaves an empty persistent library");
  SavedWorld fresh=isolated.importCopy("Neue Welt nach dem Löschen",snapshot);check(isolated.list().size()==1,"a fresh world can be imported into an empty library");isolated.delete(fresh);check(isolated.list().isEmpty(),"the last named world can also be deleted");
 }
 void testEmptyLibrary(MainActivity activity,GameView game,Bundle result,boolean reopened)throws Exception{
  if(!reopened){
   SavedWorld last=activity.active;activity.disk.submit(()->{try{for(SavedWorld entry:activity.library.list())if(!entry.id.equals(last.id))activity.library.delete(entry);}catch(IOException e){throw new java.io.UncheckedIOException(e);}}).get(30,TimeUnit.SECONDS);
   deleteViaUi(activity,last.name,true);
  }
  check(activity.homeVisible&&activity.paused&&activity.active==null&&activity.world==null&&game.world==null,"empty library remains at home without an active world");
  check(activity.library.list().isEmpty(),"deleted worlds do not reappear after restart");
  if(reopened){clickText("Neue Welt");field("Weltname","Neuanfang");field("Seed","12345");clickText("Welt erstellen");awaitWorld(activity,"Neuanfang");check(activity.library.list().size()==1,"new world creation works after deleting every world and restarting");}
  result.putString("summary","PASS: "+checks+" empty library assertions");finish(Activity.RESULT_OK,result);
 }
 void qrPreview(QrTransferActivity receiver,int orientation,int expectedRotation,String screenshot)throws Exception{
  runOnMainSync(()->receiver.setRequestedOrientation(orientation));
  boolean[] ready={false};
  for(int i=0;i<100&&!ready[0];i++){Thread.sleep(100);runOnMainSync(()->{
   QrPreview view=receiver.preview;
   if(view.bufferWidth<1||view.getHeight()<1||view.getDisplay()==null||view.getDisplay().getRotation()!=expectedRotation)return;
   int relative=view.relativeRotation();double aspect=relative%180==0?(double)view.bufferWidth/view.bufferHeight:(double)view.bufferHeight/view.bufferWidth;
   ready[0]=Math.abs((double)view.getWidth()/view.getHeight()-aspect)<.015&&receiver.camera!=null&&receiver.camera.imageCount>0;
  });}
  check(ready[0],"camera preview preserves the sensor aspect ratio in landscape orientation "+orientation);
  check(receiver.preview.getHolder().getSurface().isValid(),"rotating receiver keeps a valid automatic-orientation camera surface");
  runOnMainSync(()->receiver.preview.performClick());
  Thread.sleep(400);android.graphics.Bitmap previewImage=smallScreenshot();if(previewImage!=null){emitScreenshot(previewImage,screenshot);previewImage.recycle();}
 }
 void testQrTransfer(MainActivity activity,GameView game)throws Exception{
  int initial=activity.library.list().size();
  runOnMainSync(()->{synchronized(game.world){game.world.set(29,30,29,14);}});
  ActivityMonitor senderMonitor=addMonitor(QrTransferActivity.class.getName(),null,false);
  runOnMainSync(activity::showHome);clickText("Welt per QR teilen");clickText(activity.active.name);
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
  clickText("Zurück");waitForIdleSync();
  check(activity.homeVisible&&activity.paused,"canceling QR export returns to the paused home screen");
  ActivityMonitor receiverMonitor=addMonitor(QrTransferActivity.class.getName(),null,false);
  clickText("Welt per QR empfangen");
  QrTransferActivity receiver=(QrTransferActivity)waitForMonitorWithTimeout(receiverMonitor,15000);removeMonitor(receiverMonitor);
  check(receiver!=null,"receiving menu opens the QR camera");
  for(int i=0;i<100;i++){boolean[] ready={false};runOnMainSync(()->ready[0]=receiver.camera!=null&&receiver.camera.imageCount>0);if(ready[0])break;Thread.sleep(100);}
  QrCamera[] opened={null};runOnMainSync(()->opened[0]=receiver.camera);
  check(opened[0]!=null&&opened[0].imageCount>0,"real Camera2 preview delivers images in airplane mode");
  qrPreview(receiver,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,android.view.Surface.ROTATION_270,"qrReceiverReverseScreenshot");
  qrPreview(receiver,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,android.view.Surface.ROTATION_90,"qrReceiverScreenshot");
  receiver.acceptFrame(visible);receiver.worker.submit(()->{}).get(30,TimeUnit.SECONDS);waitForIdleSync();
  check(receiver.collector.received()==1,"optically decoded screenshot enters receiver progress");
  receiver.acceptFrame(visible);receiver.acceptFrame("not a world code");receiver.worker.submit(()->{}).get(30,TimeUnit.SECONDS);
  check(receiver.collector.received()==1&&activity.library.list().size()==initial,"duplicates and unrelated QR codes cannot import a partial world");
  try(InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("input keyevent KEYCODE_HOME"))){byte[] bytes=new byte[512];while(in.read(bytes)!=-1){}}
  for(int i=0;i<60&&!opened[0].closed();i++)Thread.sleep(100);
  check(opened[0].closed(),"camera closes when the receiving Activity enters the background");
  // Reordering an existing Activity does not create one: startActivitySync would wait forever.
  runOnMainSync(()->activity.startActivity(new Intent(activity,QrTransferActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
  for(int i=0;i<100&&!receiver.resumed;i++)Thread.sleep(100);waitForIdleSync();
  check(receiver.resumed&&receiver.collector.received()==1,"returning to receiver preserves partial QR progress");
  java.util.Collections.reverse(frames);
  for(String text:frames){
   com.google.zxing.common.BitMatrix matrix=QrCodec.encode(text);int side=matrix.getWidth()*3;byte[] pixels=new byte[side*side];
   for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=(byte)(matrix.get(x/3,y/3)?0:255);
   int row=side+23;java.nio.ByteBuffer plane=java.nio.ByteBuffer.allocateDirect(row*(side-1)+side);
   for(int y=0;y<side;y++)for(int x=0;x<side;x++)plane.put(y*row+x,pixels[y*side+x]);
   String decoded=QrCodec.decode(QrCameraFrame.luma(plane,side,side,row,1),side,side);check(text.equals(decoded),"QR fragment decodes from a padded camera Y plane on Android");receiver.acceptFrame(decoded);
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
  runOnMainSync(activity::showHome);clickText("Welten verwalten");clickText(name);clickText("Spielen");awaitWorld(activity,name);
 }
 void testWorldLibrary(MainActivity activity,GameView game)throws Exception{
  runOnMainSync(()->activity.switchMode(0));awaitSlot(activity,0);
  check(game.world.get(30,30,30)==11,"legacy creative world is preserved before creating worlds");
  runOnMainSync(activity::showHome);clickText("Neue Welt");field("Weltname","Burg");field("Seed","445566");clickText("Welt erstellen");awaitWorld(activity,"Burg");
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
  if(!"seed".equals(phase)){check(activity.homeVisible&&activity.paused,"launch opens the paused main menu");if(activity.world!=null)runOnMainSync(activity::resumeFromHome);}
  GameView game=activity.game;
  if("empty-seed".equals(phase)||"empty-reopen".equals(phase)){testEmptyLibrary(activity,game,result,"empty-reopen".equals(phase));return;}
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
   testForge(activity,game);
   testQrTransfer(activity,game);
  Thread.sleep(1500);
  android.graphics.Bitmap screenshot=smallScreenshot();
  if(screenshot!=null){try(FileOutputStream out=context.openFileOutput("smoke.png",0)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}emitScreenshot(screenshot,"screenshot");screenshot.recycle();}
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,960);testSwipeControl(activity,game);
  orient(activity,game,android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,960);
  testWorldLibrary(activity,game);
  testRecentOnlineRemoval(activity);
  testSyncRequests(activity,game);
  testOnlineStreaming(activity,game);
  testHomeAndDeletion(activity,game);
  android.graphics.Bitmap portrait=smallScreenshot();if(portrait!=null){emitScreenshot(portrait,"landscapeScreenshot");portrait.recycle();}
  result.putString("summary","PASS: "+checks+" Android assertions");finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));try{android.graphics.Bitmap failure=smallScreenshot();if(failure!=null){emitScreenshot(failure,"failureScreenshot");failure.recycle();}android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)result.putString("activeWindowPackage",String.valueOf(root.getPackageName()));}catch(Exception ignored){}finish(Activity.RESULT_CANCELED,result);}}
}

