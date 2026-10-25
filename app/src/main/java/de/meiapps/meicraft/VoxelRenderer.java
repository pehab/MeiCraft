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
    int program,texture,pos,uv,light,mvp,camera,fog;
    final float[] projection=new float[16],look=new float[16],matrix=new float[16];
    final Mesh[] meshes=new Mesh[36];
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
    VoxelRenderer(GameView view){this.view=view;for(int i=0;i<36;i++)meshes[i]=new Mesh();}
    static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig config){
        String vs="uniform mat4 uM;uniform vec3 uCamera;attribute vec3 aPos;attribute vec2 aUv;attribute float aLight;varying vec2 vUv;varying float vLight;varying float vDistance;void main(){gl_Position=uM*vec4(aPos,1.0);vUv=aUv;vLight=aLight;vDistance=distance(aPos,uCamera);}";
        String fs="precision mediump float;uniform sampler2D uTex;uniform vec3 uFog;varying vec2 vUv;varying float vLight;varying float vDistance;void main(){vec3 c=texture2D(uTex,vUv).rgb*vLight;float f=smoothstep(27.0,55.0,vDistance);gl_FragColor=vec4(mix(c,uFog,f),1.0);}";
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs));GLES20.glLinkProgram(program);int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        pos=GLES20.glGetAttribLocation(program,"aPos");uv=GLES20.glGetAttribLocation(program,"aUv");light=GLES20.glGetAttribLocation(program,"aLight");mvp=GLES20.glGetUniformLocation(program,"uM");camera=GLES20.glGetUniformLocation(program,"uCamera");fog=GLES20.glGetUniformLocation(program,"uFog");
        int[] ids=new int[37];GLES20.glGenBuffers(36,ids,0);for(int i=0;i<36;i++){meshes[i].buffer=ids[i];meshes[i].dirty=true;}
        GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        try(java.io.InputStream stream=view.getContext().getAssets().open("blocks.png")){Bitmap bitmap=BitmapFactory.decodeStream(stream);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();}catch(Exception e){throw new IllegalStateException(e);}
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);GLES20.glCullFace(GLES20.GL_BACK);GLES20.glFrontFace(GLES20.GL_CCW);
        last=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,72,(float)w/h,.08f,70);}
    void dirty(int x,int z){for(int zz=Math.max(0,(z-1)/16);zz<=Math.min(5,(z+1)/16);zz++)for(int xx=Math.max(0,(x-1)/16);xx<=Math.min(5,(x+1)/16);xx++)meshes[zz*6+xx].dirty=true;}
    static final class Floats{float[] values=new float[8192];int size;void add(float a,float b,float c,float u,float v,float l){if(size+6>values.length)values=java.util.Arrays.copyOf(values,values.length*2);values[size++]=a;values[size++]=b;values[size++]=c;values[size++]=u;values[size++]=v;values[size++]=l;}}
    void mesh(int cx,int cz,Mesh mesh){Floats data=new Floats();
        for(int y=0;y<World.H;y++)for(int z=cz*16;z<cz*16+16;z++)for(int x=cx*16;x<cx*16+16;x++){
            int block=world.get(x,y,z);if(block==0)continue;
            for(int face=0;face<6;face++){
                int neighbor=world.get(x+N[face][0],y+N[face][1],z+N[face][2]);
                if(neighbor!=0 && !(neighbor==10&&block!=10))continue;
                if(y==0&&face==3)continue;
                int tile=block==13?16:block==14?17:block;if(block==1)tile=face==2?1:face==3?2:13;if(block==5&&face>=2&&face<=3)tile=14;
                float u0=(tile*32+.5f)/1024f,u1=(tile*32+31.5f)/1024f;
                float shade=block==11?1:SHADE[face];
                for(int corner:ORDER){float[] f=F[face];float u=(corner==0||corner==1)?u0:u1;float v=(corner==0||corner==3)?31.5f/32:.5f/32;data.add(x+f[corner*3],y+f[corner*3+1],z+f[corner*3+2],u,v,shade);}
            }
        }
        FloatBuffer buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(data.values,0,data.size).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,mesh.buffer);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,data.size*4,buffer,GLES20.GL_STATIC_DRAW);mesh.count=data.size/6;mesh.dirty=false;
    }
    @Override public void onDrawFrame(GL10 gl){
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last)*1e-9f);last=now;
        world=view.world;if(world==null)return;
        if(world!=view.renderedWorld){for(Mesh m:meshes)m.dirty=true;view.renderedWorld=world;}
        synchronized(world){
            if(!view.activity.paused)view.tick(dt);
            GLES20.glClearColor(.53f,.76f,.86f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float dx=-(float)Math.sin(world.yaw)*(float)Math.cos(world.pitch),dy=(float)Math.sin(world.pitch),dz=-(float)Math.cos(world.yaw)*(float)Math.cos(world.pitch);
            Matrix.setLookAtM(look,0,world.x,world.y,world.z,world.x+dx,world.y+dy,world.z+dz,0,1,0);Matrix.multiplyMM(matrix,0,projection,0,look,0);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvp,1,false,matrix,0);GLES20.glUniform3f(camera,world.x,world.y,world.z);GLES20.glUniform3f(fog,.53f,.76f,.86f);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTex"),0);
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(uv);GLES20.glEnableVertexAttribArray(light);
            for(int i=0;i<36;i++){Mesh m=meshes[i];if(m.dirty)mesh(i%6,i/6,m);float cx=i%6*16+8-world.x,cz=i/6*16+8-world.z;if(cx*cx+cz*cz>64*64)continue;
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,m.buffer);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,0);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,24,12);GLES20.glVertexAttribPointer(light,1,GLES20.GL_FLOAT,false,24,20);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,m.count);
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
            view.target=world.ray(6);outline(view.target);view.hud.postInvalidate();
        }
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
