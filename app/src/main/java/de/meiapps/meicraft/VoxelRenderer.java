package de.meiapps.meicraft;

import android.opengl.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.nio.*;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

final class VoxelRenderer implements GLSurfaceView.Renderer {
    final GameView view;
    World world;
    int program,texture,pos,uv,light,mvp,camera,fog,ambient,lamps;
    final float[] lampData=new float[32];float lampTimer;
    final float[] projection=new float[16],look=new float[16],matrix=new float[16];
    Mesh[] meshes=new Mesh[0];int meshCols,meshRows;
    long last;
    int width,height;
    static final int[][] N={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final float[][] F={
        {1,0,0,1,1,0,1,1,1,1,0,1}, {0,0,1,0,1,1,0,1,0,0,0,0},
        {0,1,0,0,1,1,1,1,1,1,1,0}, {0,0,1,0,0,0,1,0,0,1,0,1},
        {1,0,1,1,1,1,0,1,1,0,0,1}, {0,0,0,0,1,0,1,1,0,1,0,0}
    };
    static final float[] SHADE={.82f,.66f,1,.49f,.88f,.73f};
    static final int[] ORDER={0,1,2,0,2,3};
    static final class Mesh{int buffer,count;boolean dirty=true;}
    VoxelRenderer(GameView view){this.view=view;}
    static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){
        String vs="uniform mat4 uM;uniform vec3 uCamera;attribute vec3 aPos;attribute vec2 aUv;attribute float aLight;varying vec2 vUv;varying float vLight;varying float vDistance;varying vec3 vWorld;void main(){vWorld=aPos;gl_Position=uM*vec4(aPos,1.0);vUv=aUv;vLight=aLight;vDistance=distance(aPos,uCamera);}";
        String fs="precision mediump float;uniform sampler2D uTex;uniform vec3 uFog;uniform float uAmbient;uniform vec4 uLamps[8];varying vec3 vWorld;varying vec2 vUv;varying float vLight;varying float vDistance;void main(){float brightness=uAmbient;for(int i=0;i<8;i++){brightness=max(brightness,uLamps[i].w*max(0.0,1.0-distance(vWorld,uLamps[i].xyz)/6.0));}vec3 c=texture2D(uTex,vUv).rgb*min(vLight,1.0)*(vLight>1.1?1.0:brightness);float f=smoothstep(27.0,55.0,vDistance);gl_FragColor=vec4(mix(c,uFog,f),1.0);}";
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(program);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        pos=GLES20.glGetAttribLocation(program,"aPos");uv=GLES20.glGetAttribLocation(program,"aUv");light=GLES20.glGetAttribLocation(program,"aLight");mvp=GLES20.glGetUniformLocation(program,"uM");camera=GLES20.glGetUniformLocation(program,"uCamera");fog=GLES20.glGetUniformLocation(program,"uFog");ambient=GLES20.glGetUniformLocation(program,"uAmbient");lamps=GLES20.glGetUniformLocation(program,"uLamps[0]");
        ensureMeshes(view.world,true);int[] ids=new int[1];
        GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        try(java.io.InputStream stream=view.getContext().getAssets().open("blocks.png")){Bitmap bitmap=BitmapFactory.decodeStream(stream);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();}catch(Exception e){throw new IllegalStateException(e);}
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);GLES20.glCullFace(GLES20.GL_BACK);GLES20.glFrontFace(GLES20.GL_CCW);
        last=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,72,(float)w/h,.08f,70);}
    void ensureMeshes(World target,boolean newContext){
        int cols=(target==null?World.W:target.width)/16,rows=(target==null?World.D:target.depth)/16;
        if(!newContext&&cols==meshCols&&rows==meshRows)return;
        if(!newContext&&meshes.length>0){int[] old=new int[meshes.length];for(int i=0;i<old.length;i++)old[i]=meshes[i].buffer;GLES20.glDeleteBuffers(old.length,old,0);}
        meshCols=cols;meshRows=rows;meshes=new Mesh[cols*rows];int[] ids=new int[meshes.length];GLES20.glGenBuffers(ids.length,ids,0);for(int i=0;i<ids.length;i++){meshes[i]=new Mesh();meshes[i].buffer=ids[i];}
    }
    void dirty(int x,int z){for(int zz=Math.max(0,(z-1)/16);zz<=Math.min(meshRows-1,(z+1)/16);zz++)for(int xx=Math.max(0,(x-1)/16);xx<=Math.min(meshCols-1,(x+1)/16);xx++)meshes[zz*meshCols+xx].dirty=true;}
    static final class Floats{float[] values=new float[8192];int size;void add(float a,float b,float c,float u,float v,float l){if(size+6>values.length)values=java.util.Arrays.copyOf(values,values.length*2);values[size++]=a;values[size++]=b;values[size++]=c;values[size++]=u;values[size++]=v;values[size++]=l;}}
    void mesh(int cx,int cz,Mesh mesh){Floats data=new Floats();
        for(int y=0;y<World.H;y++)for(int z=cz*16;z<cz*16+16;z++)for(int x=cx*16;x<cx*16+16;x++){
            int block=world.get(x,y,z);if(block==0)continue;float heat=world.forge.heat(x,y,z);
            for(int face=0;face<6;face++){
                int neighbor=world.get(x+N[face][0],y+N[face][1],z+N[face][2]);
                if(neighbor!=0 && !((neighbor==10||neighbor==15)&&block!=neighbor))continue;
                if(y==0&&face==3)continue;
                int tile=block==16?21:block==13?(heat>0?22:16):block==14?(heat>0?23:17):block==15?18:block;if(block==1)tile=face==2?1:face==3?2:13;if(block==5&&face>=2&&face<=3)tile=14;
                float u0=(tile*32+.5f)/1024f,u1=(tile*32+31.5f)/1024f;
                float shade=block==11||block==15||block==16||heat>0?1.2f:SHADE[face];
                for(int corner:ORDER){float[] f=F[face];float u=(corner==0||corner==1)?u0:u1;float v=(corner==0||corner==3)?31.5f/32:.5f/32;data.add(x+f[corner*3],y+f[corner*3+1],z+f[corner*3+2],u,v,shade);}
            }
        }
        FloatBuffer buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(data.values,0,data.size).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.buffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,data.size*4,buffer,GLES20.GL_STATIC_DRAW);mesh.count=data.size/6;mesh.dirty=false;
    }
    @Override public void onDrawFrame(GL10 gl){
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last)*1e-9f);last=now;
        world=view.world;if(world==null)return;
        ensureMeshes(world,false);
        if(world!=view.renderedWorld){for(Mesh m:meshes)m.dirty=true;view.renderedWorld=world;lampTimer=0;}
        synchronized(world){
            if(!view.activity.paused)view.tick(dt);
            long changed=world.online?0:world.nature.takeDirtyChunks();for(int i=0;i<meshes.length&&i<64;i++)if((changed&(1L<<i))!=0)meshes[i].dirty=true;
            float daylight=world.story&&!world.online?world.survival.daylight():1;float skyR=.025f+.505f*daylight*daylight,skyG=.035f+.725f*daylight*daylight,skyB=.09f+.77f*daylight*daylight;GLES20.glClearColor(skyR,skyG,skyB,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float dx=-(float)Math.sin(world.yaw)*(float)Math.cos(world.pitch),dy=(float)Math.sin(world.pitch),dz=-(float)Math.cos(world.yaw)*(float)Math.cos(world.pitch);
            Matrix.setLookAtM(look,0,world.x,world.y,world.z,world.x+dx,world.y+dy,world.z+dz,0,1,0);Matrix.multiplyMM(matrix,0,projection,0,look,0);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvp,1,false,matrix,0);GLES20.glUniform3f(camera,world.x,world.y,world.z);GLES20.glUniform3f(fog,skyR,skyG,skyB);GLES20.glUniform1f(ambient,daylight);lampTimer-=dt;if(lampTimer<=0){findLamps();lampTimer=.5f;}GLES20.glUniform4fv(lamps,8,lampData,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTex"),0);
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv);GLES20.glEnableVertexAttribArray(light);
            for(int i=0;i<meshes.length;i++){Mesh m=meshes[i];int cx=i%meshCols,cz=i/meshCols;float dxChunk=cx*16+8-world.x,dzChunk=cz*16+8-world.z;
                OnlineSession online=view.activity.online==null?null:view.activity.online.session;
                if(dxChunk*dxChunk+dzChunk*dzChunk>72*72||world.online&&(online==null||!online.canMove(cx*16+8,cz*16+8))){if(world.online&&m.count>0){GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,0,null,GLES20.GL_STATIC_DRAW);m.count=0;m.dirty=true;}continue;}
                if(m.dirty)mesh(cx,cz,m);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,0);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,12);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,20);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
            drawMobs();view.target=world.ray(6);if(world.survival.aimed(world)==null)outline(view.target);view.hud.postInvalidate();
        }
    }
    void findLamps(){
        java.util.Arrays.fill(lampData,0);float[] distances=new float[8];java.util.Arrays.fill(distances,Float.MAX_VALUE);
        int cx=(int)world.x,cy=(int)world.y,cz=(int)world.z;
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++)for(int y=cy-6;y<=cy+5;y++){
            int b=world.get(x,y,z);float heat=world.forge.heat(x,y,z);if(b!=11&&b!=15&&b!=16&&heat<=0)continue;float d=(x+.5f-world.x)*(x+.5f-world.x)+(y+.5f-world.y)*(y+.5f-world.y)+(z+.5f-world.z)*(z+.5f-world.z);
            for(int i=0;i<8;i++)if(d<distances[i]){for(int j=7;j>i;j--){distances[j]=distances[j-1];System.arraycopy(lampData,(j-1)*4,lampData,j*4,4);}distances[i]=d;lampData[i*4]=x+.5f;lampData[i*4+1]=y+.5f;lampData[i*4+2]=z+.5f;lampData[i*4+3]=b==16?.4f:heat>0?.25f+.7f*heat:1;break;}
        }
    }
    final Floats creatures=new Floats();
    final FloatBuffer creatureBuffer=ByteBuffer.allocateDirect(100000*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    void cube(float x,float y,float z,float sx,float sy,float sz,int tile){
        for(int face=0;face<6;face++)for(int corner:ORDER){float[] f=F[face];float u=(tile*32+((corner==0||corner==1)?.5f:31.5f))/1024f,v=(corner==0||corner==3)?31.5f/32:.5f/32;
            creatures.add(x+f[corner*3]*sx,y+f[corner*3+1]*sy,z+f[corner*3+2]*sz,u,v,tile==24?1.2f:SHADE[face]);}
    }
    static final class PlayerLabel {final String name;final float x,y;PlayerLabel(String name,float x,float y){this.name=name;this.x=x;this.y=y;}}
    volatile java.util.List<PlayerLabel> playerLabels=java.util.Collections.emptyList();
    final java.util.Map<String,float[]> avatarPositions=new java.util.HashMap<>();
    void actorPart(float cx,float feet,float cz,float yaw,float ox,float oy,float oz,float sx,float sy,float sz,int tile){
        float sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        for(int face=0;face<6;face++)for(int corner:ORDER){float[] f=F[face];float xx=ox+f[corner*3]*sx,zz=oz+f[corner*3+2]*sz;boolean plainSkin=Math.abs(tile)==25&&(tile<0||face!=5);int atlas=Math.abs(tile);
            float u=(atlas*32+(plainSkin?2.5f:(corner==0||corner==1)?.5f:31.5f))/1024f,v=plainSkin?2.5f/32:(corner==0||corner==3)?31.5f/32:.5f/32;
            creatures.add(cx+xx*cos+zz*sin,feet+oy+f[corner*3+1]*sy,cz-xx*sin+zz*cos,u,v,SHADE[face]);
        }
    }
    void drawPlayers(){
        OnlineSession online=view.activity.online==null?null:view.activity.online.session;
        if(!world.online||online==null){playerLabels=java.util.Collections.emptyList();avatarPositions.clear();return;}
        java.util.List<PlayerLabel> labels=new java.util.ArrayList<>();java.util.Set<String> seen=new java.util.HashSet<>();
        for(OnlineSession.Player player:online.players){
            if(player.uid.equals(online.firebase.uid)||!online.readyRegions.contains(player.sector))continue;
            float targetX=player.sector%online.side*World.W+player.x,targetZ=player.sector/online.side*World.D+player.z;
            if((targetX-world.x)*(targetX-world.x)+(targetZ-world.z)*(targetZ-world.z)>65*65)continue;seen.add(player.uid);
            float[] p=avatarPositions.get(player.uid);if(p==null){p=new float[]{targetX,player.y,targetZ,player.yaw};avatarPositions.put(player.uid,p);}
            float remaining=(targetX-p[0])*(targetX-p[0])+(targetZ-p[2])*(targetZ-p[2]);
            p[0]+=(targetX-p[0])*.09f;p[1]+=(player.y-p[1])*.09f;p[2]+=(targetZ-p[2])*.09f;p[3]+=(float)Math.IEEEremainder(player.yaw-p[3],Math.PI*2)*.09f;
            float x=p[0],feet=p[1]-1.6f,z=p[2],yaw=p[3],swing=remaining>.02f?(float)Math.sin(System.nanoTime()*1e-9*7)*.09f:0;int shirt=27+OnlineWorld.colorIndex(player.uid);
            actorPart(x,feet,z,yaw,-.25f,.65f,-.15f,.5f,.55f,.3f,shirt);actorPart(x,feet,z,yaw,-.22f,1.2f,-.22f,.44f,.44f,.44f,25);
            actorPart(x,feet,z,yaw,-.24f,0,-.14f+swing,.19f,.65f,.28f,26);actorPart(x,feet,z,yaw,.05f,0,-.14f-swing,.19f,.65f,.28f,26);
            actorPart(x,feet,z,yaw,-.4f,.6f,-.13f-swing,.15f,.6f,.26f,-25);actorPart(x,feet,z,yaw,.25f,.6f,-.13f+swing,.15f,.6f,.26f,-25);
            float[] screen=new float[4];if(GLU.gluProject(x,feet+1.85f,z,look,0,projection,0,new int[]{0,0,width,height},0,screen,0)==1&&screen[2]>0&&screen[2]<1&&world.clearLine(world.x,world.y,world.z,x,feet+1.6f,z))labels.add(new PlayerLabel(player.name,screen[0],height-screen[1]));
        }
        avatarPositions.keySet().retainAll(seen);playerLabels=java.util.Collections.unmodifiableList(labels);
    }
    void drawMobs(){
        creatures.size=0;
        if(world.story)for(Survival.Mob m:world.survival.mobs){float feet=m.y-1.6f,swing=(float)Math.sin(world.survival.time*5+m.x)*.1f;
            cube(m.x-.3f,feet+.5f,m.z-.22f,.6f,.65f,.44f,19);
            cube(m.x-.34f,feet+1.15f,m.z-.34f,.68f,.55f,.68f,20);
            cube(m.x-.25f,feet,m.z-.18f+swing,.2f,.5f,.36f,19);cube(m.x+.05f,feet,m.z-.18f-swing,.2f,.5f,.36f,19);
            cube(m.x-.48f,feet+.52f,m.z-.15f,.18f,.58f,.3f,19);cube(m.x+.3f,feet+.52f,m.z-.15f,.18f,.58f,.3f,19);
        }
        int sparks=0;
        for(java.util.Map.Entry<Integer,Float> hot:world.forge.glow.entrySet()){
            int i=hot.getKey(),block=world.blocks[i]&255;if(block!=13&&block!=14)continue;
            float x=i%World.W+.5f,y=i/(World.W*World.D)+1,z=i/World.W%World.D+.5f;
            if(Math.abs(x-world.x)>12||Math.abs(z-world.z)>12||Math.abs(y-world.y)>8||sparks++>=48)continue;
            float phase=(world.forge.animation*.8f+(i%17)/17f)%1f;
            cube(x+(float)Math.sin(i+phase*5)*.24f,y+phase*.7f,z+(float)Math.cos(i+phase*4)*.24f,.055f,.055f,.055f,24);
        }
        drawPlayers();
        if(creatures.size==0)return;
        creatureBuffer.clear();creatureBuffer.put(creatures.values,0,creatures.size);creatureBuffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,creatureBuffer);
        creatureBuffer.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,creatureBuffer);creatureBuffer.position(5);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,creatureBuffer);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,creatures.size/6);
    }
    final FloatBuffer outlineBuffer=ByteBuffer.allocateDirect(24*6*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    void outline(int[] hit){
        if(hit==null)return;
        outlineBuffer.clear();
        int[][] edges={{0,1},{1,3},{3,2},{2,0},{4,5},{5,7},{7,6},{6,4},{0,4},{1,5},{2,6},{3,7}};
        for(int[] edge:edges)for(int vertex:edge){
            outlineBuffer.put(hit[0]+((vertex&1)==0?-.004f:1.004f));
            outlineBuffer.put(hit[1]+((vertex&2)==0?-.004f:1.004f));
            outlineBuffer.put(hit[2]+((vertex&4)==0?-.004f:1.004f));
            outlineBuffer.put((11*32+16f)/1024);outlineBuffer.put(.5f);outlineBuffer.put(1f);
        }
        outlineBuffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,outlineBuffer);
        outlineBuffer.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,outlineBuffer);
        outlineBuffer.position(5);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,outlineBuffer);
        GLES20.glLineWidth(2);GLES20.glDrawArrays(GLES20.GL_LINES,0,24);
    }

}

