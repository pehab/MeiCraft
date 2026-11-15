package de.meiapps.meicraft;

import android.opengl.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.nio.*;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

final class VoxelRenderer implements GLSurfaceView.Renderer {
    // Glass uses view-dependent sky/ground color. Constant face shades identify the normal axis
    // without derivatives, extra vertex attributes or imprecise world-coordinate fractions.
    static final String FRAGMENT_SHADER="precision mediump float;uniform sampler2D uTex;uniform vec3 uFog;uniform float uAmbient;uniform float uGlassPass;uniform vec3 uGlassEye;uniform float uLiquidTime;uniform vec4 uLamps[8];varying vec3 vWorld;varying vec2 vUv;varying float vLight;varying float vDistance;void main(){float brightness=uAmbient;if(uAmbient<1.0){for(int i=0;i<8;i++){brightness=max(brightness,uLamps[i].w*max(0.0,1.0-distance(vWorld,uLamps[i].xyz)/6.0));}}vec3 c=texture2D(uTex,vUv).rgb*min(vLight,1.0)*(vLight>1.1?1.0:brightness);float tile=floor(vUv.x*64.0);float liquid=step(9.5,tile)*step(tile,10.5)+step(17.5,tile)*step(tile,18.5);float shimmer=sin(vWorld.x*2.3+vWorld.z*1.7+uLiquidTime*2.1)*sin(vWorld.y*2.7-uLiquidTime*1.4);c*=1.0+liquid*step(0.001,uLiquidTime)*0.075*shimmer;float fogStart=27.0;float fogEnd=55.0;float haze=step(0.001,uLiquidTime)*(0.75+0.25*sin(uLiquidTime*0.11+vWorld.x*0.035+vWorld.z*0.045));float f=smoothstep(fogStart,fogEnd,vDistance);f=clamp(f+haze*0.045*smoothstep(18.0,48.0,vDistance),0.0,1.0);float alpha=1.0;if(uGlassPass>0.5){float ax=min(abs(vLight-0.82),abs(vLight-0.66));float ay=min(abs(vLight-1.0),abs(vLight-0.49));float az=min(abs(vLight-0.88),abs(vLight-0.73));vec3 n=vec3(0.0,0.0,1.0);if(ax<ay&&ax<az)n=vec3(1.0,0.0,0.0);else if(ay<az)n=vec3(0.0,1.0,0.0);vec3 eye=uGlassEye-vWorld;vec3 viewDir=eye/max(length(eye),0.001);float rim=1.0-clamp(abs(dot(n,viewDir)),0.0,1.0);rim=rim*rim*rim;vec3 reflected=reflect(-viewDir,n);vec3 environment=mix(vec3(0.20,0.25,0.24),uFog*1.1+vec3(0.05),smoothstep(-0.4,0.6,reflected.y))*brightness;float shine=pow(max(dot(reflect(-normalize(vec3(-0.4,0.85,0.25)),n),viewDir),0.0),24.0);c=mix(c,environment,0.10+rim*0.30)+vec3(shine*0.12*brightness);alpha=0.34+rim*0.20;}gl_FragColor=vec4(mix(c,uFog,f),alpha);}";
    final GameView view;
    World world;
    int program,texture,pos,uv,light,mvp,camera,fog,ambient,glassPass,glassEye,liquidTime,lamps,sampler,terrainElements;
    final float[] lampData=new float[32],lampDistances=new float[8];float lampTimer;
    final float[] projection=new float[16],look=new float[16],matrix=new float[16];
    Mesh[] meshes=new Mesh[0];int meshCols,meshRows;
    long last;boolean measuring;
    final RenderMetrics metrics=new RenderMetrics();
    int width,height;
    final ViewFrustum frustum=new ViewFrustum();
    final int[] viewport=new int[4];
    final float[] playerScreen=new float[4];
    static final int[][] EDGES={{0,1},{1,3},{3,2},{2,0},{4,5},{5,7},{7,6},{6,4},{0,4},{1,5},{2,6},{3,7}};
    static final int[][] N={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final float[][] F={
        {1,0,0,1,1,0,1,1,1,1,0,1}, {0,0,1,0,1,1,0,1,0,0,0,0},
        {0,1,0,0,1,1,1,1,1,1,1,0}, {0,0,1,0,0,0,1,0,0,1,0,1},
        {1,0,1,1,1,1,0,1,1,0,0,1}, {0,0,0,0,1,0,1,1,0,1,0,0}
    };
    static final float[] SHADE={.82f,.66f,1,.49f,.88f,.73f};
    static final int[] ORDER={0,1,2,0,2,3};
    static final class Mesh{int buffer,count,glassBuffer,glassCount;boolean dirty=true;}
    VoxelRenderer(GameView view){this.view=view;}
    static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){
        String vs="uniform mat4 uM;uniform vec3 uCamera;attribute vec3 aPos;attribute vec2 aUv;attribute float aLight;varying vec2 vUv;varying float vLight;varying float vDistance;varying vec3 vWorld;void main(){vWorld=aPos;gl_Position=uM*vec4(aPos,1.0);vUv=aUv;vLight=aLight;vDistance=distance(aPos,uCamera);}";
        String fs=FRAGMENT_SHADER;
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(program);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        pos=GLES20.glGetAttribLocation(program,"aPos");uv=GLES20.glGetAttribLocation(program,"aUv");light=GLES20.glGetAttribLocation(program,"aLight");mvp=GLES20.glGetUniformLocation(program,"uM");camera=GLES20.glGetUniformLocation(program,"uCamera");fog=GLES20.glGetUniformLocation(program,"uFog");ambient=GLES20.glGetUniformLocation(program,"uAmbient");glassPass=GLES20.glGetUniformLocation(program,"uGlassPass");glassEye=GLES20.glGetUniformLocation(program,"uGlassEye");liquidTime=GLES20.glGetUniformLocation(program,"uLiquidTime");lamps=GLES20.glGetUniformLocation(program,"uLamps[0]");sampler=GLES20.glGetUniformLocation(program,"uTex");
        ensureMeshes(view.world,true);int[] ids=new int[1];
        GLES20.glGenBuffers(1,ids,0);terrainElements=ids[0];
        short[] indices=TerrainIndices.pattern();ShortBuffer elements=ByteBuffer.allocateDirect(indices.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();elements.put(indices).flip();
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,terrainElements);GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,indices.length*2,elements,GLES20.GL_STATIC_DRAW);GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
        GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        try(java.io.InputStream stream=view.getContext().getAssets().open("blocks.png")){Bitmap bitmap=BitmapFactory.decodeStream(stream);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();}catch(Exception e){throw new IllegalStateException(e);}
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);GLES20.glCullFace(GLES20.GL_BACK);GLES20.glFrontFace(GLES20.GL_CCW);
        last=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;viewport[2]=w;viewport[3]=h;GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,72,(float)w/h,.08f,70);}
    void ensureMeshes(World target,boolean newContext){
        int cols=(target==null?World.W:target.width)/16,rows=(target==null?World.D:target.depth)/16;
        if(!newContext&&cols==meshCols&&rows==meshRows)return;
        if(!newContext&&meshes.length>0){int[] old=new int[meshes.length*2];for(int i=0;i<meshes.length;i++){old[i*2]=meshes[i].buffer;old[i*2+1]=meshes[i].glassBuffer;}GLES20.glDeleteBuffers(old.length,old,0);}
        meshCols=cols;meshRows=rows;meshes=new Mesh[cols*rows];int[] ids=new int[meshes.length*2];GLES20.glGenBuffers(ids.length,ids,0);for(int i=0;i<meshes.length;i++){meshes[i]=new Mesh();meshes[i].buffer=ids[i*2];meshes[i].glassBuffer=ids[i*2+1];}
    }
    void dirty(int x,int z){for(int zz=Math.max(0,(z-1)/16);zz<=Math.min(meshRows-1,(z+1)/16);zz++)for(int xx=Math.max(0,(x-1)/16);xx<=Math.min(meshCols-1,(x+1)/16);xx++)meshes[zz*meshCols+xx].dirty=true;}
    static final class Floats{float[] values=new float[8192];int size;void add(float a,float b,float c,float u,float v,float l){if(size+6>values.length)values=java.util.Arrays.copyOf(values,values.length*2);values[size++]=a;values[size++]=b;values[size++]=c;values[size++]=u;values[size++]=v;values[size++]=l;}}
    final VoxelMesher terrainMesher=new VoxelMesher();
    FloatBuffer terrainBuffer=ByteBuffer.allocateDirect(8192*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    void mesh(int cx,int cz,Mesh mesh){
        long started=System.nanoTime();
        terrainMesher.build(world,cx,cz,false);
        if(terrainBuffer.capacity()<terrainMesher.size)terrainBuffer=ByteBuffer.allocateDirect(terrainMesher.values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        terrainBuffer.clear();terrainBuffer.put(terrainMesher.values,0,terrainMesher.size).flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.buffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,terrainMesher.size*4,terrainBuffer,GLES20.GL_STATIC_DRAW);mesh.count=terrainMesher.size/6;
        terrainMesher.build(world,cx,cz,true);
        if(terrainBuffer.capacity()<terrainMesher.size)terrainBuffer=ByteBuffer.allocateDirect(terrainMesher.values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        terrainBuffer.clear();terrainBuffer.put(terrainMesher.values,0,terrainMesher.size).flip();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.glassBuffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,terrainMesher.size*4,terrainBuffer,GLES20.GL_STATIC_DRAW);mesh.glassCount=terrainMesher.size/6;mesh.dirty=false;
        metrics.mesh(System.nanoTime()-started);
    }
    @Override public void onDrawFrame(GL10 gl){
        long now=System.nanoTime(),interval=now-last;float dt=Math.min(.05f,interval*1e-9f);last=now;
        boolean sample=measuring&&!view.activity.paused;measuring=!view.activity.paused;
        world=view.world;if(world==null)return;
        ensureMeshes(world,false);
        if(world!=view.renderedWorld){for(Mesh m:meshes)m.dirty=true;view.renderedWorld=world;lampTimer=0;}
        synchronized(world){
            if(!view.activity.paused)view.tick(dt);
            long changed=world.online?0:world.nature.takeDirtyChunks();for(int i=0;i<meshes.length&&i<64;i++)if((changed&(1L<<i))!=0)meshes[i].dirty=true;
            float daylight=world.story&&!world.online?world.survival.daylight():1;float skyR=.025f+.505f*daylight*daylight,skyG=.035f+.725f*daylight*daylight,skyB=.09f+.77f*daylight*daylight;GLES20.glClearColor(skyR,skyG,skyB,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float dx=-(float)Math.sin(world.yaw)*(float)Math.cos(world.pitch),dy=(float)Math.sin(world.pitch),dz=-(float)Math.cos(world.yaw)*(float)Math.cos(world.pitch);
            Matrix.setLookAtM(look,0,world.x,world.y,world.z,world.x+dx,world.y+dy,world.z+dz,0,1,0);Matrix.multiplyMM(matrix,0,projection,0,look,0);frustum.update(matrix);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvp,1,false,matrix,0);GLES20.glUniform3f(camera,world.x,world.y,world.z);GLES20.glUniform3f(glassEye,world.x,world.y,world.z);GLES20.glUniform3f(fog,skyR,skyG,skyB);GLES20.glUniform1f(ambient,daylight);GLES20.glUniform1f(glassPass,0f);GLES20.glUniform1f(liquidTime,(now*1e-9f)%10000f);lampTimer-=dt;if(lampTimer<=0){findLamps();lampTimer=.5f;}GLES20.glUniform4fv(lamps,8,lampData,0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(sampler,0);
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv);GLES20.glEnableVertexAttribArray(light);
            int drawn=0,culled=0;
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,terrainElements);
            for(int i=0;i<meshes.length;i++){Mesh m=meshes[i];int cx=i%meshCols,cz=i/meshCols;float dxChunk=cx*16+8-world.x,dzChunk=cz*16+8-world.z;
                OnlineSession online=view.activity.online==null?null:view.activity.online.session;
                if(dxChunk*dxChunk+dzChunk*dzChunk>72*72||world.online&&(online==null||!online.canMove(cx*16+8,cz*16+8))){if(world.online&&m.count>0){GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,0,null,GLES20.GL_STATIC_DRAW);m.count=0;m.glassCount=0;m.dirty=true;}continue;}
                if(m.dirty)mesh(cx,cz,m);
                // Retain the existing prewarming order, including off-camera
                // meshes, so turning never creates a new rebuild backlog.
                if(!frustum.intersects(cx*16,0,cz*16,cx*16+16,World.H,cz*16+16)){culled++;continue;}
                drawn++;
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);
                // Ordinary chunks use one draw. Even a fully alternating liquid
                // volume remains valid with bounded 16-bit batches on ES 2/3.
                for(int first=0;first<m.count;first+=TerrainIndices.MAX_VERTICES){
                    int count=Math.min(TerrainIndices.MAX_VERTICES,m.count-first),offset=first*24;
                    GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,offset);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,offset+12);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,offset+20);GLES20.glDrawElements(GLES20.GL_TRIANGLES,count/4*6,GLES20.GL_UNSIGNED_SHORT,0);
                }
            }
            // Transparent glass is drawn after opaque geometry, without writing depth.
            GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);GLES20.glDepthMask(false);GLES20.glUniform1f(glassPass,1f);
            for(int i=0;i<meshes.length;i++){Mesh m=meshes[i];if(m.glassCount==0)continue;int cx=i%meshCols,cz=i/meshCols;
                float ddx=cx*16+8-world.x,ddz=cz*16+8-world.z;
                if(ddx*ddx+ddz*ddz>72*72||!frustum.intersects(cx*16,0,cz*16+16-16,cx*16+16,World.H,cz*16+16))continue;
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.glassBuffer);
                for(int first=0;first<m.glassCount;first+=TerrainIndices.MAX_VERTICES){int count=Math.min(TerrainIndices.MAX_VERTICES,m.glassCount-first),offset=first*24;
                    GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,offset);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,offset+12);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,offset+20);GLES20.glDrawElements(GLES20.GL_TRIANGLES,count/4*6,GLES20.GL_UNSIGNED_SHORT,0);
                }
            }
            GLES20.glUniform1f(glassPass,0f);GLES20.glDepthMask(true);GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER,0);
            drawMobs();view.target=world.ray(6);if(world.survival.aimed(world)==null)outline(view.target);view.hud.postInvalidate();
            if(sample)metrics.frame(System.nanoTime()-now,interval,drawn,culled);
        }
    }
    void findLamps(){
        java.util.Arrays.fill(lampData,0);float[] distances=lampDistances;java.util.Arrays.fill(distances,Float.MAX_VALUE);
        int cx=(int)world.x,cy=(int)world.y,cz=(int)world.z;
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++)for(int y=cy-6;y<=cy+5;y++){
            int b=world.get(x,y,z);float heat=world.forge.heat(x,y,z);if(!Materials.luminous(b)&&!Materials.burning(world,x,y,z)&&heat<=0)continue;float d=(x+.5f-world.x)*(x+.5f-world.x)+(y+.5f-world.y)*(y+.5f-world.y)+(z+.5f-world.z)*(z+.5f-world.z);
            for(int i=0;i<8;i++)if(d<distances[i]){for(int j=7;j>i;j--){distances[j]=distances[j-1];System.arraycopy(lampData,(j-1)*4,lampData,j*4,4);}distances[i]=d;lampData[i*4]=x+.5f;lampData[i*4+1]=y+.5f;lampData[i*4+2]=z+.5f;lampData[i*4+3]=b==16?.4f:heat>0?.25f+.7f*heat:1;break;}
        }
    }
    final Floats creatures=new Floats();
    FloatBuffer creatureBuffer=ByteBuffer.allocateDirect(100000*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    void cube(float x,float y,float z,float sx,float sy,float sz,int tile){
        for(int face=0;face<6;face++)for(int corner:ORDER){float[] f=F[face];float u=(tile*32+((corner==0||corner==1)?.5f:31.5f))/2048f,v=(corner==0||corner==3)?31.5f/32:.5f/32;
            creatures.add(x+f[corner*3]*sx,y+f[corner*3+1]*sy,z+f[corner*3+2]*sz,u,v,tile==24||tile==35||tile==36?1.2f:SHADE[face]);}
    }
    static final class PlayerLabel {final String name;final float x,y;PlayerLabel(String name,float x,float y){this.name=name;this.x=x;this.y=y;}}
    volatile java.util.List<PlayerLabel> playerLabels=java.util.Collections.emptyList();
    final java.util.Map<String,OnlinePose> avatarPositions=new java.util.HashMap<>();
    void actorPart(float cx,float feet,float cz,float yaw,float ox,float oy,float oz,float sx,float sy,float sz,int tile){
        float sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        for(int face=0;face<6;face++)for(int corner:ORDER){float[] f=F[face];float xx=ox+f[corner*3]*sx,zz=oz+f[corner*3+2]*sz;boolean plainSkin=Math.abs(tile)==25&&(tile<0||face!=5);int atlas=Math.abs(tile);
            float u=(atlas*32+(plainSkin?2.5f:(corner==0||corner==1)?.5f:31.5f))/2048f,v=plainSkin?2.5f/32:(corner==0||corner==3)?31.5f/32:.5f/32;
            creatures.add(cx+xx*cos+zz*sin,feet+oy+f[corner*3+1]*sy,cz-xx*sin+zz*cos,u,v,SHADE[face]);
        }
    }
    final java.util.Set<String> seenAvatars=new java.util.HashSet<>();
    void drawPlayers(){
        OnlineSession online=view.activity.online==null?null:view.activity.online.session;
        if(!world.online||online==null){playerLabels=java.util.Collections.emptyList();avatarPositions.clear();return;}
        java.util.List<OnlineSession.Player> players=online.players;
        if(players.isEmpty()||players.size()==1&&players.get(0).uid.equals(online.firebase.uid)){playerLabels=java.util.Collections.emptyList();avatarPositions.clear();return;}
        java.util.List<PlayerLabel> labels=new java.util.ArrayList<>();java.util.Set<String> seen=seenAvatars;seen.clear();
        for(OnlineSession.Player player:players){
            if(player.uid.equals(online.firebase.uid)||!online.readyRegions.contains(player.sector))continue;
            float targetX=player.sector%online.side*World.W+player.x,targetZ=player.sector/online.side*World.D+player.z;
            if((targetX-world.x)*(targetX-world.x)+(targetZ-world.z)*(targetZ-world.z)>65*65)continue;seen.add(player.uid);
            OnlinePose pose=avatarPositions.get(player.uid);if(pose==null){pose=new OnlinePose();avatarPositions.put(player.uid,pose);}
            pose.receive(player.receivedAt,targetX,player.y,targetZ,player.yaw);float[] p=pose.at(System.nanoTime());
            float remaining=(targetX-p[0])*(targetX-p[0])+(targetZ-p[2])*(targetZ-p[2]);
            float x=p[0],feet=p[1]-1.6f,z=p[2],yaw=p[3],swing=remaining>.0004f?(float)Math.sin(System.nanoTime()*1e-9*7)*.09f:0;int shirt=27+OnlineWorld.colorIndex(player.uid);
            actorPart(x,feet,z,yaw,-.25f,.65f,-.15f,.5f,.55f,.3f,shirt);actorPart(x,feet,z,yaw,-.22f,1.2f,-.22f,.44f,.44f,.44f,25);
            actorPart(x,feet,z,yaw,-.24f,0,-.14f+swing,.19f,.65f,.28f,26);actorPart(x,feet,z,yaw,.05f,0,-.14f-swing,.19f,.65f,.28f,26);
            actorPart(x,feet,z,yaw,-.4f,.6f,-.13f-swing,.15f,.6f,.26f,-25);actorPart(x,feet,z,yaw,.25f,.6f,-.13f+swing,.15f,.6f,.26f,-25);
            float[] screen=playerScreen;if(GLU.gluProject(x,feet+1.85f,z,look,0,projection,0,viewport,0,screen,0)==1&&screen[2]>0&&screen[2]<1&&world.clearLine(world.x,world.y,world.z,x,feet+1.6f,z))labels.add(new PlayerLabel(player.name,screen[0],height-screen[1]));
        }
        avatarPositions.keySet().retainAll(seen);playerLabels=java.util.Collections.unmodifiableList(labels);
    }
    /** Visual-only mining debris; never persisted or synchronized over the network. */
    static final class MiningBurst {
        final float x,y,z;final int tile;final long started;
        MiningBurst(int x,int y,int z,int tile,long started){this.x=x+.5f;this.y=y+.5f;this.z=z+.5f;this.tile=tile;this.started=started;}
    }
    int particleCount;
    void particle(float x,float y,float z,float sx,float sy,float sz,int tile){if(particleCount++<150)cube(x,y,z,sx,sy,sz,tile);}
    final java.util.ArrayDeque<MiningBurst> miningBursts=new java.util.ArrayDeque<>();
    void miningDebris(int x,int y,int z,int block){
        if(block<=0||miningBursts.size()>=12)return;
        miningBursts.addLast(new MiningBurst(x,y,z,Materials.tile(block),System.nanoTime()));
    }
    void drawMiningDebris(){
        long now=System.nanoTime();
        while(!miningBursts.isEmpty()&&(now-miningBursts.peekFirst().started)*1e-9f>.7f)miningBursts.removeFirst();
        for(MiningBurst burst:miningBursts){
            float age=(now-burst.started)*1e-9f;
            if(age<0||age>.7f||Math.abs(burst.x-world.x)>16||Math.abs(burst.z-world.z)>16)continue;
            for(int i=0;i<8;i++){
                float a=i*2.3999632f+burst.x*1.37f+burst.z*.73f;
                float speed=.5f+(i%3)*.2f;
                float x=burst.x+(float)Math.cos(a)*speed*age;
                float z=burst.z+(float)Math.sin(a)*speed*age;
                float y=burst.y+.5f*age-2.8f*age*age+(i%4)*.08f;
                particle(x,y,z,.07f,.07f,.07f,burst.tile);
            }
        }
    }
    /** Local-only lava embers and smoke. No world mutation or Firebase writes. */
    void drawLavaAtmosphere(){
        int px=(int)Math.floor(world.x),pz=(int)Math.floor(world.z),emitted=0;
        float time=(System.nanoTime()*1e-9f)%10000f;
        for(int x=Math.max(1,px-9);x<Math.min(world.width-1,px+10)&&emitted<32;x++)
            for(int z=Math.max(1,pz-9);z<Math.min(world.depth-1,pz+10)&&emitted<32;z++){
                long hash=(world.seed^(x*73856093L)^(z*19349663L))*0x9E3779B97F4A7C15L;
                if((hash&3)!=0)continue;
                int y=-1;
                for(int yy=Math.min(World.H-2,(int)world.y+7);yy>=Math.max(1,(int)world.y-9);yy--)
                    if(world.get(x,yy,z)==15&&world.get(x,yy+1,z)==0){y=yy;break;}
                if(y<0)continue;
                float phase=(time*.6f+((hash>>>8)&255)/255f)%1f;
                float bx=x+.5f+(float)Math.sin(time*1.3f+x)*.2f;
                float bz=z+.5f+(float)Math.cos(time*1.1f+z)*.2f;
                // Rising ember and a larger, dimmer smoke puff above the lava.
                particle(bx,y+1.02f+phase*.75f,bz,.065f,.065f,.065f,24);
                if(phase>.22f){
                    float drift=(phase-.22f)*.27f;
                    particle(bx+drift,y+1.25f+phase*1.1f,bz-drift,.12f+phase*.09f,.10f+phase*.06f,.12f+phase*.09f,3);
                }
                emitted++;
            }
    }
    void drawMaterialEffects(){
        int sites=0;float t=(System.nanoTime()*1e-9f)%10000f;
        for(int y=Math.max(1,(int)world.y-9);y<Math.min(World.H,(int)world.y+7)&&sites<32;y++)for(int z=Math.max(0,(int)world.z-9);z<Math.min(world.depth,(int)world.z+10)&&sites<32;z++)for(int x=Math.max(0,(int)world.x-9);x<Math.min(world.width,(int)world.x+10)&&sites<32;x++){
            int b=world.get(x,y,z);boolean burning=Materials.burning(world,x,y,z);
            if(b!=Materials.CRYSTAL&&b!=Materials.EMBER&&!burning)continue;sites++;float phase=(t*.7f+x*.31f+z*.17f)%1f;
            if(burning){boolean wet=false;for(int[] d:MaterialReactions.NEIGHBORS)if(world.get(x+d[0],y+d[1],z+d[2])==10)wet=true;if(wet)continue;
                particle(x+.3f,y+1+phase*.65f,z+.35f,.16f,.30f*(1-phase)+.10f,.13f,24);
                particle(x+.6f,y+1.2f+phase*1.1f,z+.6f,.18f,.15f,.18f,3);
            }else particle(x+.15f+phase*.6f,y+1.05f+(float)Math.sin(t+x)*.13f,z+.5f,.045f,.045f,.045f,b==Materials.CRYSTAL?35:36);
        }
    }
    void drawMobs(){
        creatures.size=0;particleCount=0;
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
            particle(x+(float)Math.sin(i+phase*5)*.24f,y+phase*.7f,z+(float)Math.cos(i+phase*4)*.24f,.055f,.055f,.055f,24);
        }
        drawVegetation();
        drawLavaAtmosphere();
        drawMaterialEffects();
        drawMiningDebris();
        drawPlayers();
        if(creatures.size==0)return;
        if(creatures.size>creatureBuffer.capacity())creatureBuffer=ByteBuffer.allocateDirect(Math.max(creatures.size,creatureBuffer.capacity()*2)*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        creatureBuffer.clear();creatureBuffer.put(creatures.values,0,creatures.size);creatureBuffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,creatureBuffer);
        creatureBuffer.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,creatureBuffer);creatureBuffer.position(5);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,creatureBuffer);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,creatures.size/6);
    }
    /** Deterministic, local-only foliage: no block IDs, save changes or network traffic. */
    void drawVegetation(){
        int px=(int)Math.floor(world.x),pz=(int)Math.floor(world.z),drawn=0;
        for(int x=Math.max(1,px-12);x<Math.min(world.width-1,px+13);x++)
            for(int z=Math.max(1,pz-12);z<Math.min(world.depth-1,pz+13);z++){
                long hash=(world.seed^(x*73856093L)^(z*19349663L))*0x9E3779B97F4A7C15L;
                hash^=hash>>>32;
                if((hash&15)!=0||drawn>=64)continue;
                int top=-1;
                for(int y=World.H-2;y>=World.SEA;y--)if(world.get(x,y,z)==1&&world.get(x,y+1,z)==0){top=y;break;}
                if(top<0||Math.abs(top+1-world.y)>9)continue;
                float bx=x+.32f+((hash>>>8)&7)*.035f,bz=z+.32f+((hash>>>12)&7)*.035f;
                int kind=(int)((hash>>>20)&7);
                if(kind==0){
                    // A tiny mushroom, with a contrasting cap.
                    cube(bx,top+1,bz,.10f,.16f,.10f,9);
                    cube(bx-.06f,top+1.14f,bz-.06f,.22f,.09f,.22f,7);
                }else if(kind<=2){
                    // A flower on a thin stem with a bright bloom.
                    cube(bx+.04f,top+1,bz+.04f,.055f,.29f,.055f,6);
                    cube(bx-.025f,top+1.26f,bz-.025f,.18f,.13f,.18f,kind==1?11:12);
                }else{
                    // Two thin, offset blades of grass.
                    cube(bx,top+1,bz,.045f,.20f,.045f,6);
                    cube(bx+.10f,top+1,bz+.08f,.045f,.14f,.045f,6);
                }
                drawn++;
            }
    }
    final FloatBuffer outlineBuffer=ByteBuffer.allocateDirect(24*6*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    void outline(int[] hit){
        if(hit==null)return;
        outlineBuffer.clear();
        for(int[] edge:EDGES)for(int vertex:edge){
            outlineBuffer.put(hit[0]+((vertex&1)==0?-.004f:1.004f));
            outlineBuffer.put(hit[1]+((vertex&2)==0?-.004f:1.004f));
            outlineBuffer.put(hit[2]+((vertex&4)==0?-.004f:1.004f));
            outlineBuffer.put((11*32+16f)/2048);outlineBuffer.put(.5f);outlineBuffer.put(1f);
        }
        outlineBuffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,outlineBuffer);
        outlineBuffer.position(3);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,outlineBuffer);
        outlineBuffer.position(5);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,outlineBuffer);
        GLES20.glLineWidth(2);GLES20.glDrawArrays(GLES20.GL_LINES,0,24);
    }

}

