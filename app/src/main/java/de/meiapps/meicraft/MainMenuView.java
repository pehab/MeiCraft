package de.meiapps.meicraft;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Paused home screen: a quiet landscape, one primary action, and grouped world tools. */
final class MainMenuView extends ScrollView {
    private static final int INK=0xffeef4e9, MUTED=0xffb2c5ba, GOLD=0xffffd779;
    private final MainActivity activity;
    private final LinearLayout page,heroContent,tools;
    private final FrameLayout hero;
    private boolean wide;

    MainMenuView(Context context){
        super(context);activity=(MainActivity)context;
        setFillViewport(true);setBackgroundColor(0xff101f24);setClipToPadding(false);
        page=column();page.setPadding(dp(16),dp(16),dp(16),dp(12));addView(page);
        hero=new FrameLayout(context);hero.setBackground(shape(0xff163b3d,20,0));hero.setClipToOutline(true);
        hero.addView(new Landscape(context),new FrameLayout.LayoutParams(-1,-1));
        heroContent=column();heroContent.setPadding(dp(22),dp(22),dp(22),dp(18));
        TextView eyebrow=text("MEIAPPS · BLOCK FÜR BLOCK",10,GOLD);eyebrow.setLetterSpacing(.14f);heroContent.addView(eyebrow);
        TextView title=text("meiCraft",38,INK);title.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));heroContent.addView(title);
        heroContent.addView(text("Deine Welten.\nDeine Geschichten.",17,0xffc7dfd4));
        View space=new View(context);heroContent.addView(space,new LinearLayout.LayoutParams(1,dp(42),1));
        hero.addView(heroContent,new FrameLayout.LayoutParams(-1,-1));

        tools=column();
        LinearLayout heading=row();TextView headingText=text("Deine Welten",20,INK);bold(headingText);heading.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=text("v"+activity.versionName(),11,MUTED);version.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);heading.addView(version,new LinearLayout.LayoutParams(-2,dp(30)));tools.addView(heading);
        TextView sub=text("Bauen. Entdecken. Gemeinsam Ideen teilen.",12,MUTED);sub.setPadding(0,dp(2),0,dp(12));tools.addView(sub);

        LinearLayout current=column();current.setPadding(dp(14),dp(12),dp(14),dp(12));current.setBackground(shape(0xff223b3e,14,0xff39524c));
        TextView caption=text(activity.world==null?"DEIN ABENTEUER BEGINNT HIER":"ZULETZT GESPIELT",10,GOLD);caption.setLetterSpacing(.1f);current.addView(caption);
        TextView name=text(activity.active==null?"Platz für deine erste Welt":activity.active.name,18,INK);bold(name);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(0,dp(3),0,dp(2));current.addView(name);
        current.addView(text(activity.world==null?"Neue Welt erstellen oder per QR empfangen.":activity.world.story?"Story · Sammeln, bauen und überleben":"Kreativ · Deiner Fantasie freien Lauf lassen",11,MUTED));
        Button resume=button("Weiterbauen",activity::resumeFromHome,true);resume.setEnabled(activity.world!=null);resume.setAlpha(activity.world==null?.38f:1);
        LinearLayout.LayoutParams resumeParams=new LinearLayout.LayoutParams(-1,dp(48));resumeParams.topMargin=dp(10);current.addView(resume,resumeParams);heroContent.addView(current);

        TextView section=text("DEIN NÄCHSTES ABENTEUER",10,MUTED);section.setLetterSpacing(.12f);section.setPadding(0,dp(5),0,dp(9));tools.addView(section);
        LinearLayout first=row();addTile(first,"Neue Welt","Frisch ins Abenteuer",1,activity::newWorld,false);addTile(first,"Welten verwalten","Laden, umbenennen, löschen",0,activity::manageWorlds,true);tools.addView(first);
        LinearLayout second=row();addTile(second,"Welt per QR teilen","Eine Welt weitergeben",2,activity::pickShareWorld,false);addTile(second,"Welt per QR empfangen","Eine Welt entdecken",3,()->activity.qrTransfer(false),true);
        LinearLayout.LayoutParams secondParams=new LinearLayout.LayoutParams(-1,-2);secondParams.topMargin=dp(8);tools.addView(second,secondParams);

        LinearLayout utilities=row();utilities.setPadding(0,dp(9),0,0);
        for(String label:new String[]{"Steuerung","Updates suchen","Über meiCraft"}){
            Runnable action=label.equals("Steuerung")?activity::help:label.equals("Updates suchen")?()->activity.updates.check(true):activity::about;
            Button utility=button(label,action,false);utilities.addView(utility,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        tools.addView(utilities);
        TextView footer=text("OFFLINE SPIELEN  ·  OHNE WERBUNG  ·  OHNE KONTEN",9,MUTED);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(4),0,0);tools.addView(footer);
        arrange(false);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        boolean next=MeasureSpec.getSize(widthSpec)>=dp(600);
        if(next!=wide)arrange(next);
        super.onMeasure(widthSpec,heightSpec);
    }
    private void arrange(boolean landscape){
        wide=landscape;page.removeAllViews();page.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        if(wide){
            LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,.38f);page.addView(hero,left);
            LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,.62f);right.leftMargin=dp(20);page.addView(tools,right);
            heroContent.setMinimumHeight(dp(320));
        }else{
            page.addView(hero,new LinearLayout.LayoutParams(-1,dp(410)));heroContent.setMinimumHeight(0);
            LinearLayout.LayoutParams below=new LinearLayout.LayoutParams(-1,-2);below.topMargin=dp(18);page.addView(tools,below);
        }
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private void bold(TextView v){v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private RippleDrawable touch(int color,int border){return new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape(color,12,border),shape(-1,12,0));}
    private Button button(String label,Runnable action,boolean primary){
        Button v=new Button(activity);v.setText(label);v.setAllCaps(false);v.setTextSize(primary?16:11);bold(v);v.setTextColor(primary?0xff233c31:MUTED);
        v.setMinWidth(0);v.setMinimumWidth(0);v.setPadding(dp(4),0,dp(4),0);
        v.setBackground(touch(primary?GOLD:Color.TRANSPARENT,0));v.setOnClickListener(view->action.run());return v;
    }
    private void addTile(LinearLayout parent,String title,String subtitle,int icon,Runnable action,boolean right){
        LinearLayout tile=row();tile.setGravity(Gravity.CENTER_VERTICAL);tile.setPadding(dp(10),dp(9),dp(9),dp(9));tile.setBackground(touch(0xff1a3035,0xff2e4649));
        Icon symbol=new Icon(activity,icon);tile.addView(symbol,new LinearLayout.LayoutParams(dp(25),dp(25)));
        LinearLayout words=column();LinearLayout.LayoutParams wordParams=new LinearLayout.LayoutParams(0,-2,1);wordParams.leftMargin=dp(9);tile.addView(words,wordParams);
        TextView label=text(title,13,INK);bold(label);words.addView(label);
        TextView hint=text(subtitle,10,MUTED);hint.setPadding(0,dp(3),0,0);words.addView(hint);
        tile.setMinimumHeight(dp(66));tile.setFocusable(true);tile.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(right)params.leftMargin=dp(8);parent.addView(tile,params);
    }

    /** Small drawn symbols avoid emoji and font-dependent icons. */
    private static final class Icon extends View {
        private final Paint paint=new Paint(3);private final int kind;
        Icon(Context context,int kind){super(context);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(GOLD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind==0){Path p=new Path();p.moveTo(3,7);p.lineTo(3,4);p.lineTo(10,4);p.lineTo(13,7);p.lineTo(21,7);p.lineTo(21,20);p.lineTo(3,20);p.close();canvas.drawPath(p,paint);canvas.drawLine(3,10,21,10,paint);}
            else if(kind==1){canvas.drawRoundRect(3,3,21,21,3,3,paint);canvas.drawLine(12,7,12,17,paint);canvas.drawLine(7,12,17,12,paint);}
            else{canvas.drawRect(2,2,8,8,paint);canvas.drawRect(2,15,8,21,paint);canvas.drawRect(15,2,21,8,paint);canvas.drawLine(13,17,23,17,paint);float end=kind==2?23:13,back=kind==2?19:17;canvas.drawLine(end,17,back,13,paint);canvas.drawLine(end,17,back,21,paint);}
            canvas.restore();
        }
    }

    /** A deterministic block landscape, rendered once with the static menu. */
    private static final class Landscape extends View {
        private final Paint p=new Paint();
        Landscape(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void box(Canvas c,float l,float t,float r,float b,int color){p.setColor(color);c.drawRect(l,t,r,b,p);}
        private void land(Canvas c,int[] heights,int color){for(int i=0;i<heights.length;i++)box(c,i*24,heights[i],(i+1)*24,460,color);}
        private void cube(Canvas c,float x,float y,float w,int top,int front,int side){
            Path face=new Path();face.moveTo(x,y);face.lineTo(x+w,y-w/2);face.lineTo(x+2*w,y);face.lineTo(x+w,y+w/2);face.close();p.setColor(top);c.drawPath(face,p);
            face.reset();face.moveTo(x,y);face.lineTo(x+w,y+w/2);face.lineTo(x+w,y+w*1.5f);face.lineTo(x,y+w);face.close();p.setColor(front);c.drawPath(face,p);
            face.reset();face.moveTo(x+w,y+w/2);face.lineTo(x+2*w,y);face.lineTo(x+2*w,y+w);face.lineTo(x+w,y+w*1.5f);face.close();p.setColor(side);c.drawPath(face,p);
        }
        @Override protected void onDraw(Canvas c){
            float scale=Math.max(getWidth()/320f,getHeight()/460f);c.save();c.translate((getWidth()-320*scale)/2,(getHeight()-460*scale)/2);c.scale(scale,scale);
            p.setShader(new LinearGradient(0,0,320,460,new int[]{0xff163c42,0xff386c68,0xffcfb779},null,Shader.TileMode.CLAMP));c.drawRect(0,0,320,460,p);p.setShader(null);
            box(c,235,188,267,220,0xfff5dba0);box(c,50,205,53,208,0xffc6d5b3);box(c,278,155,280,157,0xffc6d5b3);
            land(c,new int[]{294,294,270,246,246,270,294,270,246,246,222,246,270,294},0xff3e6c63);
            land(c,new int[]{342,318,318,294,318,342,342,342,318,318,294,318,342,342},0xff28534e);
            box(c,0,360,320,460,0xff408079);box(c,185,370,263,373,0xff81b4a0);box(c,215,382,285,384,0xff70a38d);box(c,26,395,95,398,0xff70a38d);
            cube(c,54,325,42,0xff89ad61,0xff526c46,0xff394f3c);cube(c,12,346,42,0xff789e55,0xff48643e,0xff304a39);
            cube(c,96,346,42,0xff88b266,0xff526d45,0xff364e3c);cube(c,54,367,42,0xff759854,0xff46613c,0xff304937);
            box(c,81,280,90,337,0xff604e39);box(c,57,260,114,289,0xff3a7050);box(c,66,243,105,266,0xff54845a);box(c,75,234,96,250,0xff6c975d);
            cube(c,223,335,18,0xffffdf81,0xffbf984e,0xff997440);box(c,241,355,245,359,0xffffedb5);
            p.setShader(new LinearGradient(0,0,0,460,new int[]{0x99101f24,0x00101f24,0x00101f24,0xcc101f24},new float[]{0,.42f,.76f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,320,460,p);p.setShader(null);c.restore();
        }
    }
}
