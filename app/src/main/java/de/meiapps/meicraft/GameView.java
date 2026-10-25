package de.meiapps.meicraft;

import android.content.*;
import android.graphics.*;
import android.opengl.GLSurfaceView;
import android.view.*;
import android.widget.FrameLayout;

final class GameView extends FrameLayout {
    final MainActivity activity; final GLSurfaceView gl;final VoxelRenderer renderer;final Hud hud;
    volatile World world,renderedWorld;
    volatile int[] target;
    volatile float forward,strafe,vertical;
    volatile float lookHorizontal,lookVertical;
    final Object inputLock=new Object();
    float velocityY;boolean grounded;
    int moveId=-1,lookId=-1,verticalId=-1;float knobX,knobY,lookKnobX,lookKnobY;volatile String status="";long statusUntil;
    static final int[] COLORS={0,0xff79b94f,0xff986a45,0xff8c9397,0xffdfcd91,0xff9c7246,0xff47944d,0xffbd6954,0xffb9e1e8,0xffbf955f,0xff3c9dbd,0xffffdb76,0xffe8f4f7};
    GameView(MainActivity activity,World world){super(activity);this.activity=activity;this.world=world;
        gl=new GLSurfaceView(activity);gl.setEGLContextClientVersion(2);gl.setPreserveEGLContextOnPause(true);renderer=new VoxelRenderer(this);gl.setRenderer(renderer);addView(gl,new LayoutParams(-1,-1));hud=new Hud(activity);addView(hud,new LayoutParams(-1,-1));setKeepScreenOn(true);
    }
    void replace(World next){clearInput();world=next;velocityY=0;target=null;}
    void clearInput(){synchronized(inputLock){forward=strafe=vertical=lookHorizontal=lookVertical=0;moveId=lookId=verticalId=-1;knobX=knobY=lookKnobX=lookKnobY=0;}}
    void message(String text){status=text;statusUntil=System.currentTimeMillis()+1800;}
    void edit(boolean place){gl.queueEvent(()->{World w=world;synchronized(w){int[] hit=w.ray(6);if(hit==null){message("Näher an einen Block herangehen");return;}int x=hit[place?3:0],y=hit[place?4:1],z=hit[place?5:2];if(place&&w.overlapsPlayer(x,y,z)){message("Hier stehst du selbst");return;}if(place&&w.get(x,y,z)!=0)return;if(w.set(x,y,z,place?w.selected:0)){renderer.dirty(x,z);}else message("Hier ist die Weltgrenze");}});}
    void tick(float dt){World w=world;w.yaw-=lookHorizontal*2.2f*dt;w.pitch=Math.max(-1.48f,Math.min(1.48f,w.pitch-lookVertical*1.8f*dt));
        float fw=forward,st=strafe,len=(float)Math.sqrt(fw*fw+st*st);if(len>1){fw/=len;st/=len;}
        float speed=w.flying?7:4.3f;float dx=(- (float)Math.sin(w.yaw)*fw+(float)Math.cos(w.yaw)*st)*speed;float dz=(- (float)Math.cos(w.yaw)*fw-(float)Math.sin(w.yaw)*st)*speed;
        int steps=Math.max(1,(int)Math.ceil(dt/.012f));float step=dt/steps;
        for(int i=0;i<steps;i++){
            if(!w.collides(w.x+dx*step,w.y,w.z))w.x+=dx*step;
            if(!w.collides(w.x,w.y,w.z+dz*step))w.z+=dz*step;
            if(w.flying){velocityY=vertical*5.5f;}else{if(vertical>0&&grounded){velocityY=7;grounded=false;}velocityY=Math.max(-20,velocityY-20*step);}
            float yy=w.y+velocityY*step;if(!w.collides(w.x,yy,w.z)){w.y=yy;grounded=false;}else{grounded=velocityY<0;velocityY=0;}
        }
    }
    final class Hud extends View {
        final Paint p=new Paint(3);final RectF rect=new RectF();float scale=1,offsetX,offsetY;final float joyX=111,joyY=390,lookJoyX=849,lookJoyY=390;
        Hud(Context context){super(context);}
        void box(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,13,13,p);}
        void text(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));c.drawText(s,x,y,p);}
        void center(Canvas c,String s,float x,float y,float size,int color){p.setTextSize(size);text(c,s,x-p.measureText(s)/2,y,size,color);}
        void layoutHud(){
            int left=0,right=0,top=0,bottom=0;
            if(android.os.Build.VERSION.SDK_INT>=28){WindowInsets insets=getRootWindowInsets();if(insets!=null&&insets.getDisplayCutout()!=null){DisplayCutout cutout=insets.getDisplayCutout();left=cutout.getSafeInsetLeft();right=cutout.getSafeInsetRight();top=cutout.getSafeInsetTop();bottom=cutout.getSafeInsetBottom();}}
            scale=Math.min((getWidth()-left-right)/960f,(getHeight()-top-bottom)/540f);offsetX=left+(getWidth()-left-right-960*scale)/2;offsetY=top+(getHeight()-top-bottom-540*scale)/2;
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);layoutHud();c.save();c.translate(offsetX,offsetY);c.scale(scale,scale);
            box(c,20,16,221,55,0xbb153d42);text(c,"meiCraft",34,41,23,0xffffdb76);text(c,"OFFLINE · KREATIV",35,59,11,0xffd1e8e6);
            box(c,867,18,72,49,0xcc153d42);center(c,"☰",903,51,25,Color.WHITE);
            World w=world;int[] hit=target;
            if(hit!=null){box(c,347,20,266,31,0x99152c32);center(c,World.NAMES[w.get(hit[0],hit[1],hit[2])],480,41,15,Color.WHITE);}
            p.setColor(0xddffffff);p.setStrokeWidth(2);c.drawLine(470,270,477,270,p);c.drawLine(483,270,490,270,p);c.drawLine(480,260,480,267,p);c.drawLine(480,273,480,280,p);
            joystick(c,joyX,joyY,knobX,knobY,"BEWEGEN");
            joystick(c,lookJoyX,lookJoyY,lookKnobX,lookKnobY,"UMSEHEN");
            button(c,790,250,"−","ABBAUEN");button(c,899,250,"+","SETZEN");
            button(c,737,355,"↑",w.flying?"HOCH":"SPRUNG");if(w.flying)button(c,737,425,"↓","RUNTER");
            int first=w.selected<=8?1:5;for(int j=0;j<8;j++){int b=first+j;float xx=260+j*56;box(c,xx,472,49,52,b==w.selected?0xfff6d16c:0xbb193c43);box(c,xx+9,480,31,27,COLORS[b]);center(c,""+b,xx+24.5f,517,10,b==w.selected?0xff193c43:Color.WHITE);}
            box(c,716,472,49,52,0xbb193c43);center(c,"…",740,504,25,Color.WHITE);
            center(c,World.NAMES[w.selected]+"  ·  "+(w.flying?"Fliegen":"Laufen"),486,458,14,Color.WHITE);
            if(statusUntil>System.currentTimeMillis()){box(c,270,85,420,35,0xcc153d42);center(c,status,480,108,14,Color.WHITE);}
            c.restore();
        }
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
                if(x>860&&y<78){clearInput();activity.menu();return true;}
                if(y>=472&&y<=530&&x>=260&&x<708){int b=(world.selected<=8?1:5)+(int)((x-260)/56);synchronized(world){world.selected=b;}return true;}
                if(x>=716&&x<=765&&y>=472){clearInput();activity.palette();return true;}
                if(at(x,y,790,250,33)){edit(false);return true;}if(at(x,y,899,250,33)){edit(true);return true;}
                if(at(x,y,737,355,34)||world.flying&&at(x,y,737,425,34)){verticalId=id;vertical=y<390?1:-1;return true;}
                if(x<240&&y>250&&moveId<0){moveId=id;updateMove(x,y);return true;}
                if(Math.hypot(x-lookJoyX,y-lookJoyY)<=85&&lookId<0){lookId=id;updateLook(x,y);return true;}
            }
            if(action==MotionEvent.ACTION_MOVE){for(int n=0;n<event.getPointerCount();n++){int pointer=event.getPointerId(n);float a=(event.getX(n)-ox)/s,b=(event.getY(n)-oy)/s;if(pointer==moveId)updateMove(a,b);if(pointer==lookId)updateLook(a,b);}}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){if(id==moveId){moveId=-1;forward=strafe=0;knobX=knobY=0;}if(id==lookId){lookId=-1;lookHorizontal=lookVertical=0;lookKnobX=lookKnobY=0;}if(id==verticalId){verticalId=-1;vertical=0;}}
            return true;
        }
        void updateLook(float x,float y){
            float dx=x-lookJoyX,dy=y-lookJoyY,n=(float)Math.hypot(dx,dy);
            if(n>52){dx*=52/n;dy*=52/n;}lookKnobX=dx;lookKnobY=dy;
            float radius=(float)Math.hypot(dx,dy)/52;
            float strength=Math.max(0,(radius-.12f)/.88f);
            lookHorizontal=radius==0?0:dx/(52*radius)*strength;
            lookVertical=radius==0?0:dy/(52*radius)*strength;
        }
        void updateMove(float x,float y){float dx=x-joyX,dy=y-joyY,n=(float)Math.sqrt(dx*dx+dy*dy);if(n>52){dx*=52/n;dy*=52/n;}knobX=dx;knobY=dy;strafe=dx/52;forward=-dy/52;}
    }
}
