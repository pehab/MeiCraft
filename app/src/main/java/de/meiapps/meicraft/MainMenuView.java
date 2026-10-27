package de.meiapps.meicraft;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;

/** The persistent, paused home screen. World operations live here rather than in the play menu. */
final class MainMenuView extends ScrollView {
    private final MainActivity activity;
    MainMenuView(MainActivity activity){
        super(activity);this.activity=activity;setFillViewport(true);setBackgroundColor(0xff102e34);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_VERTICAL);int pad=dp(18);root.setPadding(pad,pad,pad,pad);addView(root);
        TextView title=label("meiCraft",38,0xffffdb76);title.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));root.addView(title);
        root.addView(label("Deine Welten. Deine Geschichten.",14,0xffb6d9d6));
        String name=activity.active==null?"Erstelle deine erste Welt oder empfange eine Weltkopie.":"Zuletzt gespielt: "+activity.active.name;
        TextView last=label(name,14,Color.WHITE);last.setSingleLine(true);last.setEllipsize(TextUtils.TruncateAt.END);last.setPadding(0,dp(9),0,dp(8));root.addView(last);
        LinearLayout columns=new LinearLayout(activity);root.addView(columns);
        LinearLayout left=column(),right=column();columns.addView(left,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(10);columns.addView(right,rp);
        Button resume=button(left,"Weiterbauen",activity::resumeFromHome,true);resume.setEnabled(activity.world!=null);resume.setAlpha(activity.world==null?.4f:1);
        button(left,"Welten verwalten",activity::manageWorlds,false);
        button(left,"Neue Welt",activity::newWorld,false);
        button(left,"Welt per QR empfangen",()->activity.qrTransfer(false),false);
        button(right,"Welt per QR teilen",activity::pickShareWorld,false);
        button(right,"Updates suchen",()->activity.updates.check(true),false);
        button(right,"Steuerung",activity::help,false);
        button(right,"Über meiCraft",activity::about,false);
        TextView footer=label("OFFLINE · Keine Konten · Keine Werbung",11,0xff91b7b4);footer.setPadding(0,dp(8),0,0);root.addView(footer);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(color);return view;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private Button button(LinearLayout parent,String text,Runnable action,boolean primary){
        Button view=new Button(activity);view.setText(text);view.setAllCaps(false);view.setTextSize(14);view.setTextColor(primary?0xff153d42:Color.WHITE);view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?0xfff6d16c:0xff21464d));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.bottomMargin=dp(4);parent.addView(view,params);view.setOnClickListener(v->action.run());return view;
    }
}
