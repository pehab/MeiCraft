package de.meiapps.meicraft;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    GameView game; UpdateController updates; volatile World world; final ExecutorService disk=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable autosave=new Runnable(){public void run(){save();handler.postDelayed(this,30000);}};
    AtomicFile file; volatile boolean paused;
    @Override public void onCreate(Bundle state){super.onCreate(state);file=new AtomicFile(new File(getFilesDir(),"world.mcworld"));
        boolean recovered=false;
        try{world=World.decode(file.readFully());}catch(Exception e){world=new World(20261025L);recovered=file.getBaseFile().exists();}
        game=new GameView(this,world);setContentView(game);updates=new UpdateController(this);immersive();
        if(recovered)new AlertDialog.Builder(this).setTitle("Spielstand nicht lesbar").setMessage("Die gespeicherte Datei konnte nicht geladen werden. Sie bleibt als Sicherung erhalten. Eine neue Welt wurde geöffnet.").setPositiveButton("OK",null).show();
        if(recovered){try{java.nio.file.Files.copy(file.getBaseFile().toPath(),new File(getFilesDir(),"world-damaged-"+System.currentTimeMillis()).toPath());}catch(Exception ignored){}}
        if(!getPreferences(0).getBoolean("introduced",false))new AlertDialog.Builder(this).setTitle("Willkommen bei meiCraft").setMessage("Deine kleine Offline-Bauwelt.\n\nLinks: bewegen · rechts wischen: umsehen\n−: Block abbauen · +: Block setzen\n↑ / ↓: fliegen · unten: Baumaterial wählen\n\nDas Fadenkreuz zeigt auf den Block. Unter ☰ findest du alle Materialien, Laufmodus und eine neue Welt. Dein Fortschritt wird automatisch lokal gespeichert.").setPositiveButton("Losbauen",(d,w)->getPreferences(0).edit().putBoolean("introduced",true).apply()).show();
    }
    void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    void save(){if(world==null)return;final byte[] data;try{synchronized(world){data=world.encode();}}catch(IOException e){toast("Speichern fehlgeschlagen");return;}disk.execute(()->{FileOutputStream stream=null;try{stream=file.startWrite();stream.write(data);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);runOnUiThread(()->toast("Spielstand konnte nicht gespeichert werden"));}});}
    void menu(){if(paused)return;paused=true;game.clearInput();
        new AlertDialog.Builder(this).setTitle("meiCraft · Deine Bauwelt").setItems(new String[]{"Weiterbauen","Baumaterial wählen",world.flying?"Laufen statt fliegen":"Fliegen statt laufen","Spielstand speichern","Neue Welt …","Steuerung","Updates"},(dialog,which)->{
            paused=false;
            if(which==1)palette();
            if(which==2){synchronized(world){world.flying=!world.flying;}game.velocityY=0;}
            if(which==3){save();toast("Speichern gestartet");}
            if(which==4)newWorld();
            if(which==6)updates.settings();
            if(which==5){paused=true;new AlertDialog.Builder(this).setTitle("Steuerung").setMessage("Links am Kreis bewegen. Rechts frei wischen, um dich umzusehen. Mit − entfernst du den anvisierten Block; mit + setzt du deinen ausgewählten Block daneben. Reichweite: 6 Blöcke.\n\nIm Flugmodus: ↑ und ↓. Im Laufmodus: ↑ zum Springen. Die Welt ist 96 × 96 Blöcke groß. Am Weltrand hält die Bewegung an. Wasser ist durchquerbar; du kannst fliegend heraus.\n\nOhne Internet, Werbung oder Käufe. Automatisches Speichern alle 30 Sekunden und beim Verlassen.").setPositiveButton("Verstanden",(d,w)->paused=false).setOnCancelListener(d->paused=false).show();}
        }).setOnCancelListener(d->paused=false).show();}
    void palette(){paused=true;new AlertDialog.Builder(this).setTitle("Baumaterial").setItems(java.util.Arrays.copyOfRange(World.NAMES,1,World.NAMES.length),(d,n)->{synchronized(world){world.selected=n+1;}paused=false;}).setOnCancelListener(d->paused=false).show();}
    void newWorld(){paused=true;EditText seed=new EditText(this);seed.setSingleLine(true);seed.setHint("Weltname oder Zahl (optional)");new AlertDialog.Builder(this).setTitle("Neue Welt erstellen?").setMessage("Deine aktuelle Welt wird ersetzt. Das kann nicht rückgängig gemacht werden.").setView(seed).setPositiveButton("Neue Welt",(d,w)->{String text=seed.getText().toString().trim();long value=text.isEmpty()?System.currentTimeMillis():text.hashCode();try{value=Long.parseLong(text);}catch(NumberFormatException ignored){}World next=new World(value);game.gl.queueEvent(()->{game.replace(next);world=next;runOnUiThread(()->{paused=false;save();});});}).setNegativeButton("Abbrechen",(d,w)->paused=false).setOnCancelListener(d->paused=false).show();}
    void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    @Override protected void onResume(){super.onResume();if(updates!=null)updates.onResume();if(game!=null)game.gl.onResume();handler.removeCallbacks(autosave);handler.postDelayed(autosave,30000);immersive();}
    @Override protected void onPause(){if(updates!=null)updates.onPause();if(game!=null){game.clearInput();game.gl.onPause();}save();handler.removeCallbacks(autosave);super.onPause();}
    @Override protected void onDestroy(){if(updates!=null)updates.onDestroy();handler.removeCallbacks(autosave);disk.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){menu();}
}
