package de.meiapps.meicraft;

import java.io.*;
import java.util.*;

/** Bounded, offline survival simulation. Time advances only while the world is playing. */
public final class Survival {
    public static final float CYCLE=720, DUSK=450, NIGHT=480, DAWN=690;
    public static final int MAX_MOBS=6, MAX_HEALTH=10;
    public float time,spawnX=48.5f,spawnZ=48.5f;
    public int health=MAX_HEALTH;
    public boolean enemiesEnabled=true;
    public final List<Mob> mobs=new ArrayList<>();
    public String notice="";
    private float spawnTimer,damageTimer=3,attackTimer,regenTimer;
    private long attempts;
    public static final class Mob {
        public float x,y,z,health=6;
        float velocity;
        public Mob(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }
    public boolean night(){return time>=NIGHT&&time<DAWN;}
    public float daylight(){
        if(time<DUSK)return 1;
        if(time<NIGHT)return 1-(time-DUSK)/(NIGHT-DUSK)*.76f;
        if(time<DAWN)return .24f;
        return .24f+(time-DAWN)/(CYCLE-DAWN)*.76f;
    }
    public String phase(){return time<DUSK?"Tag":time<NIGHT?"Abend":time<DAWN?"Nacht":"Morgen";}
    public int secondsToChange(){return (int)Math.ceil((time<DUSK?DUSK:time<NIGHT?NIGHT:time<DAWN?DAWN:CYCLE)-time);}
    public void toggleEnemies(){enemiesEnabled=!enemiesEnabled;if(!enemiesEnabled)mobs.clear();}
    public static boolean lit(World w,float x,float y,float z){
        int cx=(int)Math.floor(x),cy=(int)Math.floor(y-1),cz=(int)Math.floor(z);
        for(int a=cx-4;a<=cx+4;a++)for(int b=Math.max(1,cy-3);b<=Math.min(World.H-1,cy+3);b++)for(int c=cz-4;c<=cz+4;c++)
            if((w.get(a,b,c)==11||w.get(a,b,c)==15)&&w.clearLine(x,y,z,a+.5f,b+1.1f,c+.5f))return true;
        return false;
    }
    /** Returns true if the player's position was reset. */
    public boolean tick(World w,float dt){
        if(!w.story||!Float.isFinite(dt)||dt<=0)return false;
        dt=Math.min(dt,.1f);boolean wasNight=night();time=(time+dt)%CYCLE;
        if(!wasNight&&night())notice="Es wird Nacht · Suche Schutz oder verteidige deine Burg!";
        if(wasNight&&!night())notice="Der Morgen kommt · Nachtschatten verschwinden im Licht.";
        damageTimer=Math.max(0,damageTimer-dt);attackTimer=Math.max(0,attackTimer-dt);
        spawnTimer+=dt;
        if(enemiesEnabled&&night()&&spawnTimer>=8){spawnTimer=0;spawn(w);}
        if(!enemiesEnabled)mobs.clear();
        for(int i=mobs.size()-1;i>=0;i--){
            Mob m=mobs.get(i);float dx=w.x-m.x,dz=w.z-m.z,distance=(float)Math.hypot(dx,dz);
            if(!night())m.health-=dt*3;
            if(w.touchesLava(m.x,m.y,m.z))m.health-=dt*12;
            if(m.health<=0||distance>48){mobs.remove(i);continue;}
            // No jumping: solid walls block them, ditches and lava really work.
            if(distance>1&&night()){
                float mx=dx/Math.max(distance,.01f)*1.35f*dt,mz=dz/Math.max(distance,.01f)*1.35f*dt;
                if(!w.collides(m.x+mx,m.y,m.z))m.x+=mx;
                if(!w.collides(m.x,m.y,m.z+mz))m.z+=mz;
            }
            m.velocity=Math.max(-12,m.velocity-20*dt);
            float yy=m.y+m.velocity*dt;
            if(!w.collides(m.x,yy,m.z))m.y=yy;else m.velocity=0;
            if(distance<1.35f&&Math.abs(w.y-m.y)<1.4f&&w.clearLine(w.x,w.y-.5f,w.z,m.x,m.y-.5f,m.z))hurt(2,"Ein Nachtschatten hat dich getroffen.");
        }
        if(w.touchesLava(w.x,w.y,w.z))hurt(4,"Vorsicht, Lava! Raus aus dem Graben!");
        if(health<=0){respawn(w);return true;}
        if(damageTimer==0&&!w.touchesLava(w.x,w.y,w.z)){regenTimer+=dt;if(regenTimer>=4){health=Math.min(MAX_HEALTH,health+1);regenTimer=0;}}
        else regenTimer=0;
        return false;
    }
    private void hurt(int amount,String text){if(damageTimer>0)return;health=Math.max(0,health-amount);damageTimer=1;regenTimer=0;notice=text;}
    private void spawn(World w){
        if(mobs.size()>=MAX_MOBS)return;
        Random random=new Random(w.seed^(++attempts*91873));
        for(int tries=0;tries<24;tries++){
            double angle=random.nextDouble()*Math.PI*2;float distance=14+random.nextFloat()*10;
            int x=(int)Math.floor(w.x+Math.cos(angle)*distance),z=(int)Math.floor(w.z+Math.sin(angle)*distance);
            float y=surface(w,x,z);
            if(y<0||lit(w,x+.5f,y,z+.5f))continue;
            mobs.add(new Mob(x+.5f,y,z+.5f));return;
        }
    }
    private static float surface(World w,int x,int z){
        if(x<1||x>=World.W-1||z<1||z>=World.D-1)return -1;
        for(int y=World.H-3;y>=1;y--){
            int b=w.get(x,y,z);if(b==0)continue;
            // First surface only: never spawn in underground rooms or on water/lava/trees.
            if(b==10||b==15||b==6||b==5||b==8||b==9||b==7||b==14)return -1;
            float eye=y+2.62f;
            return !w.collides(x+.5f,eye,z+.5f)?eye:-1;
        }
        return -1;
    }
    public void respawn(World w){
        float best=Float.MAX_VALUE,bx=w.x,by=w.y,bz=w.z;
        for(int x=1;x<World.W-1;x++)for(int z=1;z<World.D-1;z++){
            float distance=(x+.5f-spawnX)*(x+.5f-spawnX)+(z+.5f-spawnZ)*(z+.5f-spawnZ);
            if(distance>=best)continue;float y=surface(w,x,z);if(y<0)continue;
            best=distance;bx=x+.5f;by=y;bz=z+.5f;
        }
        w.x=bx;w.y=by;w.z=bz;health=MAX_HEALTH;mobs.clear();damageTimer=5;regenTimer=0;
        notice="Zurück am Start · Bauwerke und Vorräte bleiben erhalten.";
    }
    /** Aiming, reach and occlusion are checked before a melee hit. */
    public Mob aimed(World w){
        Mob best=null;float nearest=3.4f;
        float dx=-(float)(Math.sin(w.yaw)*Math.cos(w.pitch)),dy=(float)Math.sin(w.pitch),dz=-(float)(Math.cos(w.yaw)*Math.cos(w.pitch));
        for(Mob m:mobs){float rx=m.x-w.x,ry=m.y-.65f-w.y,rz=m.z-w.z,along=rx*dx+ry*dy+rz*dz;
            if(along<=0||along>nearest||rx*rx+ry*ry+rz*rz-along*along>.85f*.85f)continue;
            if(w.clearLine(w.x,w.y,w.z,m.x,m.y-.65f,m.z)){best=m;nearest=along;}
        }
        return best;
    }
    public boolean attack(World w){
        if(!w.story)return false;Mob m=aimed(w);if(m==null)return false;
        if(attackTimer>0)return true;
        attackTimer=.5f;m.health-=w.progress.sword?4:2;
        if(m.health<=0){mobs.remove(m);notice="Nachtschatten besiegt";}else notice=w.progress.sword?"Schwerttreffer":"Treffer · Ein Eisenschwert wirkt stärker";
        return true;
    }
    public void write(DataOutputStream out)throws IOException{
        out.writeFloat(time);out.writeFloat(spawnX);out.writeFloat(spawnZ);out.writeInt(health);out.writeBoolean(enemiesEnabled);out.writeLong(attempts);
        out.writeInt(mobs.size());for(Mob m:mobs){out.writeFloat(m.x);out.writeFloat(m.y);out.writeFloat(m.z);out.writeFloat(m.health);}
    }
    public void read(DataInputStream in)throws IOException{
        time=in.readFloat();spawnX=in.readFloat();spawnZ=in.readFloat();health=in.readInt();enemiesEnabled=in.readBoolean();attempts=in.readLong();
        if(!Float.isFinite(time)||time<0||time>=CYCLE||!Float.isFinite(spawnX)||spawnX<.3f||spawnX>World.W-.3f||!Float.isFinite(spawnZ)||spawnZ<.3f||spawnZ>World.D-.3f||health<1||health>MAX_HEALTH||attempts<0)throw new IOException("Ungültiger Überlebens-Spielstand");
        int count=in.readInt();if(count<0||count>MAX_MOBS||!enemiesEnabled&&count>0)throw new IOException("Ungültige Gegnerzahl");
        for(int i=0;i<count;i++){Mob m=new Mob(in.readFloat(),in.readFloat(),in.readFloat());m.health=in.readFloat();
            if(!Float.isFinite(m.x)||!Float.isFinite(m.y)||!Float.isFinite(m.z)||!Float.isFinite(m.health)||m.x<.3f||m.x>World.W-.3f||m.z<.3f||m.z>World.D-.3f||m.y<1.62f||m.y>World.H+16||m.health<=0||m.health>6)throw new IOException("Ungültiger Gegner");mobs.add(m);}
    }
}
