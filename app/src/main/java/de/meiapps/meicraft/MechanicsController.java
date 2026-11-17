package de.meiapps.meicraft;

import android.app.AlertDialog;
import android.widget.*;
import java.util.*;

/** Guided selection uses the existing crosshair and + button; configuration never edits terrain. */
final class MechanicsController {
    final MainActivity activity;volatile int stage;volatile World editing;Mechanics.Block button;final List<Mechanics.Block> blocks=new ArrayList<>();
    volatile List<int[]> highlights=Collections.emptyList();
    MechanicsController(MainActivity activity){this.activity=activity;}
    synchronized void cancel(){stage=0;editing=null;button=null;blocks.clear();highlights=Collections.emptyList();}
    String hint(){return stage==1?"Schalter anvisieren und + antippen":stage==2?"Blöcke mit + markieren · ⚙ zum Fertigstellen":"";}
    synchronized boolean capture(World w,int[] hit){if(stage==0)return false;if(editing!=w){cancel();return true;}if(hit==null){activity.game.message("Näher an einen Block herangehen");return true;}try{
        Mechanics.Block b=new Mechanics.Block(hit[0],hit[1],hit[2],w.get(hit[0],hit[1],hit[2]));Mechanics.valid(b);
        if(stage==1){button=b;stage=2;}else{if(b.key().equals(button.key()))throw new IllegalArgumentException("Der Schalter bleibt stehen");int found=-1;for(int i=0;i<blocks.size();i++)if(blocks.get(i).key().equals(b.key()))found=i;if(found>=0)blocks.remove(found);else{if(blocks.size()==Mechanics.MAX_BLOCKS)throw new IllegalArgumentException("Höchstens sieben Blöcke");blocks.add(b);}}
        List<int[]> marks=new ArrayList<>();marks.add(new int[]{button.x,button.y,button.z});for(Mechanics.Block c:blocks)marks.add(new int[]{c.x,c.y,c.z});highlights=marks;activity.game.message(stage==2?blocks.size()+" / 7 Blöcke · ⚙ zum Fertigstellen":hint());
    }catch(IllegalArgumentException e){activity.game.message(e.getMessage());}return true;}
    void open(){World w=activity.world;if(w==null)return;activity.paused=true;activity.game.clearInput();
        if(stage==2&&editing==w){direction();return;}
        new AlertDialog.Builder(activity).setTitle("Mechanik").setMessage("Ein Schalter bewegt bis zu sieben feste Blöcke. Noch einmal schalten bewegt sie zurück. Blöcke und Spieler im Weg stoppen die Bewegung.")
            .setPositiveButton("Neue Mechanik",(d,n)->{cancel();editing=w;stage=1;activity.game.message(hint());})
            .setNeutralButton("Verbindungen entfernen",(d,n)->activity.handler.post(this::removeMenu)).setNegativeButton(stage>0?"Auswahl abbrechen":"Zurück",(d,n)->cancel()).setOnDismissListener(d->activity.resumePlay()).show();
    }
    void direction(){if(blocks.isEmpty()){activity.toast("Erst mindestens einen Block mit + markieren");activity.resumePlay();return;}
        new AlertDialog.Builder(activity).setTitle("In welche Richtung?").setItems(Mechanics.LABELS,(d,n)->activity.handler.post(()->distance(n))).setNegativeButton("Weiter markieren",null).setNeutralButton("Abbrechen",(d,n)->cancel()).setOnDismissListener(d->activity.resumePlay()).show();
    }
    void distance(int direction){activity.paused=true;new AlertDialog.Builder(activity).setTitle("Wie weit?").setItems(new String[]{"1 Block","2 Blöcke","3 Blöcke","4 Blöcke"},(d,n)->save(direction,n+1)).setNegativeButton("Zurück",(d,n)->activity.handler.post(this::direction)).setOnDismissListener(d->activity.resumePlay()).show();}
    synchronized void save(int direction,int distance){if(editing==null||button==null||blocks.isEmpty()){cancel();return;}World w=editing;Mechanics.Block selected=button;List<Mechanics.Block> group=new ArrayList<>(blocks);activity.game.gl.queueEvent(()->{try{synchronized(w){if(w!=activity.game.world)return;Mechanics.Link link=new Mechanics.Link(selected,group,direction,distance,false);if(w.online)online().configure(link);else w.mechanics.install(w,link);}cancel();if(!w.online)activity.game.message("Mechanik bereit · Schalter anvisieren und ⚙ antippen");}catch(Exception e){activity.game.message(e.getMessage());}});}
    OnlineMechanics online(){OnlineSession s=activity.online==null?null:activity.online.session;if(s==null||!(s.modern instanceof OnlineV6))throw new IllegalArgumentException("Mechanik braucht die aktuelle Online-Version");return new OnlineMechanics((OnlineV6)s.modern);}
    void use(){if(stage!=0){open();return;}activity.game.gl.queueEvent(()->{World w=activity.game.world;if(w==null)return;synchronized(w){int[] hit=w.ray(6);Mechanics.Link link=hit==null?null:w.mechanics.links.get(w.idx(hit[0],hit[1],hit[2]));if(link==null){activity.runOnUiThread(this::open);return;}try{if(w.online)online().toggle(link);else{List<Mechanics.Block> changed=Mechanics.plan(link,Mechanics.space(w));w.mechanics.apply(w,link);for(Mechanics.Block b:changed)activity.game.renderer.dirty(b.x,b.z);activity.game.message(link.moved?"Zurückgeschoben":"Verschoben");}}catch(Exception e){activity.game.message(e.getMessage());}}});}
    void removeMenu(){World w=activity.world;List<Mechanics.Link> list;synchronized(w){list=new ArrayList<>(w.mechanics.links.values());}String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++){Mechanics.Block b=list.get(i).button;labels[i]="Schalter "+b.x+" / "+b.y+" / "+b.z;}
        activity.paused=true;AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle("Verbindung entfernen").setNegativeButton("Zurück",null).setOnDismissListener(d->activity.resumePlay());if(list.isEmpty())dialog.setMessage("Hier sind noch keine Schalter eingerichtet.");else dialog.setItems(labels,(d,n)->activity.game.gl.queueEvent(()->{synchronized(w){try{Mechanics.Link link=list.get(n);if(w.online)online().remove(link);else w.mechanics.links.remove(w.idx(link.button.x,link.button.y,link.button.z));activity.game.message("Verbindung entfernt · Blöcke bleiben stehen");}catch(Exception e){activity.game.message(e.getMessage());}}}));dialog.show();
    }
}
