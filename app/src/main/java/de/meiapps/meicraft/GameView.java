package de.meiapps.meicraft;

import android.content.*;
import android.graphics.*;
import android.opengl.GLSurfaceView;
import android.view.*;
import android.widget.FrameLayout;

final class GameView {
    final FrameLayout root;
    final MainActivity activity; final GLSurfaceView gl;final VoxelRenderer renderer;final Hud hud;
    volatile World world,renderedWorld;
    volatile int[] target;
    volatile float forward,strafe,vertical;
    volatile float turnX,turnY,miningProgress;volatile boolean mineHeld;
    final Object inputLock=new Object();
    float velocityY;boolean grounded;
    int moveId=-1,lookId=-1,verticalId=-1,mineId=-1;float lookX,lookY,knobX,knobY,mineTime;int[] miningTarget;volatile String status="";volatile long statusUntil;
    static final int[] COLORS={0,0xff79b94f,0xff986a45,0xff8c9397,0xffdfcd91,0xff9c7246,0xff47944d,0xffbd6954,0xffb9e1e8,0xffbf955f,0xff3c9dbd,0xffffdb76,0xffe8f4f7,0xffaa8b77,0xffbac4ca,0xffff6624};
    GameView(MainActivity activity,World world){this.activity=activity;root=new FrameLayout(activity);this.world=world;
        gl=new GLSurfaceView(activity);gl.setEGLContextClientVersion(2);gl.setPreserveEGLContextOnPause(true);renderer=new VoxelRenderer(this);gl.setRenderer(renderer);root.addView(gl,new FrameLayout.LayoutParams(-1,-1));hud=new Hud(activity);root.addView(hud,new FrameLayout.LayoutParams(-1,-1));root.setKeepScreenOn(true);
    }
    Context getContext(){return activity;}
    void replace(World next){clearInput();world=next;velocityY=0;target=null;}
    void clearInput(){synchronized(inputLock){forward=strafe=vertical=turnX=turnY=miningProgress=0;mineHeld=false;moveId=lookId=verticalId=mineId=-1;knobX=knobY=0;}endMining();}
    void endMining(){mineHeld=false;miningProgress=0;gl.queueEvent(()->{mineTime=0;miningTarget=null;});}
    void message(String text){status=text;statusUntil=System.currentTimeMillis()+1800;}
    void edit(boolean place){gl.queueEvent(()->{World w=world;synchronized(w){int[] hit=w.ray(6);if(hit==null){message("Näher an einen Block herangehen");return;}if(!place&&Nature.liquid(w.get(hit[0],hit[1],hit[2]))){message(w.scoopLiquid(hit[0],hit[1],hit[2]));renderer.dirty(hit[0],hit[2]);return;}int x=hit[place?3:0],y=hit[place?4:1],z=hit[place?5:2];if(place&&w.overlapsPlayer(x,y,z)){message("Hier stehst du selbst");return;}if(place&&w.story&&w.progress.inventory[w.selected]==0){message("Erst "+World.NAMES[w.selected]+" sammeln oder herstellen");return;}if(place?w.place(x,y,z,w.selected):w.mine(x,y,z)){renderer.dirty(x,z);}else message(place?"Hier kannst du keinen Block setzen":w.miningProblem(x,y,z));}});}
    void mining(float dt){World w=world;if(!w.story||!mineHeld){mineTime=0;miningTarget=null;miningProgress=0;return;}if(w.survival.attack(w)){mineTime=0;miningTarget=null;miningProgress=0;return;}int[] hit=w.ray(6);if(hit!=null&&Nature.liquid(w.get(hit[0],hit[1],hit[2]))){if(mineTime==0){message(w.scoopLiquid(hit[0],hit[1],hit[2]));renderer.dirty(hit[0],hit[2]);}mineTime+=dt;miningProgress=0;return;}if(hit==null){mineTime=0;miningTarget=null;miningProgress=0;return;}String problem=w.miningProblem(hit[0],hit[1],hit[2]);if(!problem.isEmpty()){mineTime=0;miningProgress=0;miningTarget=null;if(statusUntil<System.currentTimeMillis())message(problem);return;}
        if(miningTarget==null||hit[0]!=miningTarget[0]||hit[1]!=miningTarget[1]||hit[2]!=miningTarget[2]){mineTime=0;miningTarget=hit;}
        mineTime+=dt;float duration=w.progress.seconds(w.get(hit[0],hit[1],hit[2]));miningProgress=Math.min(1,mineTime/duration);
        if(mineTime>=duration){if(w.mine(hit[0],hit[1],hit[2]))renderer.dirty(hit[0],hit[2]);else message("Dein Vorrat ist voll");mineTime=0;miningProgress=0;miningTarget=null;}
    }
    void tick(float dt){World w=world;float tx,ty;synchronized(inputLock){tx=turnX;ty=turnY;turnX=turnY=0;}w.yaw-=tx*.004f;w.pitch=Math.max(-1.48f,Math.min(1.48f,w.pitch-ty*.004f));
        float fw=forward,st=strafe,len=(float)Math.sqrt(fw*fw+st*st);if(len>1){fw/=len;st/=len;}
        float speed=w.flying?7:4.3f;float dx=(- (float)Math.sin(w.yaw)*fw+(float)Math.cos(w.yaw)*st)*speed;float dz=(- (float)Math.cos(w.yaw)*fw-(float)Math.sin(w.yaw)*st)*speed;
        int steps=Math.max(1,(int)Math.ceil(dt/.012f));float step=dt/steps;
        for(int i=0;i<steps;i++){
            if(!w.collides(w.x+dx*step,w.y,w.z))w.x+=dx*step;
            if(!w.collides(w.x,w.y,w.z+dz*step))w.z+=dz*step;
            boolean swimming=w.get((int)Math.floor(w.x),(int)Math.floor(w.y-1.55f),(int)Math.floor(w.z))==10;
            if(w.flying){velocityY=vertical*5.5f;}else if(swimming){velocityY=vertical>0?7:Math.max(-1.8f,velocityY-3*step);}else{if(vertical>0&&grounded){velocityY=7;grounded=false;}velocityY=Math.max(-20,velocityY-20*step);}
            float yy=w.y+velocityY*step;if(!w.collides(w.x,yy,w.z)){w.y=yy;grounded=false;}else{grounded=velocityY<0;velocityY=0;}
        }
        w.nature.tick(w,dt);
        if(w.survival.tick(w,dt)){velocityY=0;clearInput();}
        mining(dt);
        if(!w.survival.notice.isEmpty()){message(w.survival.notice);w.survival.notice="";}
    }
    final class Hud extends View {
        final Paint p=new Paint(3);final RectF rect=new RectF();float scale=1,offsetX,offsetY;float joyX=111,joyY=390,designW=960,designH=540,mineX=826,placeX=897,actionY=300,upX=897,downX=826,flyY=390,barX=260,barY=472,paletteX=716;
        Hud(Context context){super(context);}
        void box(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,13,13,p);}
        void text(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText(s,x,y,p);}
        void center(Canvas c,String s,float x,float y,float size,int color){p.setTextSize(size);text(c,s,x-p.measureText(s)/2,y,size,color);}
        void layoutHud(){
            int left=0,right=0,top=0,bottom=0;
            if(android.os.Build.VERSION.SDK_INT>=28){WindowInsets insets=getRootWindowInsets();if(insets!=null&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=cutout.getSafeInsetLeft();right=cutout.getSafeInsetRight();top=cutout.getSafeInsetTop();bottom=cutout.getSafeInsetBottom();}}
            boolean portrait=getHeight()-top-bottom>getWidth()-left-right;
            designW=portrait?540:960;designH=portrait?960:540;
            joyY=portrait?810:390;
            mineX=portrait?379:826;placeX=portrait?489:897;actionY=portrait?660:300;upX=portrait?489:897;downX=portrait?379:826;flyY=portrait?790:390;barX=portrait?26:260;barY=portrait?900:472;paletteX=portrait?474:716;
            scale=Math.min((getWidth()-left-right)/designW,(getHeight()-top-bottom)/designH);offsetX=left+(getWidth()-left-right-designW*scale)/2;offsetY=top+(getHeight()-top-bottom-designH*scale)/2;
        }
        @Override protected void onDraw(Canvas c){World drawing=world;synchronized(drawing){super.onDraw(c);layoutHud();c.save();c.translate(offsetX,offsetY);c.scale(scale,scale);
            box(c,20,16,221,55,0xbb153d42);text(c,"meiCraft",34,41,23,0xffffdb76);text(c,world.story?"STORY · "+Story.TOOLS[world.progress.tool()]:"OFFLINE · KREATIV",35,59,11,0xffd1e8e6);
            box(c,designW-93,18,72,49,0xcc153d42);center(c,"☰",designW-57,51,25,Color.WHITE);
            World w=world;int[] hit=target;
            {box(c,250,18,80,49,w.story&&w.progress.ready()?0xfff6d16c:0xcc153d42);center(c,"STORY",290,49,14,w.story&&w.progress.ready()?0xff153d42:Color.WHITE);if(w.story){float yy=designW<960?122:80;box(c,20,yy,designW<960?500:410,48,0xbb153d42);text(c,w.progress.goal(),31,yy+20,12,Color.WHITE);text(c,"− halten: abbauen · STORY: Auftrag / Rezepte",31,yy+38,11,0xffd1e8e6);}}
            if(hit!=null){box(c,(designW-266)/2,designW<960?82:20,266,31,0x99152c32);center(c,(w.nature.spring(hit[0],hit[1],hit[2])?"Lavaquelle · nachfüllbar":w.nature.stream(hit[0],hit[1],hit[2])?World.NAMES[w.get(hit[0],hit[1],hit[2])]+" · fließend":World.NAMES[w.get(hit[0],hit[1],hit[2])]),designW/2,designW<960?103:41,15,Color.WHITE);}
            float cx=(getWidth()/2f-offsetX)/scale,cy=(getHeight()/2f-offsetY)/scale;p.setColor(0xddffffff);p.setStrokeWidth(2);c.drawLine(cx-10,cy,cx-3,cy,p);c.drawLine(cx+3,cy,cx+10,cy,p);c.drawLine(cx,cy-10,cx,cy-3,p);c.drawLine(cx,cy+3,cx,cy+10,p);
            joystick(c,joyX,joyY,knobX,knobY,"BEWEGEN");
            button(c,mineX,actionY,"−",w.story&&w.survival.aimed(w)!=null?"ANGRIFF":hit!=null&&Nature.liquid(w.get(hit[0],hit[1],hit[2]))?"EIMER":"ABBAUEN");button(c,placeX,actionY,"+","SETZEN");
            button(c,upX,flyY,"↑",w.flying?"HOCH":"SPRUNG");if(w.flying)button(c,downX,flyY,"↓","RUNTER");
            if(w.story&&miningProgress>0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xffffd26b);rect.set(cx-18,cy-18,cx+18,cy+18);c.drawArc(rect,-90,360*miningProgress,false,p);p.setStyle(Paint.Style.FILL);}
            int first=hotbarFirst();for(int j=0;j<8;j++){int b=first+j;float xx=barX+j*56;box(c,xx,barY,49,52,b==w.selected?0xfff6d16c:0xbb193c43);box(c,xx+9,barY+8,31,27,COLORS[b]);center(c,""+(w.story?w.progress.inventory[b]:b),xx+24.5f,barY+45,10,b==w.selected?0xff193c43:Color.WHITE);}
            box(c,paletteX,barY,49,52,0xbb193c43);center(c,"…",paletteX+24,barY+32,25,Color.WHITE);
            center(c,World.NAMES[w.selected]+(w.story?" ("+w.progress.inventory[w.selected]+")":"")+"  ·  "+(w.flying?"Fliegen":"Laufen"),designW/2,barY-14,14,Color.WHITE);
            if(w.story){
                box(c,designW-310,80,290,48,0xcc153d42);
                center(c,w.survival.phase()+" · "+w.survival.secondsToChange()+" s · Gegner "+(w.survival.enemiesEnabled?w.survival.mobs.size():"aus"),designW-165,99,12,Color.WHITE);
                center(c,"Leben "+w.survival.health+"/10 · "+(w.progress.sword?"Eisenschwert":"Hand"),designW-165,118,12,w.survival.health<=4?0xffff8b75:0xffffdb76);
            }
            if(statusUntil>System.currentTimeMillis()){box(c,(designW-420)/2,w.story?(designW<960?186:139):130,420,35,0xcc153d42);center(c,status,designW/2,(w.story?(designW<960?186:139):130)+23,14,Color.WHITE);}
            c.restore();
        }}
        int hotbarFirst(){return world.selected<=8?1:world.selected<=12?5:World.NAMES.length-8;}
        void joystick(Canvas c,float x,float y,float dx,float dy,String label){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0x88ffffff);c.drawCircle(x,y,68,p);p.setStyle(Paint.Style.FILL);p.setColor(0x55122d34);c.drawCircle(x,y,66,p);p.setColor(0x99ffffff);c.drawCircle(x+dx,y+dy,27,p);center(c,label,x,y+90,10,0xffe1eeee);
        }
        void button(Canvas c,float x,float y,String symbol,String label){box(c,x-29,y-29,58,58,0xaa173b43);center(c,symbol,x,y+10,31,Color.WHITE);center(c,label,x,y+44,9,Color.WHITE);}
        boolean at(float x,float y,float a,float b,float radius){return Math.abs(x-a)<radius&&Math.abs(y-b)<radius;}
        @Override public boolean onTouchEvent(MotionEvent event){
            layoutHud();float s=scale,ox=offsetX,oy=offsetY;
            int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);float x=(event.getX(index)-ox)/s,y=(event.getY(index)-oy)/s;
            if(action==MotionEvent.ACTION_CANCEL){clearInput();return true;}
            if(activity.paused){clearInput();return true;}
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
                if(x>=250&&x<=330&&y>=18&&y<67){clearInput();if(world.story)activity.storyMenu();else activity.chooseMode();return true;}
                if(x>designW-100&&y<78){clearInput();activity.menu();return true;}
                if(y>=barY&&y<=barY+58&&x>=barX&&x<barX+448){int b=hotbarFirst()+(int)((x-barX)/56);synchronized(world){world.selected=b;}return true;}
                if(x>=paletteX&&x<=paletteX+49&&y>=barY){clearInput();activity.palette();return true;}
                if(at(x,y,mineX,actionY,33)){if(world.story){mineId=id;mineHeld=true;}else edit(false);return true;}if(at(x,y,placeX,actionY,33)){edit(true);return true;}
                if(at(x,y,upX,flyY,34)||world.flying&&at(x,y,downX,flyY,34)){verticalId=id;vertical=x>(upX+downX)/2?1:-1;return true;}
                if(x<240&&y>(designH==540?250:designH/2)&&moveId<0){moveId=id;updateMove(x,y);return true;}
                if(lookId<0){lookId=id;lookX=x;lookY=y;return true;}
            }
            if(action==MotionEvent.ACTION_MOVE){for(int n=0;n<event.getPointerCount();n++){int pointer=event.getPointerId(n);float a=(event.getX(n)-ox)/s,b=(event.getY(n)-oy)/s;if(pointer==moveId)updateMove(a,b);if(pointer==lookId){synchronized(inputLock){turnX+=a-lookX;turnY+=b-lookY;}lookX=a;lookY=b;}if(pointer==mineId&&!at(a,b,mineX,actionY,60)){endMining();}}}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){if(id==moveId){moveId=-1;forward=strafe=0;knobX=knobY=0;}if(id==lookId)lookId=-1;if(id==mineId){mineId=-1;endMining();}if(id==verticalId){verticalId=-1;vertical=0;}}
            if(action==MotionEvent.ACTION_UP)performClick();
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        void updateMove(float x,float y){float dx=x-joyX,dy=y-joyY,n=(float)Math.sqrt(dx*dx+dy*dy);if(n>52){dx*=52/n;dy*=52/n;}knobX=dx;knobY=dy;strafe=dx/52;forward=-dy/52;}
    }
}

