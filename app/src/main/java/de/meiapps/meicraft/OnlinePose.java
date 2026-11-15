package de.meiapps.meicraft;

/** Render-thread pose history. Monotonic time, bounded interpolation, shortest yaw arc. */
final class OnlinePose {
    private long sample,previous;private float ax,ay,az,aa,bx,by,bz,ba;private boolean initialized;
    final float[] value=new float[4];
    void receive(long time,float x,float y,float z,float yaw){
        if(initialized&&time<=sample)return;
        boolean snap=!initialized||(x-bx)*(x-bx)+(y-by)*(y-by)+(z-bz)*(z-bz)>64;
        if(snap){ax=x;ay=y;az=z;aa=yaw;previous=time;}else{ax=bx;ay=by;az=bz;aa=ba;previous=Math.max(sample,time-100000000L);}
        bx=x;by=y;bz=z;ba=yaw;sample=time;initialized=true;
    }
    float[] at(long now){
        double t=sample==previous?1:Math.max(0,Math.min(1,(now-100000000L-previous)/(double)(sample-previous)));
        value[0]=(float)(ax+(bx-ax)*t);value[1]=(float)(ay+(by-ay)*t);value[2]=(float)(az+(bz-az)*t);value[3]=(float)(aa+Math.IEEEremainder(ba-aa,Math.PI*2)*t);return value;
    }
    static boolean send(long now,long last,float x,float y,float z,float yaw,float px,float py,float pz,float oldYaw){
        return last==0||now-last>=2000000000L||now-last>=100000000L&&((x-px)*(x-px)+(y-py)*(y-py)+(z-pz)*(z-pz)>=.0004f||Math.abs(Math.IEEEremainder(yaw-oldYaw,Math.PI*2))>=.025);
    }
}
