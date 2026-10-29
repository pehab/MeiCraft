package de.meiapps.meicraft;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.util.AtomicFile;
import android.widget.*;
import com.google.zxing.common.BitMatrix;
import org.json.*;
import java.util.*;

/** Online lobby and lifecycle, deliberately separate from local world persistence. */
final class OnlineController {
    final MainActivity activity;
    volatile OnlineSession session;
    World localWorld;SavedWorld localEntry;AtomicFile localFile;int localSlot;
    AlertDialog progress,workbenchDialog;ArrayAdapter<String> workbenchAdapter;
    OnlineController(MainActivity activity){this.activity=activity;}
    boolean active(){return session!=null;}
    void lobby(){
        if(activity.busy)return;
        if(active()){players();return;}
        activity.paused=true;
        new AlertDialog.Builder(activity).setTitle("Gemeinsam bauen").setMessage("Gemeinsame Bauwelten mit begrenzten Rohstoffen, Werkzeugen und Werkbank. Ihr startet zu Fuß. Die Welt wird beim Weiterlaufen ohne Gebietswechsel nachgeladen und bleibt ohne Gegner, Tag/Nacht und Flüssigkeitsfluss.")
            .setPositiveButton("Neue Online-Welt",(d,n)->activity.handler.post(this::createForm))
            .setNeutralButton("Beitreten / Öffnen",(d,n)->activity.handler.post(this::joinMenu))
            .setNegativeButton("Zurück",null).setOnDismissListener(d->activity.resumePlay()).show();
    }
    LinearLayout form(){LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);return form;}
    EditText input(LinearLayout form,String hint,String value,int limit){EditText input=new EditText(activity);input.setSingleLine();input.setHint(hint);input.setText(value);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});form.addView(input);return input;}
    String savedName(){return activity.getSharedPreferences("online-worlds",0).getString("player","");}
    void createForm(){
        LinearLayout form=form();EditText worldName=input(form,"Name der Online-Welt","Familienwelt",48),player=input(form,"Dein Spielername",savedName(),24);
        Spinner size=new Spinner(activity);size.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"192 × 192 Blöcke · zusammenhängend","288 × 288 Blöcke · zusammenhängend"}));form.addView(size);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Neue Online-Bauwelt").setView(form).setPositiveButton("Erstellen",null).setNegativeButton("Abbrechen",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{OnlineWorld.playerName(player.getText().toString());if(worldName.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Bitte einen Weltnamen eingeben.");dialog.dismiss();connect(new OnlineWorld(OnlineWorld.randomId(),OnlineWorld.randomId()),player.getText().toString(),true,worldName.getText().toString(),size.getSelectedItemPosition()+2);}catch(IllegalArgumentException e){player.setError(e.getMessage());}});
    }
    void joinMenu(){
        List<String> ids=new ArrayList<>(),labels=new ArrayList<>();labels.add("Einladungs-QR scannen");labels.add("Einladung als Text eingeben");
        try{JSONObject saved=new JSONObject(activity.getSharedPreferences("online-worlds",0).getString("worlds","{}"));Iterator<String> keys=saved.keys();while(keys.hasNext()){String id=keys.next();ids.add(id);labels.add(saved.getString(id));}}catch(JSONException ignored){/* Empty recent list. */}
        new AlertDialog.Builder(activity).setTitle("Online-Welten").setItems(labels.toArray(new String[0]),(d,n)->{if(n==0)scan();else if(n==1)joinForm(null);else joinForm(new OnlineWorld(ids.get(n-2),"00000000000000000000000000000000"));}).setNegativeButton("Zurück",null).show();
    }
    @SuppressWarnings("deprecation")
    void scan(){activity.startActivityForResult(new Intent(activity,OnlineInviteActivity.class),OnlineInviteActivity.REQUEST);}
    void joinForm(OnlineWorld supplied){
        LinearLayout form=form();EditText player=input(form,"Dein Spielername",savedName(),24),invite=supplied==null?input(form,"Einladungstext","",128):null;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Online-Welt betreten").setView(form).setPositiveButton("Verbinden",null).setNegativeButton("Abbrechen",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{OnlineWorld parsed=supplied!=null?supplied:OnlineWorld.decode(invite.getText().toString().trim());String name=OnlineWorld.playerName(player.getText().toString());dialog.dismiss();connect(parsed,name,false,"",2);}catch(IllegalArgumentException e){if(invite!=null)invite.setError(e.getMessage());else player.setError(e.getMessage());}});
    }
    void connect(OnlineWorld invitation,String player,boolean create,String name,int side){
        if(active()||activity.busy)return;
        activity.save();localWorld=activity.world;localEntry=activity.active;localFile=activity.file;localSlot=activity.slot;
        activity.busy=true;activity.paused=true;
        try{
            FirebaseRest firebase=new FirebaseRest(activity);
            OnlineSession next=new OnlineSession(activity,firebase,invitation,player,new OnlineSession.Listener(){
                public void loaded(World world,String worldName){
                    OnlineSession current=session;if(current==null||current.closed)return;
                    activity.game.gl.queueEvent(()->{if(session!=current||current.closed)return;activity.game.replace(world);activity.runOnUiThread(()->{
                        if(session!=current||current.closed)return;
                        activity.world=world;activity.file=null;activity.active=null;activity.busy=false;activity.hideHome();activity.paused=false;dismissProgress();activity.toast(worldName+" · "+(current.side*World.W)+" × "+(current.side*World.D)+" Blöcke");
                    });});activity.game.gl.requestRender();
                }
                public void problem(String message,boolean fatal){
                    if(fatal&&session!=null&&session.deletionOnly&&session.owner()){
                        dismissProgress();activity.busy=false;
                        new AlertDialog.Builder(activity).setTitle(session.name).setMessage(message).setPositiveButton("Welt löschen",(d,n)->activity.handler.post(()->confirmDelete(session))).setNegativeButton("Zurück",(d,n)->leave()).setOnCancelListener(d->leave()).show();return;
                    }
                    if(fatal){leave();new AlertDialog.Builder(activity).setTitle("Online-Welt").setMessage(message).setPositiveButton("OK",null).show();}
                    else activity.game.message(message);
                }
            });session=next;
            progress=new AlertDialog.Builder(activity).setTitle("Online-Welt wird geladen").setMessage("Gerät anmelden und Welt synchronisieren …").setCancelable(false).setNegativeButton("Abbrechen",(d,n)->leave()).show();
            next.start(create,name,side);
        }catch(Exception e){leave();activity.toast("Firebase-Konfiguration konnte nicht geladen werden.");}
    }
    void dismissProgress(){if(progress!=null){progress.dismiss();progress=null;}}
    void leave(){
        if(workbenchDialog!=null){workbenchDialog.dismiss();workbenchDialog=null;}
        OnlineSession old=session;session=null;if(old!=null)old.close();dismissProgress();activity.busy=false;
        activity.world=localWorld;activity.active=localEntry;activity.file=localFile;activity.slot=localSlot;
        World restored=localWorld;activity.game.gl.queueEvent(()->activity.game.replace(restored));activity.game.gl.requestRender();activity.showHome();
    }
    void menu(){
        OnlineSession current=session;if(current==null||activity.busy)return;activity.paused=true;activity.game.clearInput();
        List<String> actions=new ArrayList<>(Arrays.asList("Weiterbauen","Baumaterial wählen","Werkbank / Werkzeuge","Spieler / Einladung","Online-Welt verlassen"));
        if(current.owner())actions.add("Online-Welt löschen");
        new AlertDialog.Builder(activity).setTitle(current.name+" · Online").setItems(actions.toArray(new String[0]),(d,n)->{if(n==1)activity.handler.post(activity::palette);else if(n==2)activity.handler.post(this::workbench);else if(n==3)activity.handler.post(this::players);else if(n==4)activity.handler.post(this::leave);else if(n==5)activity.handler.post(()->confirmDelete(current));}).setOnDismissListener(d->activity.resumePlay()).show();
    }
    void confirmDelete(OnlineSession current){
        if(current==null||session!=current||!current.owner()||activity.busy)return;
        activity.paused=true;activity.game.clearInput();
        new AlertDialog.Builder(activity).setTitle("Online-Welt löschen?").setMessage("\""+current.name+"\" für alle Spieler dauerhaft löschen? Alle Bauten und Vorräte dieser Welt werden entfernt. Das kann nicht rückgängig gemacht werden.")
            .setPositiveButton("Dauerhaft löschen",(d,n)->activity.handler.post(()->deleteWorld(current)))
            .setNegativeButton("Abbrechen",(d,n)->{if(current.deletionOnly)leave();else activity.resumePlay();}).setOnCancelListener(d->{if(current.deletionOnly)leave();else activity.resumePlay();}).show();
    }
    void deleteWorld(OnlineSession current){
        if(session!=current||!current.owner()||activity.busy)return;
        activity.busy=true;activity.paused=true;
        progress=new AlertDialog.Builder(activity).setTitle("Online-Welt wird gelöscht").setMessage("Bauten und Vorräte entfernen …").setCancelable(false).show();
        current.deleteWorld(()->{
            try{android.content.SharedPreferences prefs=activity.getSharedPreferences("online-worlds",0);JSONObject saved=new JSONObject(prefs.getString("worlds","{}"));saved.remove(current.invitation.id);prefs.edit().putString("worlds",saved.toString()).apply();}catch(JSONException ignored){/* A stale recent entry cannot restore deleted cloud data. */}
            leave();activity.toast("Online-Welt gelöscht.");
        },message->{dismissProgress();activity.busy=false;
            new AlertDialog.Builder(activity).setTitle("Löschen unterbrochen").setMessage(message).setPositiveButton("Erneut versuchen",(d,n)->activity.handler.post(()->deleteWorld(current))).setNegativeButton("Zurück",(d,n)->leave()).setOnCancelListener(d->leave()).show();
        });
    }
    String[] workbenchRows(){
        Story p=session.inventory;
        return new String[]{p.title()+" · "+p.goal(),"4 Bretter · 1 Holz ("+p.inventory[9]+")","4 Ziegel · 4 Erde + 1 Stein ("+p.inventory[7]+")","2 Glas · 2 Sand + 1 Holz ("+p.inventory[8]+")","1 Eisenbarren · 1 Erz + 1 Holz ("+p.iron+")","1 Eisenblock · 9 Barren ("+p.inventory[14]+")","4 Grasblöcke · 4 Erde + 1 Laub","Eiseneimer · 3 Barren (leer: "+p.buckets+")","Lavaeimer · Eimer + Leuchtstein + 4 Stein + 2 Holz"};
    }
    void refreshWorkbench(){if(session!=null&&workbenchDialog!=null&&workbenchDialog.isShowing()){workbenchAdapter.setNotifyOnChange(false);workbenchAdapter.clear();workbenchAdapter.addAll(workbenchRows());workbenchAdapter.notifyDataSetChanged();}}
    void workbench(){
        OnlineSession current=session;if(current==null||activity.busy)return;activity.paused=true;activity.game.clearInput();
        workbenchAdapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,new ArrayList<>(Arrays.asList(workbenchRows())));
        workbenchDialog=new AlertDialog.Builder(activity).setTitle("Werkbank · "+Story.TOOLS[current.inventory.tool()]).setAdapter(workbenchAdapter,null).setNegativeButton("Weiterbauen",null).setOnDismissListener(d->{workbenchDialog=null;activity.resumePlay();}).create();workbenchDialog.show();
        workbenchDialog.getListView().setOnItemClickListener((parent,view,index,id)->{
            if(index==0){Story p=current.inventory;new AlertDialog.Builder(activity).setTitle(p.title()).setMessage(p.detail()).setPositiveButton(p.ready()?"Auftrag abschließen":"Weiter sammeln",(d,n)->{if(p.ready())current.progressAction(-1);}).setNegativeButton("Zurück",null).show();}
            else current.progressAction(index==8?8:index-1);
        });
    }
    void players(){
        activity.paused=true;activity.game.clearInput();
        OnlineSession current=session;if(current==null)return;
        List<OnlineSession.Player> list=new ArrayList<>(current.players);String[] names=new String[list.size()];for(int i=0;i<names.length;i++){OnlineSession.Player p=list.get(i);names[i]=p.name+(p.uid.equals(current.owner)?" · Besitzer":"")+" · "+(int)(p.sector%current.side*World.W+p.x)+" / "+(int)(p.sector/current.side*World.D+p.z);}
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle("Mitspieler · "+(current.healthy?"verbunden":"Verbindung unterbrochen")).setItems(names,(d,n)->{
            OnlineSession.Player player=list.get(n);if(current.owner()&&!player.uid.equals(current.owner))new AlertDialog.Builder(activity).setTitle(player.name+" entfernen?").setMessage("Diese anonyme Geräte-ID bleibt auch mit der bisherigen Einladung gesperrt.").setPositiveButton("Entfernen",(dd,nn)->current.ban(player.uid)).setNegativeButton("Abbrechen",null).show();
        }).setNegativeButton("Zurück",null).setOnDismissListener(d->activity.resumePlay());
        if(current.owner())dialog.setPositiveButton("Einladungs-QR",(d,n)->activity.handler.post(this::showInvite));dialog.show();
    }
    void showInvite(){
        OnlineSession current=session;if(current==null||!current.owner())return;
        try{BitMatrix matrix=QrCodec.encode(current.invitation.encode());int scale=6,side=matrix.getWidth()*scale;int[] pixels=new int[side*side];for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=matrix.get(x/scale,y/scale)?Color.BLACK:Color.WHITE;
            ImageView image=new ImageView(activity);image.setImageBitmap(Bitmap.createBitmap(pixels,side,side,Bitmap.Config.ARGB_8888));image.setAdjustViewBounds(true);image.setBackgroundColor(Color.WHITE);image.setContentDescription("Einladungs-QR für die Online-Welt");
            new AlertDialog.Builder(activity).setTitle("Zur Welt einladen").setView(image).setMessage("Auf dem anderen Gerät: Gemeinsam bauen → Beitreten / Öffnen → Einladungs-QR scannen.").setPositiveButton("Fertig",null).setNeutralButton("Text kopieren",(d,n)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("meiCraft-Einladung",current.invitation.encode()));activity.toast("Einladung kopiert");}).setOnDismissListener(d->activity.resumePlay()).show();
        }catch(Exception e){activity.toast("Einladungs-QR konnte nicht angezeigt werden.");}
    }
}
