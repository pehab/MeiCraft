package de.meiapps.meicraft;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import android.view.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Foreground-only Camera2 reader. Frames and decoding never leave the device. */
final class QrCamera implements AutoCloseable,TextureView.SurfaceTextureListener {
    interface Listener {void frame(String text);void problem(String message);}
    final Activity activity;final TextureView preview;final Listener listener;
    private final HandlerThread thread=new HandlerThread("meiCraft QR camera");
    private final Handler cameraHandler;
    private CameraDevice device;private CameraCaptureSession session;private ImageReader reader;private Surface surface;
    private int focusMode=CaptureRequest.CONTROL_AF_MODE_OFF;
    private volatile boolean closed;private boolean opening;private long lastFrame;
    volatile int imageCount;
    QrCamera(Activity activity,TextureView preview,Listener listener){
        this.activity=activity;this.preview=preview;this.listener=listener;thread.start();cameraHandler=new Handler(thread.getLooper());
        preview.setSurfaceTextureListener(this);if(preview.isAvailable())start();
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){start();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){close();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
    private void error(String message){if(!closed)activity.runOnUiThread(()->{if(!closed)listener.problem(message);});}
    private void start(){
        if(closed||opening||!preview.isAvailable())return;
        if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){error("Kamerazugriff fehlt. Bitte Kamera erlauben.");return;}
        opening=true;
        try{
            CameraManager manager=(CameraManager)activity.getSystemService(Activity.CAMERA_SERVICE);
            String selected=null;CameraCharacteristics characteristics=null;
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics candidate=manager.getCameraCharacteristics(id);
                if(selected==null){selected=id;characteristics=candidate;}
                Integer lens=candidate.get(CameraCharacteristics.LENS_FACING);
                if(lens!=null&&lens==CameraCharacteristics.LENS_FACING_BACK){selected=id;characteristics=candidate;break;}
            }
            if(selected==null||characteristics==null){error("Dieses Gerät hat keine nutzbare Kamera.");return;}
            int[] focusModes=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if(focusModes!=null)for(int mode:focusModes)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)focusMode=mode;
            StreamConfigurationMap config=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=config==null?null:config.getOutputSizes(ImageFormat.YUV_420_888);
            if(sizes==null||sizes.length==0){error("Die Kamera unterstützt keinen QR-Empfang.");return;}
            Size[] textureSizes=config.getOutputSizes(SurfaceTexture.class);
            Size size=null;for(Size s:sizes){if(textureSizes!=null&&Arrays.asList(textureSizes).contains(s)&&s.getWidth()<=1280&&s.getHeight()<=1280&&(size==null||(long)s.getWidth()*s.getHeight()>(long)size.getWidth()*size.getHeight()))size=s;}
            if(size==null){error("Keine passende Kameraauflösung gefunden.");return;}
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(this::image,cameraHandler);
            SurfaceTexture texture=preview.getSurfaceTexture();if(texture==null)return;
            texture.setDefaultBufferSize(size.getWidth(),size.getHeight());surface=new Surface(texture);
            configurePreview(characteristics,size);
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice camera){if(closed){camera.close();return;}device=camera;configure();}
                @Override public void onDisconnected(CameraDevice camera){camera.close();error("Kamera getrennt. Bitte Empfang erneut öffnen.");}
                @Override public void onError(CameraDevice camera,int code){camera.close();error("Kamera nicht verfügbar. Bitte andere Kamera-Apps schließen und erneut versuchen.");}
            },new Handler(activity.getMainLooper()));
        }catch(CameraAccessException|RuntimeException e){error("Kamera konnte nicht geöffnet werden. Bitte Empfang erneut öffnen.");}
    }
    @SuppressWarnings("deprecation") // Display API fallback for Android 8/9.
    private void configurePreview(CameraCharacteristics characteristics,Size size){
        Integer sensor=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int display=activity.getWindowManager().getDefaultDisplay().getRotation()*90;
        int rotation=((sensor==null?90:sensor)-display+360)%360;
        float vw=preview.getWidth(),vh=preview.getHeight();if(vw<=0||vh<=0)return;
        Matrix matrix=new Matrix();float bufferW=rotation%180==0?size.getWidth():size.getHeight(),bufferH=rotation%180==0?size.getHeight():size.getWidth();
        float scale=Math.max(vw/bufferW,vh/bufferH);
        matrix.setScale(bufferW*scale/vw,bufferH*scale/vh,vw/2,vh/2);matrix.postRotate(rotation,vw/2,vh/2);preview.setTransform(matrix);
    }
    @SuppressWarnings("deprecation") // API 26/27 use the Camera2 session overload predating SessionConfiguration.
    private void configure(){
        if(closed||device==null||reader==null||surface==null)return;
        try{
            List<Surface> surfaces=Arrays.asList(surface,reader.getSurface());
            CameraCaptureSession.StateCallback callback=new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession configured){
                    if(closed){configured.close();return;}session=configured;
                    try{CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(surface);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,focusMode);
                        session.setRepeatingRequest(request.build(),null,cameraHandler);
                    }catch(CameraAccessException|RuntimeException e){error("Kameravorschau konnte nicht gestartet werden.");}
                }
                @Override public void onConfigureFailed(CameraCaptureSession failed){error("Kameravorschau nicht verfügbar.");}
            };
            if(Build.VERSION.SDK_INT>=28){
                List<OutputConfiguration> outputs=new ArrayList<>();for(Surface s:surfaces)outputs.add(new OutputConfiguration(s));
                device.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,command->cameraHandler.post(command),callback));
            }else device.createCaptureSession(surfaces,callback,cameraHandler);
        }catch(CameraAccessException|RuntimeException e){error("Kamera konnte nicht gestartet werden.");}
    }
    private void image(ImageReader source){
        try(Image image=source.acquireLatestImage()){
            if(image==null||closed)return;imageCount++;long now=SystemClock.elapsedRealtime();if(now-lastFrame<160)return;lastFrame=now;
            Image.Plane plane=image.getPlanes()[0];ByteBuffer buffer=plane.getBuffer();int width=image.getWidth(),height=image.getHeight();
            byte[] luma=new byte[width*height];int row=plane.getRowStride(),pixel=plane.getPixelStride(),base=buffer.position();
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)luma[y*width+x]=buffer.get(base+y*row+x*pixel);
            String text=QrCodec.decode(luma,width,height);if(text!=null&&!closed)listener.frame(text);
        }catch(IllegalStateException|IndexOutOfBoundsException ignored){/* Camera closing or a dropped frame. */}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        // Serialize resource release after any current image callback.
        cameraHandler.post(()->{if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}if(reader!=null){reader.close();reader=null;}if(surface!=null){surface.release();surface=null;}thread.quitSafely();});
    }
    boolean closed(){return closed;}
}
