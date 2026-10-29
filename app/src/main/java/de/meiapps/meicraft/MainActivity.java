package de.meiapps.meicraft;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 GameView game; volatile OnlineController online; UpdateController updates; volatile World world;
 AtomicFile file; WorldLibrary library; SavedWorld active;
 volatile int slot; volatile boolean paused, busy,homeVisible;
 MainMenuView home;
 final ExecutorService disk=Executors.newSingleThreadExecutor();
 final Handler handler=new Handler(Looper.getMainLooper());
 final Runnable autosave=new Runnable(){public void run(){save();handler.postDelayed(this,30000);}};

 SavedWorld selectedWorld(int mode){
  String id=getSharedPreferences("worlds",0).getString("selected-"+mode,SavedWorld.legacy(mode).id);
  try{SavedWorld selected=library.entry(id);if(selected.mode==mode)return selected;}catch(IOException ignored){}
  try{for(SavedWorld candidate:library.list())if(candidate.mode==mode)return candidate;}catch(IOException ignored){}
  return null;
 }
 AtomicFile slotFile(int mode){SavedWorld selected=selectedWorld(mode);return selected==null?null:library.file(selected);}
 void rememberActive(){android.content.SharedPreferences.Editor editor=getSharedPreferences("worlds",0).edit();
  if(active==null)editor.remove("activeWorld");else editor.putInt("active",slot).putString("activeWorld",active.id).putString("selected-"+slot,active.id);editor.apply();}
 World load(AtomicFile source,int mode,boolean[] recovered){
  try{World loaded=World.decode(source.readFully());if(loaded.story!=(mode==1))throw new IOException("Wrong world mode");return loaded;}
  catch(Exception e){recovered[0]=source.getBaseFile().exists();if(recovered[0])try{java.nio.file.Files.copy(source.getBaseFile().toPath(),new File(getFilesDir(),source.getBaseFile().getName()+"-damaged-"+System.currentTimeMillis()).toPath());}catch(IOException ignored){}return new World(20261025L+mode,mode==1);}
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state); library=new WorldLibrary(getFilesDir());
  int oldMode=getSharedPreferences("worlds",0).getInt("active",0)==1?1:0;
  String id=getSharedPreferences("worlds",0).getString("activeWorld",SavedWorld.legacy(oldMode).id);
  boolean missing=false;
  try{active=library.entry(id);}catch(IOException e){try{List<SavedWorld> available=library.list();active=available.isEmpty()?null:available.get(0);}catch(IOException ignored){active=null;}missing=true;}
  boolean[] recovered={false};if(active!=null){slot=active.mode;file=library.file(active);world=load(file,slot,recovered);}else{slot=0;file=null;world=null;}rememberActive();
  game=new GameView(this,world);setContentView(game.root);updates=new UpdateController(this);immersive();
  if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::menu);
  if(recovered[0])new AlertDialog.Builder(this).setTitle("Spielstand nicht lesbar").setMessage("Die alte Datei wurde als Sicherung behalten. Eine neue Welt wurde geöffnet.").setPositiveButton("OK",null).show();
  else if(missing)toast("Die zuletzt gewählte Welt konnte nicht geöffnet werden. Unter Welten verwalten findest du deine Welten.");
  showHome();
 }
 void immersive(){if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.hide(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}else immersiveLegacy();}
 @SuppressWarnings("deprecation") private void immersiveLegacy(){getWindow().getDecorView().setSystemUiVisibility(5894);}
 void resumePlay(){paused=busy||homeVisible;}
 void showHome(){if(online!=null&&online.active()){online.leave();return;}if(busy)return;game.gl.setRenderMode(android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY);paused=true;homeVisible=true;game.clearInput();if(home!=null)game.root.removeView(home);home=new MainMenuView(this);game.root.addView(home,new android.widget.FrameLayout.LayoutParams(-1,-1));}
 void hideHome(){game.gl.setRenderMode(android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY);homeVisible=false;if(home!=null){game.root.removeView(home);home=null;}}
 void resumeFromHome(){if(busy||world==null)return;hideHome();resumePlay();}
 void about(){if(busy)return;paused=true;new AlertDialog.Builder(this).setTitle("Über meiCraft · "+versionName()).setMessage("Ein eigenständiges Klötzchenspiel von meiApps.\n\nWelten werden auf diesem Gerät gespeichert. Spielen und Teilen per QR funktionieren offline. Die Kamera wird nur beim Weltempfang verwendet. Offline ohne Anmeldung, Werbung oder Käufe. Online-Welten verwenden Firebase mit einer anonymen Geräte-ID; Weltänderungen, Anzeigename und Spielfigurposition werden dort gespeichert.\n\nUpdates verwenden je nach Installation den Play Store oder GitHub.").setPositiveButton("Zurück",null).setNeutralButton("Update-Einstellungen",(d,n)->handler.post(updates::settings)).setOnDismissListener(d->resumePlay()).show();}
 String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "";}}
 void save(){save(false);}
 void save(boolean announce){
  if(world==null||file==null||busy||disk.isShutdown())return;
  final byte[] data;final AtomicFile destination=file;final World snapshot=world;
  try{synchronized(snapshot){data=snapshot.encode();}}catch(IOException e){toast("Speichern fehlgeschlagen");return;}
  disk.execute(()->{try{WorldLibrary.write(destination,data);if(announce)runOnUiThread(()->toast("Welt gespeichert"));}
   catch(IOException e){runOnUiThread(()->toast("Spielstand konnte nicht gespeichert werden"));}});
 }
 void openWorld(SavedWorld target,Long seed){
  if(busy||disk.isShutdown())return;
  if(seed==null&&active!=null&&active.id.equals(target.id)){resumeFromHome();return;}
  final byte[] outgoing;final AtomicFile outgoingFile=file;
  try{if(world==null)outgoing=null;else synchronized(world){outgoing=world.encode();}}catch(IOException e){toast("Aktuelle Welt konnte nicht gespeichert werden");resumePlay();return;}
  busy=true;paused=true;game.clearInput();
  disk.execute(()->{
   boolean saved=false;
   try{
    if(outgoing!=null)WorldLibrary.write(outgoingFile,outgoing);saved=true;
    final World next;
    if(seed!=null){
     if(library.nameUsed(target.name,target.mode,target.id))throw new IOException("Dieser Weltname ist in diesem Modus schon vergeben.");
     if(library.file(target).getBaseFile().exists())throw new IOException("Diese Welt existiert bereits.");
     next=new World(seed,target.mode==1);WorldLibrary.write(library.file(target),next.encode());library.describe(target);
    }else next=library.read(target);
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     game.gl.queueEvent(()->{game.replace(next);runOnUiThread(()->{
      if(isFinishing()||isDestroyed())return;
      world=next;active=target;file=library.file(target);slot=target.mode;rememberActive();busy=false;hideHome();paused=false;save();
      toast(target.name+" geladen");
      if(next.story&&!getPreferences(0).getBoolean("storyIntroduced",false)){
       getPreferences(0).edit().putBoolean("storyIntroduced",true).apply();chapter();
      }
     });});
    });
   }catch(IOException e){final String message=saved?(seed==null?"Welt konnte nicht geladen werden.":e.getMessage()):"Speichern fehlgeschlagen. Weltwechsel abgebrochen.";
    runOnUiThread(()->{busy=false;resumePlay();toast(message);});}
  });
 }
 void switchMode(int target){SavedWorld selected=selectedWorld(target);if(selected!=null)openWorld(selected,null);else{slot=target;newWorld();}}
 void chooseMode(){if(online!=null&&online.active()){online.players();return;}if(busy)return;paused=true;game.clearInput();new AlertDialog.Builder(this).setTitle("Weltmodus wechseln").setItems(new String[]{"Kreativ · Freies Bauen","Story · Sammeln und Werkzeuge"},(d,n)->switchMode(n)).setOnDismissListener(d->resumePlay()).show();}
 void showWorlds(){
  if(busy)return;busy=true;paused=true;game.clearInput();
  disk.execute(()->{
   try{List<SavedWorld> worlds=library.list();runOnUiThread(()->{
    if(isFinishing()||isDestroyed())return;busy=false;
    String[] labels=new String[worlds.size()];for(int i=0;i<labels.length;i++){SavedWorld entry=worlds.get(i);labels[i]=entry.label()+(active!=null&&active.id.equals(entry.id)?" · aktiv":"");}
    new AlertDialog.Builder(this).setTitle("Welten laden").setItems(labels,(d,n)->openWorld(worlds.get(n),null))
     .setNeutralButton("Neue Welt",(d,n)->handler.post(this::newWorld)).setNegativeButton("Zurück",null).setOnDismissListener(d->resumePlay()).show();
   });}catch(IOException e){runOnUiThread(()->{busy=false;resumePlay();toast("Welten konnten nicht aufgelistet werden.");});}
  });
 }
 void onlineMenu(){if(online==null)online=new OnlineController(this);online.lobby();}
 void menu(){
  if(online!=null&&online.active()){online.menu();return;}
  if(homeVisible){moveTaskToBack(true);return;}if(paused||busy)return;if(world==null){showHome();return;}paused=true;game.clearInput();
  new AlertDialog.Builder(this).setTitle(active.label()).setItems(new String[]{"Weiterbauen","Spielstand speichern","Baumaterial wählen",world.story?"Story und Werkbank":world.flying?"Laufen statt fliegen":"Fliegen statt laufen","Hauptmenü"},(d,n)->{
   switch(n){case 1:save(true);break;case 2:handler.post(this::palette);break;case 3:if(world.story)handler.post(this::storyMenu);else{synchronized(world){world.flying=!world.flying;}game.velocityY=0;}break;case 4:handler.post(this::showHome);break;default:break;}
  }).setOnDismissListener(d->resumePlay()).show();
 }
 void manageWorlds(){worldPicker(false);}
 void pickShareWorld(){worldPicker(true);}
 void worldPicker(boolean share){
  if(busy)return;paused=true;game.clearInput();busy=true;disk.execute(()->{try{List<SavedWorld> worlds=library.list();runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;
   String[] names=new String[worlds.size()];for(int i=0;i<names.length;i++)names[i]=worlds.get(i).label();
   AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(share?"Welche Welt teilen?":"Deine Welten").setNegativeButton("Zurück",null).setOnDismissListener(d->resumePlay());
   if(worlds.isEmpty())builder.setMessage("Noch keine Welten vorhanden. Erstelle eine neue Welt oder empfange eine Weltkopie.");
   else builder.setItems(names,(d,n)->handler.post(()->{if(share)shareWorld(worlds.get(n));else worldActions(worlds.get(n));}));
   if(!share)builder.setNeutralButton("Neue Welt",(d,n)->handler.post(this::newWorld));builder.show();
  });}catch(IOException e){runOnUiThread(()->{busy=false;resumePlay();toast("Welten konnten nicht aufgelistet werden.");});}});
 }
 void worldActions(SavedWorld target){
  paused=true;new AlertDialog.Builder(this).setTitle(target.label()).setItems(new String[]{"Spielen","Umbenennen","Per QR teilen","Welt löschen"},(d,n)->handler.post(()->{switch(n){case 0:openWorld(target,null);break;case 1:renameWorld(target);break;case 2:shareWorld(target);break;case 3:confirmDelete(target);break;default:break;}})).setNegativeButton("Zurück",(d,n)->handler.post(this::manageWorlds)).setOnDismissListener(d->resumePlay()).show();
 }
 void confirmDelete(SavedWorld target){
  if(busy)return;paused=true;new AlertDialog.Builder(this).setTitle("Welt löschen?").setMessage("„"+target.name+"“ dauerhaft von diesem Gerät löschen? Bauten und Vorräte dieser Welt gehen verloren. Bereits geteilte Weltkopien bleiben erhalten.").setPositiveButton("Dauerhaft löschen",(d,n)->deleteWorld(target)).setNegativeButton("Abbrechen",null).setOnDismissListener(d->resumePlay()).show();
 }
 void deleteWorld(SavedWorld target){
  if(busy)return;busy=true;paused=true;game.clearInput();boolean deletingActive=active!=null&&active.id.equals(target.id);
  disk.execute(()->{try{
   library.delete(target);SavedWorld fallback=null;World next=null;
   if(deletingActive){List<SavedWorld> remaining;try{remaining=library.list();}catch(IOException e){remaining=java.util.Collections.emptyList();}for(SavedWorld candidate:remaining)try{next=library.read(candidate);fallback=candidate;break;}catch(IOException ignored){}}
   final SavedWorld replacement=fallback;final World replacementWorld=next;
   runOnUiThread(()->{if(isFinishing()||isDestroyed())return;android.content.SharedPreferences prefs=getSharedPreferences("worlds",0);android.content.SharedPreferences.Editor edit=prefs.edit();if(target.id.equals(prefs.getString("selected-"+target.mode,"")))edit.remove("selected-"+target.mode);edit.apply();
    if(deletingActive){game.gl.queueEvent(()->{game.replace(replacementWorld);runOnUiThread(()->{world=replacementWorld;active=replacement;file=replacement==null?null:library.file(replacement);slot=replacement==null?0:replacement.mode;rememberActive();busy=false;showHome();toast("Welt gelöscht");});});}
    else{busy=false;showHome();toast("Welt gelöscht");}
   });
  }catch(IOException e){runOnUiThread(()->{busy=false;resumePlay();toast("Welt konnte nicht gelöscht werden.");});}});
 }
 void help(){paused=true;game.clearInput();new AlertDialog.Builder(this).setTitle("Steuerung").setMessage("Links am Kreis bewegen, rechts wischen zum Umsehen. Mit + setzt du den ausgewählten Block neben den anvisierten Block.\n\nStory: − zum Abbauen gedrückt halten. Du brauchst passende Werkzeuge und gesammelte Materialien. Gras gibt Erde. Unter STORY findest du Mikos Aufträge und Rezepte. Baue Stufen aus gesammelten Blöcken, um Gruben zu verlassen.\n\n↑ springt oder schwimmt aufwärts. In der Kreativwelt kannst du zusätzlich fliegen. Jede Welt speichert separat, alle 30 Sekunden und beim Verlassen. Zum Spielen ist kein Internet nötig.\n\nNeu im Storymodus: Tag/Nacht, Nachtschatten, Leben, Eisenschwert und Lavaeimer. Die Anleitung findest du unter STORY → Überleben und Lavagraben.").setPositiveButton("Verstanden",null).setOnDismissListener(d->resumePlay()).show();}
 void palette(){paused=true;game.clearInput();String[] names=new String[World.NAMES.length-1];synchronized(world){for(int i=1;i<World.NAMES.length;i++)names[i-1]=World.NAMES[i]+(world.story?" · "+world.progress.inventory[i]:"");}new AlertDialog.Builder(this).setTitle("Baumaterial").setItems(names,(d,n)->{synchronized(world){world.selected=n+1;}paused=false;}).setOnCancelListener(d->resumePlay()).show();}
 String[] storyRows(){synchronized(world){return new String[]{"Auftrag: "+world.progress.goal(),"4 Bretter · 1 Holz (Vorrat: "+world.progress.inventory[9]+")","4 Ziegel · 4 Erde + 1 Stein (Vorrat: "+world.progress.inventory[7]+")","2 Glas · 2 Sand + 1 Holz (Vorrat: "+world.progress.inventory[8]+")","1 Eisenbarren · 1 Erz + 1 Holz (Vorrat: "+world.progress.iron+")","1 Eisenblock · 9 Barren (Vorrat: "+world.progress.inventory[14]+")","4 Grasblöcke · 4 Erde + 1 Laub","1 Eiseneimer · 3 Eisenbarren (leer: "+world.progress.buckets+")","Eisenschwert · 2 Eisenbarren + 1 Holz","Lavaeimer im Ofen · 1 Eimer + 1 Leuchtstein + 4 Stein + 2 Holz","Nachtgegner: "+(world.survival.enemiesEnabled?"an · antippen zum Ausschalten":"aus · antippen zum Einschalten"),"Überleben und Lavagraben · Hilfe",world.nature.cavesAdded?"Lavahöhlen: bereits ergänzt":"Lavahöhlen in dieser Welt ergänzen", "Flüssigkeiten fließen: "+(world.nature.flowEnabled?"an":"aus")};}}
 void storyMenu(){if(!world.story)return;paused=true;game.clearInput();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new java.util.ArrayList<>(java.util.Arrays.asList(storyRows())));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Story · Werkbank / Ofen").setAdapter(adapter,null).setNegativeButton("Weiterbauen",null).setOnDismissListener(d->resumePlay()).create();dialog.show();dialog.getListView().setOnItemClickListener((parent,view,position,id)->{if(position==0){dialog.dismiss();handler.post(this::chapter);return;}if(position==10||position==12||position==13){synchronized(world){if(position==10)world.survival.toggleEnemies();else if(position==13)world.nature.flowEnabled=!world.nature.flowEnabled;else if(!world.nature.cavesAdded)toast(world.nature.addCaves(world)+" Lavahöhlen in unberührtem Gestein ergänzt");}save();adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(storyRows());adapter.notifyDataSetChanged();return;}if(position==11){dialog.dismiss();handler.post(this::survivalHelp);return;}String result;synchronized(world){result=world.progress.craft(position-1);}toast(result);save();adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(storyRows());adapter.notifyDataSetChanged();});}
 void survivalHelp(){paused=true;game.clearInput();new AlertDialog.Builder(this).setTitle("Nacht und Lavagraben").setMessage("Ein Tag dauert 12 Minuten: Nach 8 Minuten wird es Nacht. Nachts erscheinen draußen Nachtschatten. Mauern halten sie auf, Leuchtstein verhindert neue Gegner in der Nähe. Bei Morgenlicht verschwinden sie. Die Zeit steht in Menüs und bei geschlossener App still.\n\nVisiere Gegner an und halte − zum Angreifen. Mit 2 Eisenbarren und 1 Holz stellst du ein stärkeres Eisenschwert her. Bei einer Niederlage kehrst du zum Start zurück; Bauwerke und Vorräte bleiben erhalten. Nachtgegner lassen sich in der Werkbank ausschalten.\n\nBaue einen Graben und einen Eiseneimer (3 Eisenbarren). Neue Storywelten enthalten Lavahöhlen tief im Gestein, etwa 3–5 Blöcke über dem Weltboden. Suche seitlich, sobald du diese Tiefe erreicht hast. In der Mitte der Seen liegt eine nachfüllbare Lavaquelle; beim Anvisieren wird sie als solche angezeigt. Andere Lavablöcke lassen sich einmal aufnehmen.\n\nAlte Welten: Wähle in STORY einmal ‚Lavahöhlen in dieser Welt ergänzen‘. Nur vollständig unverändertes Gestein wird dafür verwendet. Deine vorhandenen Landschaften werden beim Laden nicht verändert. Das Ofenrezept bleibt als Alternative verfügbar.\n\nMit einem leeren Eimer Lava oder Wasser anvisieren und − antippen. Wähle danach Lava oder Wasser im Baumaterial und gieße mit + aus. Natürliche Wasserquellen und markierte Lavaquellen füllen sich nach; ausgegossene Quellen bleiben endlich. Fließende Flüssigkeit lässt sich nicht mit dem Eimer vermehren.\n\nAusgegossenes Wasser fließt nach unten und bis zu vier Blöcke zur Seite; Lava fließt langsamer und bis zu zwei Blöcke zur Seite. Mauern begrenzen die Ströme. Nach dem Entfernen einer Quelle trocknet ihr Strom aus. Unter STORY lässt sich das Fließen pausieren.\n\nBei direktem Kontakt mit Wasser wird der Lavablock zu Stein. Sand neben Lava wird nach etwa drei Sekunden zu Glas, solange die Lava noch da ist. Vorsicht: Auch natürliche Lavaquellen können durch Wasser erkalten! Lava schadet dir und Gegnern.\n\nFreie Erde unter offenem Himmel wird neben Gras langsam wieder grün. Für sofortigen Rasen: 4 Erde + 1 Laub ergeben an der Werkbank 4 Grasblöcke.").setPositiveButton("Verstanden",null).setOnDismissListener(d->resumePlay()).show();}
 void chapter(){if(!world.story)return;paused=true;game.clearInput();String title,detail;boolean ready;synchronized(world){title=world.progress.title();detail=world.progress.detail();ready=world.progress.ready();}final boolean claimable=ready;new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton(ready?"Auftrag abschließen":"Weiter sammeln",(d,n)->{if(claimable){synchronized(world){world.progress.claim();}save();handler.post(this::chapter);}}).setNeutralButton("Werkbank",(d,n)->handler.post(this::storyMenu)).setOnDismissListener(d->resumePlay()).show();}
 void newWorld(){
  if(busy)return;paused=true;game.clearInput();
  LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
  EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Weltname, z. B. Meine Burg");name.setContentDescription("Weltname");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});form.addView(name);
  EditText seed=new EditText(this);seed.setSingleLine(true);seed.setHint("Seed: Zahl oder Text (optional)");seed.setContentDescription("Seed");form.addView(seed);
  RadioGroup modes=new RadioGroup(this);modes.setOrientation(LinearLayout.HORIZONTAL);RadioButton creative=new RadioButton(this),story=new RadioButton(this);creative.setId(View.generateViewId());story.setId(View.generateViewId());creative.setText(de.meiapps.meicraft.R.string.mode_creative);story.setText(de.meiapps.meicraft.R.string.mode_story);modes.addView(creative);modes.addView(story);modes.check(slot==1?story.getId():creative.getId());form.addView(modes);
  ScrollView scroll=new ScrollView(this);scroll.addView(form);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Neue Welt anlegen").setView(scroll).setPositiveButton("Welt erstellen",null).setNegativeButton("Abbrechen",null).setOnDismissListener(d->resumePlay()).create();dialog.show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   final SavedWorld target;try{target=SavedWorld.create(name.getText().toString(),modes.getCheckedRadioButtonId()==story.getId()?1:0);}
   catch(IllegalArgumentException e){name.setError(e.getMessage());return;}
   String text=seed.getText().toString().trim();long value=text.isEmpty()?System.currentTimeMillis():text.hashCode();try{value=Long.parseLong(text);}catch(NumberFormatException ignored){}
   final long worldSeed=value;dialog.dismiss();handler.post(()->openWorld(target,worldSeed));
  });
 }
 void renameWorld(){if(active!=null)renameWorld(active);}
 void renameWorld(SavedWorld target){
  if(busy)return;paused=true;game.clearInput();EditText name=new EditText(this);name.setSingleLine(true);name.setText(target.name);name.setContentDescription("Weltname");name.selectAll();name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Weltnamen ändern").setView(name).setPositiveButton("Umbenennen",null).setNegativeButton("Abbrechen",null).setOnDismissListener(d->resumePlay()).create();dialog.show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   if(busy)return;final SavedWorld renamed;try{renamed=new SavedWorld(target.id,name.getText().toString(),target.mode);}catch(IllegalArgumentException e){name.setError(e.getMessage());return;}
   busy=true;disk.execute(()->{try{
    if(library.nameUsed(renamed.name,renamed.mode,renamed.id))throw new IOException("Dieser Weltname ist in diesem Modus schon vergeben.");
    library.describe(renamed);runOnUiThread(()->{if(active!=null&&active.id.equals(renamed.id))active=renamed;busy=false;dialog.dismiss();if(homeVisible)showHome();toast("Welt umbenannt");});
   }catch(IOException e){runOnUiThread(()->{busy=false;name.setError(e.getMessage());});}});
  });
 }
 @SuppressWarnings("deprecation") // Platform activity result API also works on Android 8 without a new UI framework.
 void launchQr(boolean sending){startActivityForResult(new android.content.Intent(this,QrTransferActivity.class).putExtra("send",sending),QrTransferActivity.REQUEST);}
 void qrTransfer(boolean sending){
  if(busy)return;paused=true;game.clearInput();if(!sending){launchQr(false);return;}if(active!=null)shareWorld(active);else pickShareWorld();
 }
 void shareWorld(SavedWorld target){
  if(busy)return;paused=true;game.clearInput();busy=true;
  final World current=active!=null&&active.id.equals(target.id)?world:null;
  disk.execute(()->{try{
   byte[] packet;if(current!=null)synchronized(current){packet=WorldTransfer.pack(target.name,current);}else packet=WorldTransfer.pack(target.name,library.read(target));
   WorldLibrary.write(new AtomicFile(new File(getCacheDir(),QrTransferActivity.OUTGOING)),packet);
   runOnUiThread(()->{busy=false;if(!isFinishing()&&!isDestroyed())launchQr(true);});
  }catch(IOException|IllegalArgumentException e){runOnUiThread(()->{busy=false;resumePlay();toast("QR-Übertragung konnte nicht vorbereitet werden.");});}});
 }
 @SuppressWarnings("deprecation")
 @Override protected void onActivityResult(int request,int result,android.content.Intent data){
  super.onActivityResult(request,result,data);
  if(request==OnlineInviteActivity.REQUEST){if(result==RESULT_OK&&data!=null){try{OnlineWorld invite=OnlineWorld.decode(data.getStringExtra("invitation"));handler.post(()->{if(online==null)online=new OnlineController(this);online.joinForm(invite);});}catch(IllegalArgumentException e){toast(e.getMessage());}}return;}
  if(request!=QrTransferActivity.REQUEST)return;
  if(result==RESULT_OK&&data!=null){try{SavedWorld imported=library.entry(data.getStringExtra("worldId"));handler.post(()->openWorld(imported,null));}
   catch(IOException e){resumePlay();toast("Importierte Welt konnte nicht geöffnet werden.");}}
  else resumePlay();
 }
 void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
 @Override protected void onResume(){super.onResume();if(updates!=null)updates.onResume();if(online!=null&&online.session!=null)online.session.foreground=true;if(game!=null)game.gl.onResume();handler.removeCallbacks(autosave);handler.postDelayed(autosave,30000);immersive();}
 @Override protected void onPause(){if(updates!=null)updates.onPause();if(online!=null&&online.session!=null){online.session.foreground=false;online.session.healthy=false;}if(game!=null){game.clearInput();game.gl.onPause();}save();handler.removeCallbacks(autosave);super.onPause();}
 @Override protected void onDestroy(){if(updates!=null)updates.onDestroy();if(online!=null&&online.session!=null)online.session.close();handler.removeCallbacks(autosave);disk.shutdown();super.onDestroy();}
 @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&game!=null)game.clearInput();}
 @Override public boolean onKeyUp(int code,KeyEvent event){if(Build.VERSION.SDK_INT<33&&code==KeyEvent.KEYCODE_BACK){menu();return true;}return super.onKeyUp(code,event);}
}

